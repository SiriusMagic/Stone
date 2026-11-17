package com.zirakusa.stone.nativeapp.data.library

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.core.net.toUri

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
}
