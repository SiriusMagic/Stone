package com.zirakusa.stone.nativeapp.data.library

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class AudioTags(val title: String = "", val artist: String = "", val album: String = "",
    val genre: String = "", val year: String = "", val trackNumber: String = "")

/** Preserves unknown ID3 frames and rewrites only Stone's editable MP3 text frames. */
class AudioTagEditor(private val context: Context) {
    fun read(value: String): AudioTags {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, Uri.parse(value))
            fun text(key: Int) = retriever.extractMetadata(key).orEmpty()
            AudioTags(text(MediaMetadataRetriever.METADATA_KEY_TITLE),
                text(MediaMetadataRetriever.METADATA_KEY_ARTIST), text(MediaMetadataRetriever.METADATA_KEY_ALBUM),
                text(MediaMetadataRetriever.METADATA_KEY_GENRE), text(MediaMetadataRetriever.METADATA_KEY_YEAR),
                text(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER))
        } finally { retriever.release() }
    }

    fun write(value: String, tags: AudioTags) {
        require(value.substringBefore('?').substringAfterLast('.', "").equals("mp3", true)) {
            "La edición de etiquetas está disponible para archivos MP3."
        }
        val uri = Uri.parse(value)
        val source = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("No se pudo abrir el archivo")
        val updated = rewrite(source, mapOf("TIT2" to tags.title, "TPE1" to tags.artist,
            "TALB" to tags.album, "TCON" to tags.genre, "TYER" to tags.year, "TRCK" to tags.trackNumber))
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(updated) }
            ?: error("El proveedor no permite modificar este archivo")
    }

    /** Writes a new MP3 copy with an ID3 USLT frame; the selected source is never changed. */
    fun writeLyricsCopy(sourceValue: String, target: Uri, lyrics: String) {
        require(sourceValue.substringBefore('?').substringAfterLast('.', "").equals("mp3", true)) {
            "La incrustación de letras está disponible para copias MP3."
        }
        val source = context.contentResolver.openInputStream(Uri.parse(sourceValue))?.use { it.readBytes() }
            ?: error("No se pudo abrir el archivo")
        val updated = rewriteLyrics(source, lyrics)
        context.contentResolver.openOutputStream(target, "wt")?.use { it.write(updated) }
            ?: error("No se pudo crear la copia")
    }

    private fun rewrite(source: ByteArray, changes: Map<String, String>): ByteArray {
        val hasTag = source.size >= 10 && String(source, 0, 3, Charsets.ISO_8859_1) == "ID3"
        val version = if (hasTag && source[3].toInt() in 3..4) source[3].toInt() else 3
        val tagEnd = if (hasTag) (10 + syncSafe(source, 6)).coerceAtMost(source.size) else 0
        val existing = if (hasTag) source.copyOfRange(10, tagEnd) else ByteArray(0)
        val body = ByteArrayOutputStream().apply {
            write(preserve(existing, version))
            changes.forEach { (frame, text) -> writeTextFrame(this, version, frame, text) }
        }.toByteArray()
        require(body.size <= 0x0fffffff)
        return ByteArrayOutputStream(10 + body.size + source.size - tagEnd).apply {
            write("ID3".toByteArray(Charsets.ISO_8859_1)); write(byteArrayOf(version.toByte(), 0, 0))
            write(syncSafeBytes(body.size)); write(body); write(source, tagEnd, source.size - tagEnd)
        }.toByteArray()
    }

    private fun rewriteLyrics(source: ByteArray, lyrics: String): ByteArray {
        val hasTag = source.size >= 10 && String(source, 0, 3, Charsets.ISO_8859_1) == "ID3"
        val version = if (hasTag && source[3].toInt() in 3..4) source[3].toInt() else 3
        val tagEnd = if (hasTag) (10 + syncSafe(source, 6)).coerceAtMost(source.size) else 0
        val existing = if (hasTag) source.copyOfRange(10, tagEnd) else ByteArray(0)
        val body = ByteArrayOutputStream().apply {
            write(preserve(existing, version))
            writeUslt(this, version, lyrics)
        }.toByteArray()
        require(body.size <= 0x0fffffff)
        return ByteArrayOutputStream(10 + body.size + source.size - tagEnd).apply {
            write("ID3".toByteArray(Charsets.ISO_8859_1)); write(byteArrayOf(version.toByte(), 0, 0))
            write(syncSafeBytes(body.size)); write(body); write(source, tagEnd, source.size - tagEnd)
        }.toByteArray()
    }

    private fun preserve(body: ByteArray, version: Int): ByteArray {
        val replaced = setOf("TIT2", "TPE1", "TALB", "TCON", "TYER", "TDRC", "TRCK", "USLT")
        val kept = ByteArrayOutputStream(); var offset = 0
        while (offset + 10 <= body.size) {
            val id = String(body, offset, 4, Charsets.ISO_8859_1)
            if (id.all { it == '\u0000' }) break
            val size = if (version == 4) syncSafe(body, offset + 4) else
                ByteBuffer.wrap(body, offset + 4, 4).order(ByteOrder.BIG_ENDIAN).int
            val end = offset + 10 + size
            if (size < 0 || end > body.size) break
            if (id !in replaced) kept.write(body, offset, end - offset)
            offset = end
        }
        return kept.toByteArray()
    }

    private fun writeTextFrame(out: ByteArrayOutputStream, version: Int, id: String, value: String) {
        if (value.isBlank()) return
        val text = if (version == 4) value.toByteArray(Charsets.UTF_8) else value.toByteArray(Charsets.UTF_16)
        val payload = byteArrayOf(if (version == 4) 3 else 1) + text
        out.write(id.toByteArray(Charsets.ISO_8859_1))
        out.write(if (version == 4) syncSafeBytes(payload.size) else
            ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(payload.size).array())
        out.write(byteArrayOf(0, 0)); out.write(payload)
    }

    private fun writeUslt(out: ByteArrayOutputStream, version: Int, value: String) {
        if (value.isBlank()) return
        val payload = byteArrayOf(3) + "eng".toByteArray(Charsets.ISO_8859_1) + byteArrayOf(0) +
            value.toByteArray(Charsets.UTF_8)
        out.write("USLT".toByteArray(Charsets.ISO_8859_1))
        out.write(if (version == 4) syncSafeBytes(payload.size) else
            ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(payload.size).array())
        out.write(byteArrayOf(0, 0)); out.write(payload)
    }

    private fun syncSafe(bytes: ByteArray, offset: Int) = ((bytes[offset].toInt() and 0x7f) shl 21) or
        ((bytes[offset + 1].toInt() and 0x7f) shl 14) or ((bytes[offset + 2].toInt() and 0x7f) shl 7) or
        (bytes[offset + 3].toInt() and 0x7f)
    private fun syncSafeBytes(value: Int) = byteArrayOf(((value shr 21) and 0x7f).toByte(),
        ((value shr 14) and 0x7f).toByte(), ((value shr 7) and 0x7f).toByte(), (value and 0x7f).toByte())
}
