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
) {
    val id: String get() = "local:$uri"
    fun matches(query: String) = listOf(title, artist, album, folder).any {
        it.contains(query.trim(), ignoreCase = true)
    }
    fun canReuse(newSize: Long, newModifiedMs: Long) =
        newModifiedMs > 0 && newSize > 0 && size == newSize && modifiedMs == newModifiedMs
}

data class LyricLine(val timeMs: Long, val text: String)

data class LyricsDocument(val plain: List<String>, val synced: List<LyricLine>)

object LyricsParser {
    private val timestamp = Regex("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?]")
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
                val minutes = match.groupValues[1].toLong()
                val seconds = match.groupValues[2].toLong()
                val fraction = match.groupValues[3].ifEmpty { "0" }
                    .padEnd(3, '0').take(3).toLong()
                synced += LyricLine((minutes * 60_000 + seconds * 1_000 + fraction + shift)
                    .coerceAtLeast(0), text)
            }
        }
        return LyricsDocument(plain, synced.sortedBy(LyricLine::timeMs))
    }

    fun activeIndex(lines: List<LyricLine>, positionMs: Long): Int {
        if (lines.isEmpty()) return -1
        val index = lines.binarySearchBy(positionMs) { it.timeMs }
        return if (index >= 0) index else (-index - 2).coerceAtLeast(-1)
    }
}
