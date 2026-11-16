package com.zirakusa.stone.nativeapp.data.library

import android.content.Context
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Lyrics embedded in the file: ID3 USLT (MP3, the frame Stone 1.3.2 used), the FLAC LYRICS
 * comment and the M4A `©lyr` tag, which is also what Stone writes when lyrics are saved.
 */
class EmbeddedLyricsReader(private val context: Context) {
    fun read(uri: Uri): String = runCatching {
        val start = MediaFileIo.readHead(context, uri, 12)
        when {
            start.size >= 3 && String(start, 0, 3, Charsets.ISO_8859_1) == "ID3" ->
                id3(uri).ifBlank { flac(uri) }
            start.size >= 4 && String(start, 0, 4, Charsets.ISO_8859_1) == "fLaC" -> flac(uri)
            // Audio-only MP4 (M4A/M4B) by its brand: videos are not opened here.
            start.size >= 12 && String(start, 4, 4, Charsets.ISO_8859_1) == "ftyp" &&
                String(start, 8, 3, Charsets.ISO_8859_1) in setOf("M4A", "M4B") ->
                Mp4MetadataWriter.readTags(context, uri.toString())["©lyr"].orEmpty()
            else -> ""
        }
    }.getOrDefault("")

    private fun flac(uri: Uri): String = FlacTag.readLyrics(MediaFileIo.readHead(context, uri, 4 * 1024 * 1024))

    private fun id3(uri: Uri): String = runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val header = ByteArray(10)
            if (input.read(header) != 10 || String(header, 0, 3) != "ID3") return@use ""
            val version = header[3].toInt()
            var remaining = syncSafe(header, 6).coerceAtMost(16 * 1024 * 1024)
            val tag = ByteArrayOutputStream(remaining)
            val buffer = ByteArray(8192)
            while (remaining > 0) {
                val count = input.read(buffer, 0, minOf(buffer.size, remaining))
                if (count <= 0) break
                tag.write(buffer, 0, count)
                remaining -= count
            }
            parseFrames(tag.toByteArray(), version)
        }.orEmpty()
    }.getOrDefault("")

    private fun parseFrames(tag: ByteArray, version: Int): String {
        var offset = 0
        while (offset + 10 <= tag.size) {
            val id = String(tag, offset, 4, Charsets.ISO_8859_1)
            if (id.all { it == '\u0000' }) break
            val size = if (version >= 4) syncSafe(tag, offset + 4) else
                ByteBuffer.wrap(tag, offset + 4, 4).order(ByteOrder.BIG_ENDIAN).int
            if (size <= 0 || offset + 10 + size > tag.size) break
            if (id == "USLT") return decode(tag.copyOfRange(offset + 10, offset + 10 + size))
            offset += 10 + size
        }
        return ""
    }

    private fun decode(frame: ByteArray): String {
        if (frame.size < 5) return ""
        val encoding = frame[0].toInt() and 0xff
        val charset = when (encoding) {
            1 -> Charsets.UTF_16
            2 -> Charsets.UTF_16BE
            3 -> Charsets.UTF_8
            else -> Charsets.ISO_8859_1
        }
        val separatorSize = if (encoding == 1 || encoding == 2) 2 else 1
        var start = 4 // encoding + ISO-639 language
        while (start + separatorSize <= frame.size) {
            val separator = (0 until separatorSize).all { frame[start + it].toInt() == 0 }
            start += separatorSize
            if (separator) break
        }
        return String(frame, start.coerceAtMost(frame.size),
            frame.size - start.coerceAtMost(frame.size), charset).replace("\u0000", "").trim()
    }

    private fun syncSafe(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0x7f) shl 21) or
            ((bytes[offset + 1].toInt() and 0x7f) shl 14) or
            ((bytes[offset + 2].toInt() and 0x7f) shl 7) or
            (bytes[offset + 3].toInt() and 0x7f)
}
