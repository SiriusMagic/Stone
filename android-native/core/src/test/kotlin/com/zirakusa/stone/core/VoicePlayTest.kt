package com.zirakusa.stone.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class VoicePlayTest {
    private fun song(n: Int, artist: String = "Otro", title: String = "Canción $n", genre: String = "", video: Boolean = false) =
        Song("content://m/$n", title, artist, "Álbum $n", "Music", 200_000, 1, 1, isVideo = video, genre = genre)
    private val songs = (1..60).map { song(it) } + listOf(song(100, "Soda Stéreo", "De música ligera"), song(101, "Gustavo Cerati", "Crimen"),
        song(102, "Otro", "Soda en la playa"), song(103, "Soda Stereo", "Clip", video = true), song(104, "Café Tacvba", genre = "Rock"))

    @Test fun everythingShuffledWithoutVideos() {
        val all = VoicePlay.search(songs, "  ", seed = 1)
        assertEquals(songs.count { !it.isVideo }, all.size); assertTrue(all.none { it.isVideo })
        assertNotEquals(songs.filter { !it.isVideo }, all)                                  // mixed
    }

    @Test fun searchArtistFirstAccentsIgnored() {
        val soda = VoicePlay.search(songs, "soda stereo")
        assertEquals(listOf("De música ligera"), soda.map { it.title })                     // «Stéreo» matches «stereo»; no videos
        assertEquals(listOf("De música ligera", "Soda en la playa"), VoicePlay.search(songs, "soda").map { it.title })
        assertEquals(listOf("Canción 104"), VoicePlay.search(songs, "rock").map { it.title })
        assertTrue(VoicePlay.search(songs, "metallica").isEmpty())
    }

    @Test fun dailyMixIsStableForTheDayAndPrefersFavourites() {
        val day = LocalDate.of(2026, 11, 23)
        val favs = setOf(song(5).id, song(7).id)
        val plays = mapOf(song(9).id to 40)
        val mix = VoicePlay.daily(songs, plays, favs, day)
        assertEquals(VoicePlay.DAILY, mix.size); assertTrue(mix.none { it.isVideo })
        assertEquals(mix, VoicePlay.daily(songs, plays, favs, day))                          // same all day
        assertNotEquals(mix, VoicePlay.daily(songs, plays, favs, day.plusDays(1)))          // another tomorrow
        assertTrue(favs.all { f -> mix.any { it.id == f } }); assertTrue(mix.any { it.id == song(9).id })
        assertFalse(mix.map { it.id }.toSet().size < mix.size)                             // no repeats
        assertTrue(VoicePlay.daily(emptyList(), emptyMap(), emptySet(), day).isEmpty())
    }
}
