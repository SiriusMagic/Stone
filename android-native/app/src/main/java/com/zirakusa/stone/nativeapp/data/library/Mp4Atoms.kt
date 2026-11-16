package com.zirakusa.stone.nativeapp.data.library

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Pure MP4 box logic for metadata editing. It rebuilds only `moov/udta/meta/ilst` and,
 * when the movie box moves the media data, shifts every `stco`/`co64` chunk offset so the
 * samples still point at the same bytes. Audio and video are never decoded or re-encoded.
 */
internal object Mp4Atoms {
    class Atom(val type: String, val bytes: ByteArray, val headerSize: Int) {
        val payload: ByteArray get() = bytes.copyOfRange(headerSize, bytes.size)
    }

    private val containers = setOf("trak", "mdia", "minf", "stbl", "edts", "dinf", "mvex")

    /** Returns the new `moov` box. [mediaShift] is added to chunk offsets that follow it. */
    fun rebuildMoov(moov: ByteArray, draft: VideoMetadataDraft, cover: ByteArray?,
        removeCover: Boolean, mediaShift: Long = 0L, only: Set<String>? = null): ByteArray {
        onlyTypes.set(only)
        try { return rebuildMoovInternal(moov, draft, cover, removeCover, mediaShift) } finally { onlyTypes.remove() }
    }

    /** When set, tags outside this set are neither written nor removed (audio editor). */
    private val onlyTypes = ThreadLocal<Set<String>?>()

    private fun rebuildMoovInternal(moov: ByteArray, draft: VideoMetadataDraft, cover: ByteArray?,
        removeCover: Boolean, mediaShift: Long): ByteArray {
        val root = parse(moov, 0, moov.size).single()
        require(root.type == "moov") { "El átomo moov no es válido" }
        val children = parse(root.bytes, root.headerSize, root.bytes.size)
        val out = ByteArrayOutputStream(moov.size + 4096)
        var wroteUdta = false
        children.forEach { child ->
            when (child.type) {
                "udta" -> { out.write(updateUdta(child, draft, cover, removeCover)); wroteUdta = true }
                else -> out.write(if (mediaShift != 0L) shiftOffsets(child, mediaShift) else child.bytes)
            }
        }
        if (!wroteUdta) out.write(atom("udta", newMeta(draft, cover, removeCover)))
        return atom("moov", out.toByteArray())
    }

    /** Reads iTunes-style text tags from a `moov` box, keyed by atom type (e.g. `©nam`). */
    fun readTags(moov: ByteArray): Map<String, String> {
        val root = parse(moov, 0, moov.size).singleOrNull() ?: return emptyMap()
        val udta = parse(root.bytes, root.headerSize, root.bytes.size).firstOrNull { it.type == "udta" } ?: return emptyMap()
        val meta = parse(udta.bytes, udta.headerSize, udta.bytes.size).firstOrNull { it.type == "meta" } ?: return emptyMap()
        val ilst = parse(meta.bytes, meta.headerSize + 4, meta.bytes.size).firstOrNull { it.type == "ilst" } ?: return emptyMap()
        return parse(ilst.bytes, ilst.headerSize, ilst.bytes.size).mapNotNull { item ->
            val data = parse(item.bytes, item.headerSize, item.bytes.size).firstOrNull { it.type == "data" }
                ?: return@mapNotNull null
            val start = data.headerSize + 8
            if (data.bytes.size <= start || data.bytes[data.headerSize + 3].toInt() != 1) return@mapNotNull null
            item.type to String(data.bytes, start, data.bytes.size - start, Charsets.UTF_8)
        }.toMap()
    }

    private fun shiftOffsets(box: Atom, shift: Long): ByteArray = when (box.type) {
        "stco" -> {
            val bytes = box.bytes.copyOf()
            val count = readU32(bytes, box.headerSize + 4).toInt()
            repeat(count) { index ->
                val at = box.headerSize + 8 + index * 4
                val shifted = readU32(bytes, at) + shift
                require(shifted in 0..0xFFFFFFFFL) { "El desplazamiento MP4 no cabe en stco" }
                ByteBuffer.wrap(bytes, at, 4).order(ByteOrder.BIG_ENDIAN).putInt(shifted.toInt())
            }
            bytes
        }
        "co64" -> {
            val bytes = box.bytes.copyOf()
            val count = readU32(bytes, box.headerSize + 4).toInt()
            repeat(count) { index ->
                val buffer = ByteBuffer.wrap(bytes, box.headerSize + 8 + index * 8, 8).order(ByteOrder.BIG_ENDIAN)
                val value = buffer.getLong(box.headerSize + 8 + index * 8)
                buffer.putLong(box.headerSize + 8 + index * 8, value + shift)
            }
            bytes
        }
        in containers -> {
            val out = ByteArrayOutputStream(box.bytes.size)
            parse(box.bytes, box.headerSize, box.bytes.size).forEach { out.write(shiftOffsets(it, shift)) }
            atom(box.type, out.toByteArray())
        }
        else -> box.bytes
    }

    private fun updateUdta(udta: Atom, draft: VideoMetadataDraft, cover: ByteArray?, removeCover: Boolean): ByteArray {
        val children = parse(udta.bytes, udta.headerSize, udta.bytes.size)
        val out = ByteArrayOutputStream(udta.bytes.size + 4096)
        var wroteMeta = false
        children.forEach { child ->
            if (child.type == "meta") { out.write(updateMeta(child, draft, cover, removeCover)); wroteMeta = true }
            else out.write(child.bytes)
        }
        if (!wroteMeta) out.write(newMeta(draft, cover, removeCover))
        return atom("udta", out.toByteArray())
    }

    private fun updateMeta(meta: Atom, draft: VideoMetadataDraft, cover: ByteArray?, removeCover: Boolean): ByteArray {
        require(meta.bytes.size >= meta.headerSize + 4) { "El átomo meta no es válido" }
        val start = meta.headerSize + 4
        val children = parse(meta.bytes, start, meta.bytes.size)
        val out = ByteArrayOutputStream(meta.bytes.size + 4096)
        out.write(meta.bytes, meta.headerSize, 4)
        if (children.none { it.type == "hdlr" }) out.write(handler())
        var wroteIlst = false
        children.forEach { child ->
            if (child.type == "ilst") {
                val items = parse(child.bytes, child.headerSize, child.bytes.size)
                out.write(ilst(items, draft, cover, removeCover)); wroteIlst = true
            } else out.write(child.bytes)
        }
        if (!wroteIlst) out.write(ilst(emptyList(), draft, cover, removeCover))
        return atom("meta", out.toByteArray())
    }

    private fun newMeta(draft: VideoMetadataDraft, cover: ByteArray?, removeCover: Boolean) =
        atom("meta", ByteArray(4) + handler() + ilst(emptyList(), draft, cover, removeCover))

    /** iTunes metadata handler; without it many readers ignore the `ilst` box. */
    private fun handler() = atom("hdlr", ByteArray(8) + "mdirappl".toByteArray(Charsets.ISO_8859_1) + ByteArray(9))

    private fun ilst(existing: List<Atom>, draft: VideoMetadataDraft, cover: ByteArray?, removeCover: Boolean): ByteArray {
        val replacements = linkedMapOf(
            "©nam" to draft.title.trim(), "©cmt" to draft.description.trim(),
            "desc" to draft.description.trim(), "©ART" to draft.author.trim(),
            "©alb" to draft.album.trim(), "©day" to draft.date.trim(),
            "©gen" to draft.genre.trim(), "keyw" to draft.keywords.trim(),
            "©lyr" to draft.lyrics.trim(),
        // Lyrics are only touched when asked for, so the video editor never erases them.
        ).filterKeys { key -> onlyTypes.get()?.contains(key) ?: (key != "©lyr") }
        val out = ByteArrayOutputStream(4096)
        val written = mutableSetOf<String>()
        existing.forEach { item ->
            when {
                item.type in replacements -> {
                    val text = replacements.getValue(item.type)
                    // An empty field removes the tag: the editor shows existing values, so a
                    // blank field is an explicit user choice.
                    if (text.isNotBlank()) out.write(textItem(item.type, text))
                    written += item.type
                }
                item.type == "covr" && (removeCover || cover != null) -> Unit
                else -> out.write(item.bytes)
            }
        }
        replacements.forEach { (type, text) -> if (text.isNotBlank() && type !in written) out.write(textItem(type, text)) }
        if (cover != null && cover.isNotEmpty()) out.write(coverItem(cover))
        return atom("ilst", out.toByteArray())
    }

    private fun textItem(type: String, value: String): ByteArray =
        atom(type, atom("data", ByteArray(3) + byteArrayOf(1) + ByteArray(4) + value.toByteArray(Charsets.UTF_8)))

    private fun coverItem(bytes: ByteArray): ByteArray {
        val png = bytes.size >= 4 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte()
        return atom("covr", atom("data", ByteArray(3) + byteArrayOf(if (png) 14 else 13) + ByteArray(4) + bytes))
    }

    fun parse(bytes: ByteArray, start: Int, end: Int): List<Atom> {
        val result = mutableListOf<Atom>()
        var offset = start
        while (offset + 8 <= end) {
            val declared = readU32(bytes, offset)
            val header = if (declared == 1L) 16 else 8
            val size = when (declared) {
                0L -> (end - offset).toLong()
                1L -> ByteBuffer.wrap(bytes, offset + 8, 8).order(ByteOrder.BIG_ENDIAN).long
                else -> declared
            }
            require(size >= header && size <= (end - offset).toLong()) { "Átomo MP4 fuera de límites" }
            result += Atom(String(bytes, offset + 4, 4, Charsets.ISO_8859_1),
                bytes.copyOfRange(offset, offset + size.toInt()), header)
            offset += size.toInt()
        }
        // Some encoders pad container boxes with fewer than 8 trailing zero bytes.
        require(offset == end || (offset until end).all { bytes[it] == 0.toByte() }) { "Estructura MP4 incompleta" }
        return result
    }

    fun atom(type: String, payload: ByteArray): ByteArray =
        ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(payload.size + 8).array() +
            fourCc(type) + payload

    private fun fourCc(type: String): ByteArray =
        if (type.startsWith("©")) byteArrayOf(0xA9.toByte()) + type.substring(1).toByteArray(Charsets.ISO_8859_1)
        else type.toByteArray(Charsets.ISO_8859_1).also { require(it.size == 4) }

    fun readU32(bytes: ByteArray, offset: Int): Long =
        ByteBuffer.wrap(bytes, offset, 4).order(ByteOrder.BIG_ENDIAN).int.toLong() and 0xFFFFFFFFL
}
