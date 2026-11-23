package com.zirakusa.stone.core

import java.text.Normalizer
import java.time.LocalDate
import kotlin.math.ln
import kotlin.random.Random

/**
 * What to play when an assistant asks: «reproduce música» (everything, shuffled), «pon música de Soda Stereo» (what
 * matches, accents ignored, the artist first) and «mis recomendaciones del día» (a mix that stays the same all day and
 * changes the next one). Songs only, never videos. Pure; unit tested.
 */
object VoicePlay {
    const val DAILY = 30

    private fun plain(s: String) = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").trim()

    fun shuffled(songs: List<Song>, seed: Long): List<Song> = songs.filter { !it.isVideo }.shuffled(Random(seed))

    /** What matches [query]: the artist first, then title, album and genre. Empty when nothing matches. */
    fun search(songs: List<Song>, query: String, seed: Long = 0): List<Song> {
        val q = plain(query)
        if (q.isEmpty()) return shuffled(songs, seed)
        val audio = songs.filter { !it.isVideo }
        fun rank(s: Song) = when {
            plain(s.artist) == q -> 0
            plain(s.artist).contains(q) -> 1
            plain(s.title).contains(q) -> 2
            plain(s.album).contains(q) -> 3
            plain(s.genre).contains(q) -> 4
            else -> 9
        }
        return audio.map { it to rank(it) }.filter { it.second < 9 }.sortedBy { it.second }.map { it.first }
    }

    /**
     * «Recomendaciones del día»: favourites and the most played weigh more, plus a pinch of chance seeded by the date so
     * forgotten songs come back; the same [day] gives the same mix, in a mixed order.
     */
    fun daily(songs: List<Song>, playCounts: Map<String, Int>, favorites: Set<String>, day: LocalDate, size: Int = DAILY): List<Song> {
        val seed = day.toEpochDay()
        val luck = Random(seed)
        val scored = songs.filter { !it.isVideo }.map { s ->
            s to ((if (s.id in favorites) 2.0 else 0.0) + ln(1.0 + (playCounts[s.id] ?: 0)) * .8 + luck.nextDouble() * 1.8)
        }
        return scored.sortedByDescending { it.second }.take(size).map { it.first }.shuffled(Random(seed * 31 + 7))
    }
}
