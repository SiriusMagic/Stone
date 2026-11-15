package com.zirakusa.stone.nativeapp.data.library

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Pure tag builders (no I/O) so they can be unit tested. */
internal object Id3Tag {
    const val PADDING = 2048
    private val replacedText = mapOf("title" to "TIT2", "artist" to "TPE1", "album" to "TALB", "genre" to "TCON")

    /** Size in bytes of the ID3v2 tag at the start of [head] (0 when there is none). */
    fun size(head: ByteArray): Int {
        if (head.size < 10 || String(head, 0, 3, Charsets.ISO_8859_1) != "ID3") return 0
        val footer = if (head[3].toInt() == 4 && head[5].toInt() and 0x10 != 0) 10 else 0
        return 10 + syncSafe(head, 6) + footer
    }

    /**
     * Builds a new tag. [head] must contain at least the old tag. When the result fits in
     * the old size it is padded to exactly that size so the file can be edited in place.
     */
    fun rebuild(head: ByteArray, tags: AudioTags): ByteArray {
        val oldSize = size(head)
        val version = if (oldSize > 0 && head[3].toInt() in 3..4) head[3].toInt() else 3
        var body = if (oldSize > 0 && head[3].toInt() in 3..4) head.copyOfRange(10, oldSize - footer(head)) else ByteArray(0)
        val flags = if (oldSize > 0) head[5].toInt() else 0
        if (flags and 0x80 != 0) body = resync(body)
        if (flags and 0x40 != 0 && body.size >= 4) {
            val extended = if (version == 4) syncSafe(body, 0) else 4 + ByteBuffer.wrap(body, 0, 4).int
            body = body.copyOfRange(extended.coerceIn(0, body.size), body.size)
        }
        val replaced = replacedText.values.toMutableSet()
        if (tags.cover != null || tags.removeCover) replaced += "APIC"
        val frames = ByteArrayOutputStream().apply {
            write(keepFrames(body, version, replaced))
            textFrame(this, version, "TIT2", tags.title)
            textFrame(this, version, "TPE1", tags.artist)
            textFrame(this, version, "TALB", tags.album)
            textFrame(this, version, "TCON", tags.genre)
            tags.cover?.let { pictureFrame(this, version, it) }
        }.toByteArray()
        val total = if (10 + frames.size <= oldSize) oldSize else 10 + frames.size + PADDING
        return ByteArrayOutputStream(total).apply {
            write("ID3".toByteArray(Charsets.ISO_8859_1)); write(byteArrayOf(version.toByte(), 0, 0))
            write(syncSafeBytes(total - 10)); write(frames); write(ByteArray(total - 10 - frames.size))
        }.toByteArray()
    }

    private fun footer(head: ByteArray) = if (head[3].toInt() == 4 && head[5].toInt() and 0x10 != 0) 10 else 0

    private fun resync(bytes: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(bytes.size)
        var index = 0
        while (index < bytes.size) {
            out.write(bytes[index].toInt())
            if (bytes[index] == 0xFF.toByte() && index + 1 < bytes.size && bytes[index + 1] == 0.toByte()) index++
            index++
        }
        return out.toByteArray()
    }

    private fun keepFrames(body: ByteArray, version: Int, replaced: Set<String>): ByteArray {
        val kept = ByteArrayOutputStream()
        var offset = 0
        while (offset + 10 <= body.size) {
            val id = String(body, offset, 4, Charsets.ISO_8859_1)
            if (!id.all { it.isLetterOrDigit() }) break
            val size = if (version == 4) syncSafe(body, offset + 4) else ByteBuffer.wrap(body, offset + 4, 4).int
            val end = offset + 10 + size
            if (size < 0 || end > body.size) break
            if (id !in replaced) kept.write(body, offset, end - offset)
            offset = end
        }
        return kept.toByteArray()
    }

    private fun textFrame(out: ByteArrayOutputStream, version: Int, id: String, value: String) {
        if (value.isBlank()) return
        val text = if (version == 4) value.trim().toByteArray(Charsets.UTF_8) else value.trim().toByteArray(Charsets.UTF_16)
        frame(out, version, id, byteArrayOf(if (version == 4) 3 else 1) + text)
    }

    private fun pictureFrame(out: ByteArrayOutputStream, version: Int, image: ByteArray) {
        val mime = if (isPng(image)) "image/png" else "image/jpeg"
        frame(out, version, "APIC", byteArrayOf(0) + mime.toByteArray(Charsets.ISO_8859_1) +
            byteArrayOf(0, 3, 0) + image)
    }

    private fun frame(out: ByteArrayOutputStream, version: Int, id: String, payload: ByteArray) {
        out.write(id.toByteArray(Charsets.ISO_8859_1))
        out.write(if (version == 4) syncSafeBytes(payload.size) else
            ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(payload.size).array())
        out.write(byteArrayOf(0, 0)); out.write(payload)
    }

    fun syncSafe(bytes: ByteArray, offset: Int) = ((bytes[offset].toInt() and 0x7f) shl 21) or
        ((bytes[offset + 1].toInt() and 0x7f) shl 14) or ((bytes[offset + 2].toInt() and 0x7f) shl 7) or
        (bytes[offset + 3].toInt() and 0x7f)

    fun syncSafeBytes(value: Int) = byteArrayOf(((value shr 21) and 0x7f).toByte(),
        ((value shr 14) and 0x7f).toByte(), ((value shr 7) and 0x7f).toByte(), (value and 0x7f).toByte())

    fun isPng(bytes: ByteArray) = bytes.size >= 4 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte()
}

/** FLAC metadata: Vorbis comments and the front-cover PICTURE block. */
internal object FlacTag {
    private const val STREAMINFO = 0
    private const val PADDING = 1
    private const val VORBIS = 4
    private const val PICTURE = 6
    private val keys = mapOf("TITLE" to AudioTags::title, "ARTIST" to AudioTags::artist,
        "ALBUM" to AudioTags::album, "GENRE" to AudioTags::genre)

    class Block(val type: Int, val data: ByteArray)

    /** Offset of "fLaC" (after an optional ID3 tag) or -1. */
    fun start(head: ByteArray): Int {
        val offset = Id3Tag.size(head)
        return if (head.size >= offset + 4 && String(head, offset, 4, Charsets.ISO_8859_1) == "fLaC") offset else -1
    }

    /** End offset of the metadata blocks, reading headers only from [head]. */
    fun metadataEnd(head: ByteArray, start: Int): Int {
        var offset = start + 4
        while (offset + 4 <= head.size) {
            val last = head[offset].toInt() and 0x80 != 0
            val length = u24(head, offset + 1)
            offset += 4 + length
            if (last) return offset
        }
        return -1
    }

    fun blocks(head: ByteArray, start: Int, end: Int): List<Block> {
        val result = mutableListOf<Block>()
        var offset = start + 4
        while (offset + 4 <= end) {
            val type = head[offset].toInt() and 0x7f
            val length = u24(head, offset + 1)
            result += Block(type, head.copyOfRange(offset + 4, offset + 4 + length))
            offset += 4 + length
        }
        return result
    }

    fun readComments(head: ByteArray): Map<String, String> {
        val start = start(head).takeIf { it >= 0 } ?: return emptyMap()
        val end = metadataEnd(head, start).takeIf { it > 0 && it <= head.size } ?: return emptyMap()
        val block = blocks(head, start, end).firstOrNull { it.type == VORBIS } ?: return emptyMap()
        return comments(block.data).mapNotNull { entry ->
            entry.substringBefore('=', "").takeIf { it.isNotEmpty() }?.uppercase()?.let { it to entry.substringAfter('=') }
        }.toMap()
    }

    /** Returns the new head (ID3 prefix + "fLaC" + blocks), padded to the old size if it fits. */
    fun rebuild(head: ByteArray, start: Int, end: Int, tags: AudioTags): ByteArray {
        val old = blocks(head, start, end)
        val vendor: ByteArray
        val kept: List<String>
        val existing = old.firstOrNull { it.type == VORBIS }
        if (existing != null) {
            val buffer = ByteBuffer.wrap(existing.data).order(ByteOrder.LITTLE_ENDIAN)
            vendor = ByteArray(buffer.int).also { buffer.get(it) }
            kept = comments(existing.data).filterNot { it.substringBefore('=').uppercase() in keys }
        } else {
            vendor = "Stone".toByteArray(Charsets.UTF_8); kept = emptyList()
        }
        val added = keys.mapNotNull { (key, field) -> field(tags).trim().takeIf { it.isNotEmpty() }?.let { "$key=$it" } }
        val vorbis = ByteArrayOutputStream().apply {
            write(le32(vendor.size)); write(vendor); write(le32(kept.size + added.size))
            (kept + added).forEach { val bytes = it.toByteArray(Charsets.UTF_8); write(le32(bytes.size)); write(bytes) }
        }.toByteArray()
        val replacePicture = tags.cover != null || tags.removeCover
        val result = mutableListOf<Block>()
        old.forEach { block ->
            when {
                block.type == PADDING || block.type == VORBIS -> Unit
                block.type == PICTURE && replacePicture && pictureType(block.data) == 3 -> Unit
                else -> result += block
            }
            if (block.type == STREAMINFO) result += Block(VORBIS, vorbis)
        }
        if (result.none { it.type == VORBIS }) result.add(minOf(1, result.size), Block(VORBIS, vorbis))
        tags.cover?.let { result += Block(PICTURE, picture(it)) }
        val used = 4 + result.sumOf { 4 + it.data.size }
        val oldMetadata = end - start
        val padding = if (oldMetadata - used >= 4) oldMetadata - used - 4 else 4096
        result += Block(PADDING, ByteArray(padding))
        return ByteArrayOutputStream().apply {
            write(head, 0, start)
            write("fLaC".toByteArray(Charsets.ISO_8859_1))
            result.forEachIndexed { index, block ->
                val flag = if (index == result.lastIndex) 0x80 else 0
                write(flag or block.type)
                write(block.data.size shr 16 and 0xff); write(block.data.size shr 8 and 0xff); write(block.data.size and 0xff)
                write(block.data)
            }
        }.toByteArray()
    }

    private fun comments(data: ByteArray): List<String> = runCatching {
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        buffer.position(4 + buffer.int)
        List(buffer.int) { ByteArray(buffer.int).also { buffer.get(it) }.toString(Charsets.UTF_8) }
    }.getOrDefault(emptyList())

    private fun pictureType(data: ByteArray) = if (data.size >= 4) ByteBuffer.wrap(data, 0, 4).int else -1

    private fun picture(image: ByteArray): ByteArray {
        val mime = (if (Id3Tag.isPng(image)) "image/png" else "image/jpeg").toByteArray(Charsets.US_ASCII)
        return ByteBuffer.allocate(32 + mime.size + image.size).order(ByteOrder.BIG_ENDIAN)
            .putInt(3).putInt(mime.size).put(mime).putInt(0)
            .putInt(0).putInt(0).putInt(0).putInt(0).putInt(image.size).put(image).array()
    }

    private fun le32(value: Int) = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()
    private fun u24(bytes: ByteArray, offset: Int) = ((bytes[offset].toInt() and 0xff) shl 16) or
        ((bytes[offset + 1].toInt() and 0xff) shl 8) or (bytes[offset + 2].toInt() and 0xff)
}
