package com.zirakusa.stone.nativeapp

import com.zirakusa.stone.core.Song
import com.zirakusa.stone.nativeapp.feature.player.recommendations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RecommendationsTest {
    private fun song(title: String, artist: String, genre: String = "", video: Boolean = false) =
        Song("content://$title", title, artist, "", "Music", 200_000, 1, 1, isVideo = video, genre = genre)

    @Test fun prefersSameArtistAndGenreAndSkipsQueuedAndVideos() {
        val current = song("Numb", "Linkin Park", "Rock")
        val sameArtist = song("In the End", "Linkin Park", "Rock")
        val sameGenre = song("Bring Me to Life", "Evanescence", "Rock")
        val other = song("Despacito", "Luis Fonsi", "Pop")
        val queued = song("Faint", "Linkin Park", "Rock")
        val video = song("Clip", "Linkin Park", "Rock", video = true)
        val picks = recommendations(listOf(current, other, sameGenre, sameArtist, queued, video), current,
            setOf(queued.id), emptyMap(), emptySet())
        assertEquals(listOf(sameArtist, sameGenre, other), picks)
        assertFalse(current in picks)
    }
}
