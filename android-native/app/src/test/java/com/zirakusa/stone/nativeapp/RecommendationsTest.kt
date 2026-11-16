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

class LyricsIndexTest {
    @Test fun savedLyricsWinOverTheFileAndDeletedOnesHideIt() {
        val index = com.zirakusa.stone.nativeapp.data.library.LyricsIndex
        val withFile = Song("content://a.mp3", "A", "x", "", "f", 1, 1, 1, lyrics = listOf("hola"))
        val plain = Song("content://b.mp3", "B", "x", "", "f", 1, 1, 1)
        org.junit.Assert.assertTrue(index.has(withFile))
        assertFalse(index.has(plain))
        index.onSaved(plain.id, "[00:01.00] letra")
        org.junit.Assert.assertTrue(index.has(plain))
        org.junit.Assert.assertTrue(index.has(plain.id))
        index.onSaved(withFile.id, "")
        assertFalse(index.has(withFile))
        index.onSaved(plain.id, null); index.onSaved(withFile.id, null)
    }
}

class NotificationTimeTest {
    @Test fun parsesAndFormatsScheduledTimes() {
        val scheduler = com.zirakusa.stone.nativeapp.data.library.NotificationScheduler
        assertEquals(20 * 60 + 5, scheduler.minutes("20:05", 0))
        assertEquals(99, scheduler.minutes("nope", 99))
        assertEquals(23 * 60 + 59, scheduler.minutes("27:75", 0))
        assertEquals("07:30", scheduler.format(7 * 60 + 30))
    }
}
