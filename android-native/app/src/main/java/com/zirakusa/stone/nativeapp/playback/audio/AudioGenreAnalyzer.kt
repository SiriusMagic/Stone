package com.zirakusa.stone.nativeapp.playback.audio

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.core.net.toUri

data class AudioAnalysis(val genre: String?, val bitrate: String?, val durationMs: Long)

/** On-demand metadata classifier. It has no resident model and releases the retriever immediately. */
class AudioGenreAnalyzer(private val context: Context) {
    fun analyze(uri: String): AudioAnalysis = runCatching {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri.toUri())
            val rawGenre = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)
                ?.trim()?.takeIf { it.isNotEmpty() }
            AudioAnalysis(normalize(rawGenre),
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE),
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0)
        } finally { retriever.release() }
    }.getOrDefault(AudioAnalysis(null, null, 0))

    /** The genre tag exactly as the file has it (null when empty or unreadable). */
    fun rawGenre(uri: String): String? = runCatching {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri.toUri())
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)?.trim()?.takeIf { it.isNotEmpty() }
        } finally { retriever.release() }
    }.getOrNull()

    private fun normalize(value: String?): String? {
        val genre = value?.lowercase() ?: return null
        return when {
            "elect" in genre || "techno" in genre || "house" in genre -> "Electrónica"
            "rock" in genre || "metal" in genre -> "Rock"
            "hip" in genre || "rap" in genre -> "HipHop"
            "r&b" in genre || "rnb" in genre || "soul" in genre -> "R&B"
            "jazz" in genre -> "Jazz"
            "class" in genre || "orches" in genre -> "Clásico"
            "folk" in genre -> "Folk"
            "latin" in genre || "latino" in genre -> "Latín"
            "pop" in genre -> "Pop"
            "dance" in genre -> "Baile"
            else -> value
        }
    }
}
