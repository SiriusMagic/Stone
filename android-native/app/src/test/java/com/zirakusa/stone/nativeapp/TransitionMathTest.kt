package com.zirakusa.stone.nativeapp

import com.zirakusa.stone.nativeapp.playback.control.TransitionMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TransitionMathTest {
    private val song = 200_000L
    private val fade = 5_000L

    @Test fun fullVolumeInTheMiddle() {
        assertEquals(1f, TransitionMath.fade(100_000, song, fade, hasFollower = true), 0.001f)
    }

    @Test fun fadesInAtTheStartAndOutAtTheEnd() {
        assertEquals(0f, TransitionMath.fade(0, song, fade, true), 0.001f)
        assertEquals(0f, TransitionMath.fade(song, song, fade, true), 0.001f)
        val rising = TransitionMath.fade(2_500, song, fade, true)
        assertTrue(rising in 0.6f..0.8f) // equal-power curve: sin(45°) ≈ 0.707
    }

    @Test fun lastSongOfTheQueueDoesNotFadeOut() {
        assertEquals(1f, TransitionMath.fade(song - 1_000, song, fade, hasFollower = false), 0.001f)
    }

    @Test fun shortSongsUseAtMostAThirdForEachFade() {
        // 6 s clip, 5 s fade: the window shrinks to 2 s so the middle stays at full volume.
        assertEquals(1f, TransitionMath.fade(3_000, 6_000, fade, true), 0.001f)
    }
}
