package com.zirakusa.stone

import android.content.ContentValues
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
import com.facebook.react.bridge.LifecycleEventListener
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import java.io.File
import java.nio.ByteBuffer

class StoneRingtoneModule(private val context: ReactApplicationContext) : ReactContextBaseJavaModule(context), LifecycleEventListener {
  private data class Pending(val uri: String, val title: String, val startMs: Int, val promise: Promise)
  private var pending: Pending? = null
  init { context.addLifecycleEventListener(this) }
  override fun getName() = "StoneRingtone"

  @ReactMethod
  fun setRingtone(uri: String, title: String, startMs: Int, promise: Promise) {
    if (!Settings.System.canWrite(context)) {
      pending = Pending(uri, title, startMs, promise)
      context.startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
      return
    }
    applyRingtone(uri, title, startMs, promise)
  }

  override fun onHostResume() {
    val request = pending ?: return
    pending = null
    if (!Settings.System.canWrite(context)) {
      request.promise.reject("WRITE_SETTINGS_DENIED", "Debes permitir que Stone modifique los ajustes del sistema.")
      return
    }
    applyRingtone(request.uri, request.title, request.startMs, request.promise)
  }
  override fun onHostPause() = Unit
  override fun onHostDestroy() = Unit

  private fun applyRingtone(source: String, title: String, startMs: Int, promise: Promise) {
    try {
      val resolver = context.contentResolver
      val safeTitle = title.replace(Regex("[^A-Za-z0-9 _-]"), "").take(60).ifBlank { "Stone ringtone" }
      val clip = runCatching { createClip(source, startMs) }.getOrNull()
      val values = ContentValues().apply {
        put(MediaStore.Audio.Media.DISPLAY_NAME, "$safeTitle-${startMs / 1000}s.${if (clip != null) "m4a" else "mp3"}")
        put(MediaStore.Audio.Media.TITLE, safeTitle)
        put(MediaStore.Audio.Media.MIME_TYPE, if (clip != null) "audio/mp4" else "audio/mpeg")
        put(MediaStore.Audio.Media.IS_RINGTONE, true)
        put(MediaStore.Audio.Media.IS_MUSIC, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_RINGTONES + "/Stone")
      }
      val target = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values) ?: error("Android no pudo crear el tono")
      try {
        val input = clip?.inputStream() ?: openInput(source)
        input.use { resolver.openOutputStream(target, "w")!!.use { output -> it.copyTo(output) } }
        RingtoneManager.setActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE, target)
        promise.resolve(true)
      } catch (error: Exception) {
        resolver.delete(target, null, null)
        throw error
      } finally {
        clip?.delete()
      }
    } catch (error: Exception) {
      promise.reject("RINGTONE_FAILED", "No se pudo establecer el tono de llamada.", error)
    }
  }

  private fun openInput(value: String) = when (Uri.parse(value).scheme) {
    "file" -> java.io.FileInputStream(Uri.parse(value).path!!)
    else -> context.contentResolver.openInputStream(Uri.parse(value)) ?: error("No se pudo abrir la canción")
  }

  private fun createClip(value: String, startMs: Int): File {
    val extractor = MediaExtractor()
    val uri = Uri.parse(value)
    val descriptor = if (uri.scheme == "file") null else context.contentResolver.openAssetFileDescriptor(uri, "r")
    try {
      if (descriptor != null) extractor.setDataSource(descriptor.fileDescriptor, descriptor.startOffset, descriptor.length)
      else extractor.setDataSource(uri.path ?: error("Ruta inválida"))
      val sourceTrack = (0 until extractor.trackCount).firstOrNull {
        extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
      } ?: error("El archivo no contiene audio")
      extractor.selectTrack(sourceTrack)
      val output = File.createTempFile("stone-ringtone-", ".m4a", context.cacheDir)
      val muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
      try {
        val targetTrack = muxer.addTrack(extractor.getTrackFormat(sourceTrack))
        muxer.start()
        val startUs = startMs.coerceAtLeast(0) * 1000L
        val endUs = startUs + 30_000_000L
        extractor.seekTo(startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
        val buffer = ByteBuffer.allocate(1024 * 1024)
        val info = MediaCodec.BufferInfo()
        while (true) {
          val timeUs = extractor.sampleTime
          if (timeUs < 0 || timeUs > endUs) break
          val size = extractor.readSampleData(buffer, 0)
          if (size < 0) break
          info.set(0, size, (timeUs - startUs).coerceAtLeast(0), extractor.sampleFlags)
          muxer.writeSampleData(targetTrack, buffer, info)
          extractor.advance()
        }
        muxer.stop()
      } finally {
        muxer.release()
      }
      return output
    } finally {
      extractor.release()
      descriptor?.close()
    }
  }
}
