package com.zirakusa.stone.nativeapp

import com.zirakusa.stone.nativeapp.data.library.LyricsMatcher
import com.zirakusa.stone.nativeapp.data.library.LyricsTextCleaner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsMatchingTest {
    @Test fun buildsQueryFromVideoStyleNames() {
        val query = LyricsMatcher.query("Linkin Park - In The End (Official Video) [4K]", "<unknown>", "", 219_000)
        assertEquals("In The End", query.title)
        assertEquals("Linkin Park", query.artist)
        val featured = LyricsMatcher.query("Despacito (feat. Daddy Yankee)", "Luis Fonsi", "", 0)
        assertEquals("Despacito", featured.title)
    }

    @Test fun scoresTheRightSongAboveCoversAndOtherSongs() {
        val query = LyricsMatcher.query("In the End", "Linkin Park", "Hybrid Theory", 216_000)
        val right = LyricsMatcher.score(query, "In the End", "Linkin Park", 216.0)
        val live = LyricsMatcher.score(query, "In the End (Live)", "Linkin Park", 240.0)
        val other = LyricsMatcher.score(query, "Numb", "Linkin Park", 185.0)
        val cover = LyricsMatcher.score(query, "In the End", "Tommee Profitt", 230.0)
        assertTrue(right >= LyricsMatcher.CONFIDENT)
        assertTrue(right > live && live > cover && cover > other)
        assertTrue(other < LyricsMatcher.CONFIDENT)
        assertTrue(LyricsMatcher.similarity("Canción Ñandú", "cancion nandu") == 1.0)
    }

    @Test fun cleanerDropsCreditsButKeepsVerses() {
        val copied = """
            I tried so hard and got so far
            But in the end, it doesn't even matter
            Más allá del sol
            Fuente: Musixmatch
            Compositores: Joseph Hahn / Brad Delson / Mike Shinoda
            Mostrar más
            12 Contributors
        """.trimIndent()
        assertEquals("I tried so hard and got so far\nBut in the end, it doesn't even matter\nMás allá del sol",
            LyricsTextCleaner.clean(copied))
    }
}
