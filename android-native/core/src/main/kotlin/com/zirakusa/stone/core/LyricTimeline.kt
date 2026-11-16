package com.zirakusa.stone.core

/** Where playback is inside the lyrics: line -1 is before the first phrase, word -1 means none yet. */
data class LyricCursor(val line: Int, val word: Int = -1) {
    companion object { val None = LyricCursor(-1) }
}

/**
 * Pure timing logic over a document, free of Android so it can be unit tested. The player's
 * position is always the input: nothing here keeps its own clock.
 */
class LyricTimeline(val document: LyricsDocument) {
    val lines: List<LyricLine> = if (document.isTimed) document.synced else emptyList()
    val isTimed: Boolean get() = lines.isNotEmpty()

    fun cursorAt(positionMs: Long): LyricCursor {
        val line = LyricsParser.activeIndex(lines, positionMs)
        val words = lines.getOrNull(line)?.words.orEmpty()
        if (words.isEmpty()) return LyricCursor(line)
        var word = -1
        for (i in words.indices) if (words[i].timeMs <= positionMs) word = i else break
        return LyricCursor(line, word)
    }

    /** Next instant after [positionMs] at which [cursorAt] changes, or null when nothing follows. */
    fun nextChangeAfter(positionMs: Long): Long? {
        val line = LyricsParser.activeIndex(lines, positionMs)
        val nextLine = lines.getOrNull(line + 1)?.timeMs
        val nextWord = lines.getOrNull(line)?.words?.firstOrNull { it.timeMs > positionMs }?.timeMs
        return listOfNotNull(nextLine, nextWord).minOrNull()
    }
}
