package com.zirakusa.stone.nativeapp.data.library

import android.content.Context
import androidx.core.net.toUri
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Locale

/**
 * Minimal, defensive MP4 atom editor. It rewrites only the metadata atom tree and copies
 * the media bytes unchanged; it never decodes or recompresses audio/video. It deliberately
 * limits itself to local files small enough to process without a second large native buffer.
 */
internal object Mp4MetadataWriter {
    private const val MAX_BYTES = 64L * 1024L * 1024L
    private val textTypes = listOf("©nam", "©cmt", "©ART", "©day", "©gen")

    fun canWrite(context: Context, value: String): Boolean {
        val uri = value.toUri()
        if (uri.scheme != "file") return false
        val file = File(uri.path ?: return false)
        val extension = value.substringBefore('?').substringAfterLast('.', "").lowercase(Locale.ROOT)
        return extension in setOf("mp4", "m4v") && file.isFile && file.canRead() && file.canWrite() &&
            file.length() in 1..MAX_BYTES
    }

    fun write(context: Context, value: String, draft: VideoMetadataDraft,
        cover: ByteArray?, removeCover: Boolean) {
        val file = File(value.toUri().path ?: error("Ruta MP4 no válida"))
        require(file.isFile && file.length() <= MAX_BYTES) { "El MP4 es demasiado grande para editarlo de forma segura" }
        val source = FileInputStream(file).use { input ->
            val bytes = input.readBytes()
            require(bytes.size.toLong() == file.length()) { "El archivo cambió durante la lectura" }
            bytes
        }
        val rewritten = rewrite(source, draft, cover, removeCover)
        val temporary = File(file.parentFile, ".${file.name}.stone-metadata.tmp")
        try {
            FileOutputStream(temporary).use { output ->
                output.write(rewritten)
                output.fd.sync()
            }
            try {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING)
            } catch (_: Exception) {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            temporary.delete()
        }
    }

    private data class Atom(val type: String, val bytes: ByteArray, val headerSize: Int) {
        val payload: ByteArray get() = bytes.copyOfRange(headerSize, bytes.size)
    }

    private fun rewrite(source: ByteArray, draft: VideoMetadataDraft, cover: ByteArray?, removeCover: Boolean): ByteArray {
        val atoms = parse(source, 0, source.size)
        val moovIndex = atoms.indexOfFirst { it.type == "moov" }
        require(moovIndex >= 0) { "El archivo no contiene un átomo moov válido" }
        val updatedMoov = updateMoov(atoms[moovIndex], draft, cover, removeCover)
        return ByteArrayOutputStreamCompat(source.size + 4096).apply {
            atoms.forEachIndexed { index, atom -> write(if (index == moovIndex) updatedMoov else atom.bytes) }
        }.toByteArray()
    }

    private fun updateMoov(moov: Atom, draft: VideoMetadataDraft, cover: ByteArray?, removeCover: Boolean): ByteArray {
        val children = parse(moov.payload, 0, moov.payload.size)
        val udtaIndex = children.indexOfFirst { it.type == "udta" }
        val updatedUdta = if (udtaIndex >= 0) updateUdta(children[udtaIndex], draft, cover, removeCover)
        else atom("udta", metaAtom(ilstAtom(emptyList(), draft, cover, removeCover)).bytes).bytes
        val output = ByteArrayOutputStreamCompat(moov.bytes.size + 4096)
        children.forEachIndexed { index, child -> output.write(if (index == udtaIndex) updatedUdta else child.bytes) }
        if (udtaIndex < 0) output.write(updatedUdta)
        return atom("moov", output.toByteArray()).bytes
    }

    private fun updateUdta(udta: Atom, draft: VideoMetadataDraft, cover: ByteArray?, removeCover: Boolean): ByteArray {
        val children = parse(udta.payload, 0, udta.payload.size)
        val metaIndex = children.indexOfFirst { it.type == "meta" }
        val updatedMeta = if (metaIndex >= 0) updateMeta(children[metaIndex], draft, cover, removeCover)
        else metaAtom(ilstAtom(emptyList(), draft, cover, removeCover)).bytes
        val output = ByteArrayOutputStreamCompat(udta.bytes.size + 4096)
        children.forEachIndexed { index, child -> output.write(if (index == metaIndex) updatedMeta else child.bytes) }
        if (metaIndex < 0) output.write(updatedMeta)
        return atom("udta", output.toByteArray()).bytes
    }

    private fun updateMeta(meta: Atom, draft: VideoMetadataDraft, cover: ByteArray?, removeCover: Boolean): ByteArray {
        require(meta.payload.size >= 4) { "El átomo meta no es válido" }
        val flags = meta.payload.copyOfRange(0, 4)
        val children = parse(meta.payload, 4, meta.payload.size)
        val ilstIndex = children.indexOfFirst { it.type == "ilst" }
        val existing = if (ilstIndex >= 0) parse(children[ilstIndex].payload, 0, children[ilstIndex].payload.size)
        else emptyList()
        val updatedIlst = ilstAtom(existing, draft, cover, removeCover).bytes
        val output = ByteArrayOutputStreamCompat(meta.bytes.size + 4096).apply { write(flags) }
        children.forEachIndexed { index, child -> output.write(if (index == ilstIndex) updatedIlst else child.bytes) }
        if (ilstIndex < 0) output.write(updatedIlst)
        return atom("meta", output.toByteArray()).bytes
    }

    private fun metaAtom(ilst: Atom): Atom = atom("meta", byteArrayOf(0, 0, 0, 0) + ilst.bytes)

    private fun ilstAtom(existing: List<Atom>, draft: VideoMetadataDraft, cover: ByteArray?, removeCover: Boolean): Atom {
        val replacements = linkedMapOf(
            "©nam" to draft.title.trim(),
            "©cmt" to draft.description.trim(),
            "©ART" to draft.author.trim(),
            "©day" to draft.date.trim(),
            "©gen" to draft.genre.trim(),
        )
        val output = ByteArrayOutputStreamCompat(4096)
        val written = mutableSetOf<String>()
        existing.forEach { item ->
            when {
                item.type in replacements -> {
                    val text = replacements.getValue(item.type)
                    // A blank value from MediaMetadataRetriever means "not exposed", not
                    // necessarily "delete". Preserve the existing atom to avoid data loss
                    // when the user edits a different field.
                    output.write(if (text.isNotBlank()) textItem(item.type, text) else item.bytes)
                    written += item.type
                }
                item.type == "covr" && (removeCover || cover != null) -> Unit
                else -> output.write(item.bytes)
            }
        }
        replacements.forEach { (type, text) -> if (text.isNotBlank() && type !in written) output.write(textItem(type, text)) }
        if (cover != null && cover.isNotEmpty()) output.write(coverItem(cover))
        return atom("ilst", output.toByteArray())
    }

    private fun textItem(type: String, value: String): ByteArray {
        val valueBytes = value.toByteArray(Charsets.UTF_8)
        val dataPayload = byteArrayOf(0, 0, 0, 0) + intBytes(1) + byteArrayOf(0, 0, 0, 0) + valueBytes
        return atom(type, atom("data", dataPayload).bytes).bytes
    }

    private fun coverItem(bytes: ByteArray): ByteArray {
        val png = bytes.size >= 8 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4e.toByte() && bytes[3] == 0x47.toByte()
        val dataType = if (png) 14 else 13
        val dataPayload = byteArrayOf(0, 0, 0, 0) + intBytes(dataType) + byteArrayOf(0, 0, 0, 0) + bytes
        return atom("covr", atom("data", dataPayload).bytes).bytes
    }

    private fun parse(bytes: ByteArray, start: Int, end: Int): List<Atom> {
        val result = mutableListOf<Atom>()
        var offset = start
        while (offset + 8 <= end) {
            val declared = readU32(bytes, offset)
            val type = String(bytes, offset + 4, 4, Charsets.ISO_8859_1)
            val header = if (declared == 1L) 16 else 8
            val size = if (declared == 0L) (end - offset).toLong()
            else if (declared == 1L) readU64(bytes, offset + 8)
            else declared
            require(size >= header && size <= (end - offset).toLong()) { "Átomo MP4 fuera de límites" }
            require(size <= Int.MAX_VALUE) { "Átomo MP4 demasiado grande" }
            result += Atom(type, bytes.copyOfRange(offset, offset + size.toInt()), header)
            offset += size.toInt()
        }
        require(offset == end) { "Estructura MP4 incompleta" }
        return result
    }

    private fun atom(type: String, payload: ByteArray): Atom {
        val bytes = intBytes(payload.size + 8) + fourCc(type) + payload
        return Atom(type, bytes, 8)
    }

    private fun fourCc(type: String): ByteArray = when (type) {
        "©nam" -> byteArrayOf(0xA9.toByte(), 'n'.code.toByte(), 'a'.code.toByte(), 'm'.code.toByte())
        "©cmt" -> byteArrayOf(0xA9.toByte(), 'c'.code.toByte(), 'm'.code.toByte(), 't'.code.toByte())
        "©ART" -> byteArrayOf(0xA9.toByte(), 'A'.code.toByte(), 'R'.code.toByte(), 'T'.code.toByte())
        "©day" -> byteArrayOf(0xA9.toByte(), 'd'.code.toByte(), 'a'.code.toByte(), 'y'.code.toByte())
        "©gen" -> byteArrayOf(0xA9.toByte(), 'g'.code.toByte(), 'e'.code.toByte(), 'n'.code.toByte())
        else -> type.toByteArray(Charsets.ISO_8859_1).also { require(it.size == 4) }
    }

    private fun readU32(bytes: ByteArray, offset: Int): Long =
        ((bytes[offset].toLong() and 0xff) shl 24) or
            ((bytes[offset + 1].toLong() and 0xff) shl 16) or
            ((bytes[offset + 2].toLong() and 0xff) shl 8) or
            (bytes[offset + 3].toLong() and 0xff)

    private fun readU64(bytes: ByteArray, offset: Int): Long =
        ByteBuffer.wrap(bytes, offset, 8).order(ByteOrder.BIG_ENDIAN).long

    private fun intBytes(value: Int) = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(value).array()

    private class ByteArrayOutputStreamCompat(initial: Int) {
        private val out = java.io.ByteArrayOutputStream(initial)
        fun write(value: ByteArray) { out.write(value) }
        fun toByteArray(): ByteArray = out.toByteArray()
    }
}
