package com.zirakusa.stone.nativeapp.data.library

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import androidx.core.net.toUri
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.util.Locale

/**
 * Streams an MP4/M4V edit without loading the media into memory. Only `moov` is read and
 * rebuilt by [Mp4Atoms]; media bytes are copied with the kernel's channel transfer.
 *
 * - `moov` at the end of the file (camera videos): rewritten in place and truncated.
 * - `moov` before `mdat` (fast-start downloads): the file is rebuilt in a temporary copy
 *   with shifted chunk offsets, then replaced. Works for plain files and SAF documents.
 */
object Mp4MetadataWriter {
    private const val MAX_MOOV = 64L * 1024L * 1024L
    private val extensions = setOf("mp4", "m4v")

    private class Box(val type: String, val offset: Long, val size: Long)

    fun canWrite(context: Context, value: String): Boolean {
        val extension = Uri.decode(value).substringBefore('?').substringAfterLast('.', "").lowercase(Locale.ROOT)
        if (extension !in extensions) return false
        val uri = value.toUri()
        return when (uri.scheme) {
            "file" -> File(uri.path ?: return false).let { it.isFile && it.canRead() && it.canWrite() }
            "content" -> runCatching {
                context.contentResolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_FLAGS),
                    null, null, null)?.use { cursor ->
                    cursor.moveToFirst() &&
                        cursor.getInt(0) and DocumentsContract.Document.FLAG_SUPPORTS_WRITE != 0
                } == true
            }.getOrDefault(false)
            else -> false
        }
    }

    /** Reads iTunes text tags (`©nam`, `©ART`, `desc`…) or an empty map when unavailable. */
    fun readTags(context: Context, value: String): Map<String, String> = runCatching {
        open(context, value.toUri(), "r").use { input ->
            val channel = input.channel
            val moov = boxes(channel).firstOrNull { it.type == "moov" } ?: return emptyMap()
            Mp4Atoms.readTags(read(channel, moov))
        }
    }.getOrDefault(emptyMap())

    fun write(context: Context, value: String, draft: VideoMetadataDraft, cover: ByteArray?, removeCover: Boolean) {
        val uri = value.toUri()
        val (layout, moovBytes) = open(context, uri, "r").use { input ->
            val boxes = boxes(input.channel)
            val moov = boxes.firstOrNull { it.type == "moov" } ?: error("El archivo no contiene un átomo moov válido")
            boxes to read(input.channel, moov)
        }
        val moov = layout.first { it.type == "moov" }
        val mediaAfter = layout.any { it.offset > moov.offset && it.type == "mdat" }
        if (!mediaAfter && layout.last() === moov) {
            val rebuilt = Mp4Atoms.rebuildMoov(moovBytes, draft, cover, removeCover)
            openWritable(context, uri).use { output ->
                val buffer = ByteBuffer.wrap(rebuilt)
                while (buffer.hasRemaining()) output.write(buffer, moov.offset + buffer.position())
                output.truncate(moov.offset + rebuilt.size)
                output.force(true)
            }
            return
        }
        // The new moov size decides how far later media moves; offsets only move when
        // media follows moov. Rebuilding twice keeps the size calculation exact.
        val probe = Mp4Atoms.rebuildMoov(moovBytes, draft, cover, removeCover)
        val shift = if (mediaAfter) probe.size - moov.size else 0L
        val rebuilt = if (shift == 0L) probe else Mp4Atoms.rebuildMoov(moovBytes, draft, cover, removeCover, shift)
        check(rebuilt.size == probe.size) { "No se pudo recalcular el MP4" }
        replaceWithRebuilt(context, uri, layout, moov, rebuilt)
    }

    private fun replaceWithRebuilt(context: Context, uri: Uri, layout: List<Box>, moov: Box, rebuilt: ByteArray) {
        val local = uri.scheme == "file"
        val directory = if (local) File(uri.path!!).parentFile!! else context.cacheDir
        val temporary = File.createTempFile(".stone-metadata-", ".mp4", directory)
        try {
            var expected = 0L
            open(context, uri, "r").use { input ->
                FileOutputStream(temporary).use { output ->
                    layout.forEach { box ->
                        if (box === moov) output.write(rebuilt)
                        else copy(input.channel, box.offset, box.size, output.channel)
                        expected += if (box === moov) rebuilt.size.toLong() else box.size
                    }
                    output.fd.sync()
                }
            }
            check(temporary.length() == expected) { "La copia temporal del MP4 está incompleta" }
            if (local) {
                check(temporary.renameTo(File(uri.path!!))) { "No se pudo reemplazar el archivo" }
            } else {
                openWritable(context, uri).use { output ->
                    output.position(0)
                    FileInputStream(temporary).use { input -> copy(input.channel, 0, expected, output) }
                    output.truncate(expected)
                    output.force(true)
                }
            }
        } finally {
            temporary.delete()
        }
    }

    private fun boxes(channel: FileChannel): List<Box> {
        val result = mutableListOf<Box>()
        val size = channel.size()
        val header = ByteBuffer.allocate(16).order(ByteOrder.BIG_ENDIAN)
        var offset = 0L
        while (offset + 8 <= size) {
            header.clear().limit(16)
            channel.read(header, offset)
            val declared = header.getInt(0).toLong() and 0xFFFFFFFFL
            val type = String(header.array(), 4, 4, Charsets.ISO_8859_1)
            val length = when (declared) {
                0L -> size - offset
                1L -> header.getLong(8)
                else -> declared
            }
            require(length >= 8 && offset + length <= size) { "Átomo MP4 fuera de límites" }
            result += Box(type, offset, length)
            offset += length
        }
        require(result.isNotEmpty()) { "Estructura MP4 incompleta" }
        return result
    }

    private fun read(channel: FileChannel, box: Box): ByteArray {
        require(box.size <= MAX_MOOV) { "El índice del MP4 es demasiado grande" }
        val buffer = ByteBuffer.allocate(box.size.toInt())
        while (buffer.hasRemaining()) {
            if (channel.read(buffer, box.offset + buffer.position()) < 0) error("Lectura MP4 incompleta")
        }
        return buffer.array()
    }

    private fun copy(source: FileChannel, offset: Long, length: Long, target: FileChannel) {
        var done = 0L
        while (done < length) {
            val moved = source.transferTo(offset + done, length - done, target)
            check(moved > 0) { "Copia MP4 interrumpida" }
            done += moved
        }
    }

    private fun open(context: Context, uri: Uri, mode: String): FileInputStream =
        if (uri.scheme == "file") FileInputStream(File(uri.path!!))
        else ParcelFileDescriptor.AutoCloseInputStream(context.contentResolver.openFileDescriptor(uri, mode)
            ?: error("No se pudo abrir el vídeo"))

    /** Positional writer; never opened in append mode so `moov` can be replaced in place. */
    private fun openWritable(context: Context, uri: Uri): FileChannel =
        if (uri.scheme == "file") RandomAccessFile(File(uri.path!!), "rw").channel
        else ParcelFileDescriptor.AutoCloseOutputStream(context.contentResolver.openFileDescriptor(uri, "rw")
            ?: error("No se pudo abrir el vídeo para escribir")).channel
}
