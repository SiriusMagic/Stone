package com.zirakusa.stone.nativeapp.data.library.lyrics

/**
 * Cleans lyrics copied from a web page: drops credits ("Fuente: Musixmatch", "Compositores: …"),
 * buttons ("Mostrar más"), Genius artifacts ("12 Contributors", "Embed") and extra blank lines.
 */
object LyricsTextCleaner {
    /** Credit lines: a label followed by a colon, or a "provided by" phrase. */
    private val credits = Regex("(?i)^((fuente|source|compositores?|composers?|songwriters?|writer\\(s\\)|autores?|letra)\\s*:" +
        "|lyrics (provided|licensed|powered) by|written by|escrita por|letra proporcionada por).*$")
    /** Whole lines that are page buttons, never lyrics. */
    private val buttons = Regex("(?i)^(mostrar m[aá]s|show more|ver m[aá]s|see more|m[aá]s|more|embed|you might also like|" +
        "translations?|traducci[oó]n|\\d+\\s*contributors?|report a problem|denunciar|compartir|share|copiar|copy)$")
    private val trailingEmbed = Regex("\\d*\\s*Embed$")

    fun clean(raw: String): String {
        val lines = raw.replace("\r\n", "\n").replace(' ', ' ').lines()
            .map { it.trim().replace(trailingEmbed, "").trim() }
            .filterNot { credits.matches(it) || buttons.matches(it) }
        val text = lines.joinToString("\n").replace(Regex("\n{3,}"), "\n\n").trim()
        // Genius puts "Song Title Lyrics" as the first line.
        return text.lines().let { all ->
            if (all.size > 3 && all.first().endsWith(" Lyrics", ignoreCase = true)) all.drop(1).joinToString("\n").trim() else text
        }
    }
}
