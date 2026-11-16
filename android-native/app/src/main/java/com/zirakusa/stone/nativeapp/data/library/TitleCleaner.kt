package com.zirakusa.stone.nativeapp.data.library

/**
 * Leaves only the real song title in names copied from YouTube and similar sites:
 * "Artist - Song (Official Video) [Lyrics] | Channel" → "Song".
 *
 * Removed: lyrics/letra, official video/audio, visualizer, HD/4K, slowed/reverb, "prod. by", the
 * artist repeated in the title and extra "| …" parts (channel, other names). Kept on purpose
 * because they are a different recording: remix, live, acoustic, sped up, version names.
 */
object TitleCleaner {
    private const val EXTRA = "lyrics?|lyric\\s*video|letras?|con\\s+letra|official(?:\\s+music)?(?:\\s+video|\\s+audio|\\s+visualizer)?|" +
        "video\\s+oficial|audio\\s+oficial|videoclip|music\\s+video|visuali[sz]er|audio|hd|hq|4k|full\\s+hd|" +
        "slowed(?:\\s*(?:\\+|and|&)\\s*reverb)?|reverb|8d(?:\\s+audio)?|bass\\s+boosted|nightcore|" +
        "prod\\.?(?:\\s+by)?\\s+[^)\\]]*|free\\s+download|subtitulad[oa]|traducid[oa]|sub\\.?\\s+español"
    private val bracketed = Regex("(?i)\\s*[(\\[{]\\s*(?:$EXTRA)(?:\\s*[/,+&-]\\s*(?:$EXTRA))*\\s*[)\\]}]")
    private val trailing = Regex("(?i)\\s+[-–—]\\s*(?:$EXTRA)\\s*$")
    private val bare = Regex("(?i)\\s+(?:official\\s+(?:music\\s+)?video|lyrics?|letra|slowed\\s*\\+\\s*reverb|hd|hq|4k)\\s*$")
    private val featuring = Regex("(?i)\\s*[(\\[]?\\s*(?:feat\\.?|ft\\.?|featuring)\\s+[^)\\]]*[)\\]]?")

    fun clean(title: String, artist: String = ""): String {
        var text = RenameTemplate.clean(title)
        repeat(3) { text = bracketed.replace(text, "").replace(trailing, "").replace(bare, "") }
        text = text.replace(featuring, "")
        val name = artist.trim()
        // "Artist | Song | Channel": drop parts that are the artist, keep the first one left.
        if (" | " in text || "｜" in text) {
            val parts = text.split(" | ", "｜").map { it.trim() }.filter { it.isNotEmpty() }
            text = parts.firstOrNull { name.isEmpty() || !it.equals(name, ignoreCase = true) } ?: parts.first()
        }
        // "Artist - Song" when the artist is known (or the only dash splits two short parts).
        if (name.isNotEmpty() && text.startsWith("$name - ", ignoreCase = true)) text = text.substring(name.length + 3)
        else if (name.isNotEmpty() && text.endsWith(" - $name", ignoreCase = true)) text = text.dropLast(name.length + 3)
        text = text.replace(Regex("\\s{2,}"), " ").trim(' ', '-', '–', '|', '.', ',', '·')
        return text.ifBlank { title.trim() }
    }

    /** True when [clean] would change the title (worth fixing before searching lyrics or tags). */
    fun hasExtras(title: String, artist: String = "") = clean(title, artist) != title.trim()
}
