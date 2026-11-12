package com.zirakusa.stone.core

data class Song(
    val uri: String,
    val title: String,
    val artist: String,
    val album: String,
    val folder: String,
    val durationMs: Long,
    val size: Long,
    val modifiedMs: Long,
    val lyrics: List<String> = emptyList(),
    val syncedLyrics: List<LyricLine> = emptyList(),
    val isVideo: Boolean = false,
    /** Optional artwork extracted from a Stone package. Normal files use embedded artwork. */
    val artworkUri: String = "",
) {
    val id: String get() = "local:$uri"
    val artworkSource: String get() = artworkUri.ifBlank { uri }
    fun matches(query: String) = listOf(title, artist, album, folder).any {
        it.contains(query.trim(), ignoreCase = true)
    }
    fun canReuse(newSize: Long, newModifiedMs: Long) =
        newModifiedMs > 0 && newSize > 0 && size == newSize && modifiedMs == newModifiedMs
}

data class LyricLine(val timeMs: Long, val text: String)

data class LyricsDocument(val plain: List<String>, val synced: List<LyricLine>)

/**
 * Returns the usable lyric document for a song, promoting LRC text stored in the
 * plain field when a separate synchronized list is not available.
 */
fun Song.lyricsDocument(): LyricsDocument = if (syncedLyrics.isNotEmpty()) {
    LyricsDocument(lyrics, syncedLyrics)
} else {
    LyricsParser.parse(lyrics.joinToString("\n"))
}

object LyricsParser {
    // Accept common LRC variants: [mm:ss], [mm:ss.5], [mm:ss,50],
    // [mm:ss:500] and the less common [hh:mm:ss].
    private val timestamp = Regex("\\[(?:(\\d{1,2}):)?(\\d{1,3}):(\\d{2})(?:[.:,](\\d{1,3}))?]")
    private val tags = Regex("\\[[^]]+]")
    private val offset = Regex("\\[offset:([+-]?\\d+)]", RegexOption.IGNORE_CASE)

    fun parse(value: String): LyricsDocument {
        val shift = offset.find(value)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        val plain = mutableListOf<String>()
        val synced = mutableListOf<LyricLine>()
        value.lineSequence().forEach { raw ->
            val text = raw.replace(tags, "").trim()
            if (text.isEmpty()) return@forEach
            plain += text
            timestamp.findAll(raw).forEach { match ->
                val hours = match.groupValues[1].ifEmpty { "0" }.toLong()
                val minutes = match.groupValues[2].toLong()
                val seconds = match.groupValues[3].toLong()
                val fraction = match.groupValues[4].ifEmpty { "0" }
                    .padEnd(3, '0').take(3).toLong()
                synced += LyricLine((hours * 3_600_000 + minutes * 60_000 + seconds * 1_000 + fraction + shift)
                    .coerceAtLeast(0), text)
            }
        }
        // Plain lyrics have no timing information to infer. Keep every line editable as
        // synchronized data at the origin; real LRC timestamps above always take priority
        // and remain unchanged.
        val timedLines = if (synced.isEmpty()) {
            plain.map { LyricLine(0L, it) }
        } else synced.sortedBy(LyricLine::timeMs)
        return LyricsDocument(plain, timedLines)
    }

    fun activeIndex(lines: List<LyricLine>, positionMs: Long): Int {
        if (lines.isEmpty()) return -1
        val index = lines.binarySearchBy(positionMs) { it.timeMs }
        return if (index >= 0) index else (-index - 2).coerceAtLeast(-1)
    }

    fun toLrc(lines: List<LyricLine>): String = lines.sortedBy(LyricLine::timeMs).joinToString("\n") { line ->
        val safeTime = line.timeMs.coerceAtLeast(0)
        val minutes = safeTime / 60_000
        val seconds = (safeTime % 60_000) / 1_000
        val millis = safeTime % 1_000
        "[%02d:%02d.%03d]%s".format(minutes, seconds, millis, line.text)
    }
}
