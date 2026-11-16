package com.zirakusa.stone.nativeapp.data.library

import java.text.Normalizer
import kotlin.math.abs

/** What to search for a song: title and artist cleaned of "(Official Video)", "feat." and file noise. */
data class LyricsQuery(val title: String, val artist: String, val album: String, val durationMs: Long)

/**
 * Decides whether a lyrics result belongs to a song: title and artist similarity plus duration.
 * Scores go from 0 to 1; [CONFIDENT] or more is safe to save without asking.
 */
object LyricsMatcher {
    const val CONFIDENT = 0.75
    private val unknownArtists = setOf("", "<unknown>", "unknown", "desconocido", "artista desconocido", "various artists")
    private val brackets = Regex("[(\\[{][^)\\]}]*[)\\]}]")
    private val featuring = Regex("(?i)\\s+(feat\\.?|ft\\.?|featuring|con|with)\\s+.*$")

    /** Builds the search; "Linkin Park - In the End (Official Video)" with no artist becomes title + artist. */
    fun query(title: String, artist: String, album: String, durationMs: Long): LyricsQuery {
        // Extras such as "(Official Video)", "[Lyrics]", "slowed + reverb" or "| Channel" first, then any other bracket.
        var cleanTitle = TitleCleaner.clean(title, artist).replace(brackets, " ").trim()
        var cleanArtist = artist.trim().takeUnless { it.lowercase() in unknownArtists }.orEmpty()
        if (cleanArtist.isEmpty() && " - " in cleanTitle) {
            cleanArtist = cleanTitle.substringBefore(" - ").trim()
            cleanTitle = cleanTitle.substringAfter(" - ").trim()
        } else if (cleanArtist.isNotEmpty() && cleanTitle.startsWith("$cleanArtist - ", ignoreCase = true)) {
            cleanTitle = cleanTitle.substring(cleanArtist.length + 3).trim()
        }
        cleanTitle = cleanTitle.replace(featuring, "").replace(Regex("\\s{2,}"), " ").trim()
        return LyricsQuery(cleanTitle.ifEmpty { title.trim() }, cleanArtist.replace(featuring, ""), album.trim(), durationMs)
    }

    fun score(query: LyricsQuery, title: String, artist: String, durationSeconds: Double): Double {
        val titleScore = similarity(query.title, title)
        val artistScore = if (query.artist.isEmpty()) .5 else similarity(query.artist, artist)
        val durationScore = when {
            query.durationMs <= 0 || durationSeconds <= 0 -> .5
            else -> abs(query.durationMs / 1000.0 - durationSeconds).let { diff ->
                when { diff <= 2 -> 1.0; diff <= 5 -> .8; diff <= 10 -> .5; diff <= 30 -> .2; else -> 0.0 }
            }
        }
        return (titleScore * .55 + artistScore * .3 + durationScore * .15).coerceIn(0.0, 1.0)
    }

    /** 1 when equal or one contains the other, else the share of common words. */
    fun similarity(a: String, b: String): Double {
        val left = normalize(a); val right = normalize(b)
        if (left.isEmpty() || right.isEmpty()) return 0.0
        if (left == right) return 1.0
        if (left.contains(right) || right.contains(left)) return .9
        val x = left.split(' ').toSet(); val y = right.split(' ').toSet()
        return 2.0 * x.intersect(y).size / (x.size + y.size)
    }

    fun normalize(value: String): String = Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").replace(brackets, " ").replace(featuring, "")
        .replace("&", " and ").replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
}
