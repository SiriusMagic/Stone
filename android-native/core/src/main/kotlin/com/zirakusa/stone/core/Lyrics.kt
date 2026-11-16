package com.zirakusa.stone.core

/** One word of an enhanced LRC line (`<mm:ss.xx>word`); [timeMs] is when it starts. */
data class LyricWord(val timeMs: Long, val text: String)

/**
 * A timed lyric line. A blank [text] marks an instrumental gap (`[01:02.00]` alone),
 * so the previous phrase does not stay on screen after it ends.
 */
data class LyricLine(val timeMs: Long, val text: String, val words: List<LyricWord> = emptyList())

data class LyricsDocument(val plain: List<String>, val synced: List<LyricLine>) {
    /** True only when the lines carry real timestamps (plain lyrics are all placed at 0). */
    val isTimed: Boolean get() = synced.any { it.timeMs > 0 }
}

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
    private const val TIME = "(?:(\\d{1,2}):)?(\\d{1,3}):(\\d{2})(?:[.:,](\\d{1,3}))?"
    private val timestamp = Regex("\\[$TIME]")
    private val wordStamp = Regex("<$TIME>")
    private val tags = Regex("\\[[^]]+]")
    private val wordTags = Regex("<[^>]*>")
    private val offset = Regex("\\[offset:([+-]?\\d+)]", RegexOption.IGNORE_CASE)

    private fun millis(match: MatchResult, shift: Long): Long {
        val (h, m, s, f) = match.destructured
        val fraction = f.ifEmpty { "0" }.padEnd(3, '0').take(3).toLong()
        return (h.ifEmpty { "0" }.toLong() * 3_600_000 + m.toLong() * 60_000 + s.toLong() * 1_000 +
            fraction + shift).coerceAtLeast(0)
    }

    /** Word timings of an enhanced LRC body; empty when the line has none. */
    private fun words(body: String, shift: Long): List<LyricWord> {
        val stamps = wordStamp.findAll(body).toList()
        if (stamps.isEmpty()) return emptyList()
        return stamps.mapIndexedNotNull { index, stamp ->
            val end = stamps.getOrNull(index + 1)?.range?.first ?: body.length
            val text = body.substring(stamp.range.last + 1, end).replace(wordTags, "")
            if (text.isEmpty()) null else LyricWord(millis(stamp, shift), text)
        }
    }

    fun parse(value: String): LyricsDocument {
        val shift = offset.find(value)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        val plain = mutableListOf<String>()
        val synced = mutableListOf<LyricLine>()
        value.lineSequence().forEach { raw ->
            val body = raw.replace(tags, "")
            val text = body.replace(wordTags, "").replace(Regex("\\s+"), " ").trim()
            val stamps = timestamp.findAll(raw).toList()
            // Metadata such as [ar:…] or [offset:…] has no timestamp and no text.
            if (text.isEmpty() && stamps.isEmpty()) return@forEach
            if (text.isNotEmpty()) plain += text
            val words = words(body, shift)
            stamps.forEach { synced += LyricLine(millis(it, shift), text, words) }
        }
        // Plain lyrics have no timing information to infer. Keep every line editable as
        // synchronized data at the origin; real LRC timestamps above always take priority.
        val timedLines = if (synced.none { it.text.isNotEmpty() }) {
            plain.map { LyricLine(0L, it) }
        } else synced.distinctBy { it.timeMs to it.text }.sortedBy(LyricLine::timeMs)
            // Leading or repeated gaps add nothing to show.
            .fold(mutableListOf<LyricLine>()) { kept, line ->
                if (line.text.isEmpty() && kept.lastOrNull()?.text?.isEmpty() != false) kept
                else kept.apply { add(line) }
            }
        return LyricsDocument(plain, timedLines)
    }

    /**
     * Index of the line playing at [positionMs], or -1 before the first one. With duplicated
     * timestamps the last line of the group wins, so the result never depends on search order.
     */
    fun activeIndex(lines: List<LyricLine>, positionMs: Long): Int {
        var low = 0; var high = lines.size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (lines[mid].timeMs <= positionMs) low = mid + 1 else high = mid
        }
        return low - 1
    }

    fun toLrc(lines: List<LyricLine>): String = lines.sortedBy(LyricLine::timeMs).joinToString("\n") { line ->
        val body = if (line.words.isEmpty()) line.text
            else line.words.joinToString("") { "<${stamp(it.timeMs)}>${it.text}" }
        "[${stamp(line.timeMs)}]$body"
    }

    private fun stamp(timeMs: Long): String {
        val safe = timeMs.coerceAtLeast(0)
        return "%02d:%02d.%03d".format(safe / 60_000, (safe % 60_000) / 1_000, safe % 1_000)
    }
}
