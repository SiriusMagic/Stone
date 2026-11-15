package com.zirakusa.stone.nativeapp.data.library

/** Values a rename template can use for one item. */
data class RenameSource(val title: String, val artist: String, val album: String, val fileName: String,
    val date: String, val index: Int)

/**
 * Bulk renaming: `{title}`, `{artist}`, `{album}`, `{file}`, `{date}`, `{n}` and `{n:3}` (zero
 * padded counter), an optional regex find/replace and a "clean" pass that removes technical
 * noise from video names (underscores, extensions, [1080p], x264, YouTube ids…).
 */
object RenameTemplate {
    private val placeholder = Regex("\\{(title|artist|album|file|date|n)(?::(\\d))?\\}")
    private val noise = Regex(
        "(?i)[\\[(](?:[^\\])]*?(?:\\d{3,4}p|4k|8k|uhd|hd|fhd|x26[45]|h\\.?26[45]|hevc|av1|vp9|aac|opus|webrip|web-?dl|" +
            "bluray|brrip|hdr|60fps|official\\s*(?:music\\s*)?video|lyric[s]?\\s*video|audio|visualizer|remaster(?:ed)?\\s*\\d*)" +
            "[^\\])]*?)[\\])]")
    private val bareNoise = Regex("(?i)\\b(?:\\d{3,4}p|x26[45]|h26[45]|hevc|webrip|web-?dl|60fps)\\b")
    /** An 11-character id at the end, after a separator (YouTube downloads: "Song [dQw4w9WgXcQ]"). */
    private val youtubeId = Regex("(^|[\\[(\\s_-])([A-Za-z0-9_-]{11})[\\])]?\\s*$")
    private val extension = Regex("(?i)\\.(mp4|m4v|mkv|webm|mov|avi|mp3|m4a|flac|ogg|opus|wav)$")

    fun render(template: String, source: RenameSource): String = placeholder.replace(template) { match ->
        when (match.groupValues[1]) {
            "title" -> source.title
            "artist" -> source.artist
            "album" -> source.album
            "file" -> source.fileName
            "date" -> source.date
            else -> source.index.toString().padStart(match.groupValues[2].toIntOrNull() ?: 1, '0')
        }
    }

    fun clean(value: String): String {
        var text = extension.replace(value.trim(), "")
        // Scene-style names (Artist.Song.Name) have no spaces and several dots; "Mr.Kitty - Song" keeps its dot.
        if (' ' !in text && text.count { it == '.' } >= 2) text = text.replace('.', ' ')
        // Only strip an 11-character tail when it looks like a YouTube id (mixed case or digits, no spaces).
        youtubeId.find(text)?.let { match ->
            val id = match.groupValues[2]
            if (match.groupValues[1].isNotEmpty() && id.any(Char::isDigit) && id.any(Char::isUpperCase) && id.any(Char::isLowerCase)) text = text.removeRange(match.range)
        }
        text = noise.replace(text, " ")
        text = bareNoise.replace(text, " ")
        text = text.replace('_', ' ')
        text = text.replace(Regex("\\s{2,}"), " ").replace(Regex("\\s+([,.)\\]])"), "$1")
        return text.trim(' ', '-', '–', '|', '.', ',')
    }
}
