package com.zirakusa.stone.nativeapp.data.library

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import java.nio.ByteBuffer

object SongActions {
    fun share(context: Context, uri: String, title: String) {
        val source = Uri.parse(uri)
        val shareUri = if (source.scheme == "content") source else {
            val file = source.path?.let(::File) ?: error("La canción no tiene una ruta válida")
            require(file.exists()) { "La canción ya no está disponible." }
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = context.contentResolver.getType(shareUri) ?: audioMime(uri)
            putExtra(Intent.EXTRA_STREAM, shareUri)
            clipData = ClipData.newUri(context.contentResolver, title, shareUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Compartir canción"))
    }

    fun requestWriteSettings(context: Context) {
        context.startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,
            Uri.parse("package:${context.packageName}")))
    }

    fun canSetRingtone(context: Context) = Settings.System.canWrite(context)

    fun setRingtone(context: Context, source: String, title: String, startMs: Int) {
        check(canSetRingtone(context)) { "Debes permitir que Stone modifique los ajustes del sistema." }
        val resolver = context.contentResolver
        val safeTitle = title.replace(Regex("[^A-Za-z0-9 _-]"), "").take(60).ifBlank { "Stone ringtone" }
        val clip = runCatching { createClip(context, source, startMs) }.getOrNull()
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, "$safeTitle-${startMs / 1000}s.${if (clip != null) "m4a" else "mp3"}")
            put(MediaStore.Audio.Media.TITLE, safeTitle)
            put(MediaStore.Audio.Media.MIME_TYPE, if (clip != null) "audio/mp4" else "audio/mpeg")
            put(MediaStore.Audio.Media.IS_RINGTONE, true); put(MediaStore.Audio.Media.IS_MUSIC, false)
            if (Build.VERSION.SDK_INT >= 29) put(MediaStore.Audio.Media.RELATIVE_PATH,
                Environment.DIRECTORY_RINGTONES + "/Stone")
        }
        val target = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("Android no pudo crear el tono")
        try {
            (clip?.inputStream() ?: resolver.openInputStream(Uri.parse(source)) ?: error("No se pudo abrir la canción"))
                .use { input -> resolver.openOutputStream(target, "w")!!.use(input::copyTo) }
            RingtoneManager.setActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE, target)
        } catch (error: Exception) { resolver.delete(target, null, null); throw error }
        finally { clip?.delete() }
    }

    private fun createClip(context: Context, value: String, startMs: Int): File {
        val extractor = MediaExtractor()
        val descriptor = context.contentResolver.openAssetFileDescriptor(Uri.parse(value), "r")
            ?: error("No se pudo abrir la canción")
        try {
            extractor.setDataSource(descriptor.fileDescriptor, descriptor.startOffset, descriptor.length)
            val sourceTrack = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: error("El archivo no contiene audio")
            extractor.selectTrack(sourceTrack)
            val output = File.createTempFile("stone-ringtone-", ".m4a", context.cacheDir)
            val muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            try {
                val targetTrack = muxer.addTrack(extractor.getTrackFormat(sourceTrack)); muxer.start()
                val startUs = startMs.coerceAtLeast(0) * 1000L; val endUs = startUs + 30_000_000L
                extractor.seekTo(startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                val buffer = ByteBuffer.allocate(1024 * 1024); val info = MediaCodec.BufferInfo()
                while (true) {
                    val timeUs = extractor.sampleTime
                    if (timeUs < 0 || timeUs > endUs) break
                    val size = extractor.readSampleData(buffer, 0); if (size < 0) break
                    val flags = if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0)
                        MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                    info.set(0, size, (timeUs - startUs).coerceAtLeast(0), flags)
                    muxer.writeSampleData(targetTrack, buffer, info); extractor.advance()
                }
                muxer.stop()
            } finally { muxer.release() }
            return output
        } finally { extractor.release(); descriptor.close() }
    }

    private fun audioMime(value: String) = when (value.substringAfterLast('.', "").lowercase()) {
        "m4a", "mp4" -> "audio/mp4"; "wav" -> "audio/wav"; "flac" -> "audio/flac"
        "ogg", "opus" -> "audio/ogg"; else -> "audio/mpeg"
    }
}
