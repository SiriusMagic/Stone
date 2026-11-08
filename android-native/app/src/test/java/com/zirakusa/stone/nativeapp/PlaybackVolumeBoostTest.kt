package com.zirakusa.stone.nativeapp

import com.zirakusa.stone.nativeapp.playback.boostGainMillibels
import com.zirakusa.stone.nativeapp.playback.AudioMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackVolumeBoostTest {
    @Test fun boostIsUnityThroughOneHundredPercent() {
        assertEquals(0, boostGainMillibels(0, true))
        assertEquals(0, boostGainMillibels(100, true))
        assertEquals(0, boostGainMillibels(300, false))
    }

    @Test fun gainIsMonotonicAndClamped() {
        val balanced = boostGainMillibels(150, true)
        val cine = boostGainMillibels(300, true)
        assertTrue(balanced in 350..354)
        assertTrue(cine in 950..958)
        assertTrue(cine > balanced)
        assertEquals(cine, boostGainMillibels(500, true))
    }

    @Test fun playbackProfilesKeepTheirVolumeCeilings() {
        assertEquals(100, AudioMode.BATTERY.maxVolumePercent)
        assertEquals(150, AudioMode.BALANCED.maxVolumePercent)
        assertEquals(300, AudioMode.HIFI.maxVolumePercent)
    }
}
