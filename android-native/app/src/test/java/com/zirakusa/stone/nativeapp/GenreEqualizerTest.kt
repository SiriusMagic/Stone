package com.zirakusa.stone.nativeapp

import com.zirakusa.stone.nativeapp.data.persistence.SecretBox
import com.zirakusa.stone.nativeapp.playback.GenreEqualizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GenreEqualizerTest {
    private fun preset(genre: String?) = GenreEqualizer.presetFor(genre)?.name

    @Test fun genresPickTheirPreset() {
        assertEquals("Electrónica", preset("Electronic"))
        assertEquals("Electrónica", preset("EDM"))
        assertEquals("Electrónica", preset("Electrónica"))
        assertEquals("Rock", preset("Hard Rock"))
        assertEquals("Rock", preset("Pop Rock"))
        assertEquals("Rock", preset("Alternative"))
        assertEquals("Heavy metal", preset("Heavy Metal"))
        assertEquals("Latín", preset("Reggaetón"))
        assertEquals("Latín", preset("Música Latina"))
        assertEquals("HipHop", preset("Hip-Hop/Rap"))
        assertEquals("R&B", preset("R&B/Soul"))
        assertEquals("Baile", preset("Dance"))
        assertEquals("Pop", preset("K-Pop"))
        assertEquals("Clásico", preset("Classical"))
        assertEquals("Folk", preset("Country"))
    }

    @Test fun unknownOrMissingGenresKeepTheUsersOwnBands() {
        assertNull(preset(null)); assertNull(preset("")); assertNull(preset("Podcast")); assertNull(preset("(12)"))
    }

    @Test fun plainValuesFromOlderVersionsAreReadAsTheyAre() {
        assertEquals("[{\"id\":\"a\"}]", SecretBox.open("[{\"id\":\"a\"}]"))
        assertEquals("", SecretBox.open("ks1:broken:data"))
    }
}
