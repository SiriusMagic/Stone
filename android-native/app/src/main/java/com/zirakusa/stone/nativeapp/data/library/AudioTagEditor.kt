package com.zirakusa.stone.nativeapp.data.library

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.core.net.toUri
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class AudioTags(val title: String = "", val artist: String = "", val album: String = "",
    val genre: String = "",
    /** New front cover (JPEG/PNG bytes) or null to keep the current one. */
    val cover: ByteArray? = null,
    val removeCover: Boolean = false,
    /**
     * Lyrics to embed (LRC timestamps are kept, as most players read them from the same tag):
     * null keeps what the file has, blank removes them.
     */
    val lyrics: String? = null,
    /** Only [lyrics] change; title, artist, album, genre and cover stay exactly as they are. */
    val lyricsOnly: Boolean = false)

/**
 * Edits tags inside the ORIGINAL audio file (MP3/ID3, FLAC/Vorbis, M4A/MP4) and then asks
 * Android to re-index it so every player shows the change. Unknown tags are preserved.
 * Headers are padded, so most edits rewrite only the tag area in place.
 */
class AudioTagEditor(private val context: Context) {
    fun supports(value: String) = MediaFileIo.extension(context, value) in supported

    fun read(value: String): AudioTags {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, value.toUri())
            fun text(key: Int) = retriever.extractMetadata(key).orEmpty()
            AudioTags(text(MediaMetadataRetriever.METADATA_KEY_TITLE),
                text(MediaMetadataRetriever.METADATA_KEY_ARTIST), text(MediaMetadataRetriever.METADATA_KEY_ALBUM),
                text(MediaMetadataRetriever.METADATA_KEY_GENRE))
        } finally { retriever.release() }
    }

    fun write(value: String, tags: AudioTags) {
        val uri = value.toUri()
        when (val extension = MediaFileIo.extension(context, value)) {
            "mp3" -> {
                val head = headWith(uri) { Id3Tag.size(it).takeIf { size -> size > 0 } ?: 0 }
                MediaFileIo.replaceHead(context, uri, Id3Tag.size(head).toLong(), Id3Tag.rebuild(head, tags))
            }
            "flac" -> {
                val head = headWith(uri) { bytes ->
                    val start = FlacTag.start(bytes)
                    if (start >= 0) FlacTag.metadataEnd(bytes, start)
                    else if (bytes.size >= Id3Tag.size(bytes) + 4) error("El archivo FLAC no es válido")
                    else -1
                }
                val start = FlacTag.start(head)
                val end = FlacTag.metadataEnd(head, start)
                require(start >= 0 && end > 0) { "El archivo FLAC no es válido" }
                MediaFileIo.replaceHead(context, uri, end.toLong(), FlacTag.rebuild(head, start, end, tags))
            }
            in mp4 -> Mp4MetadataWriter.write(context, value,
                VideoMetadataDraft(title = tags.title, author = tags.artist, album = tags.album, genre = tags.genre,
                    lyrics = tags.lyrics.orEmpty()),
                tags.cover, tags.removeCover, onlyTypes = (if (tags.lyricsOnly) emptySet()
                    else setOf("©nam", "©ART", "©alb", "©gen")) + if (tags.lyrics != null) setOf("©lyr") else emptySet())
            else -> error("El formato .$extension no admite edición de etiquetas en Stone todavía.")
        }
        MediaFileIo.rescan(context, value)
    }

    /** Saves [lyrics] (LRC or plain text) inside the original file; blank removes them. */
    fun writeLyrics(value: String, lyrics: String) = write(value, AudioTags(lyrics = lyrics, lyricsOnly = true))

    /**
     * Reads the start of the file until [needed] (total header bytes, -1 = unknown yet)
     * fits. Headers with large covers may need several MB.
     */
    private fun headWith(uri: android.net.Uri, needed: (ByteArray) -> Int): ByteArray {
        val fileSize = MediaFileIo.size(context, uri)
        var length = 256 * 1024
        while (true) {
            val head = MediaFileIo.readHead(context, uri, length)
            val required = needed(head)
            if (required in 0..head.size) return head
            if (head.size.toLong() >= fileSize || length >= MAX_HEAD) error("Etiquetas demasiado grandes o dañadas")
            length = if (required > head.size) minOf(required + 16, MAX_HEAD) else minOf(length * 4, MAX_HEAD)
        }
    }

    private companion object {
        const val MAX_HEAD = 48 * 1024 * 1024
        val mp4 = setOf("m4a", "m4b", "mp4")
        val supported = setOf("mp3", "flac") + mp4
    }

    /** Writes a new MP3 copy with an ID3 USLT frame; the selected source is never changed. */
    fun writeLyricsCopy(sourceValue: String, target: Uri, lyrics: String) {
        require(sourceValue.substringBefore('?').substringAfterLast('.', "").equals("mp3", true)) {
            "La incrustación de letras está disponible para copias MP3."
        }
        val source = context.contentResolver.openInputStream(sourceValue.toUri())?.use { it.readBytes() }
            ?: error("No se pudo abrir el archivo")
        val updated = rewriteLyrics(source, lyrics)
        context.contentResolver.openOutputStream(target, "wt")?.use { it.write(updated) }
            ?: error("No se pudo crear la copia")
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
        // The lyrics copy keeps every frame (title, artist, cover…) and replaces only USLT.
        val replaced = setOf("USLT")
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
