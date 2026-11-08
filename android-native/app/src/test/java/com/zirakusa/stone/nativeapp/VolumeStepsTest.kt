package com.zirakusa.stone.nativeapp

import com.zirakusa.stone.nativeapp.playback.nextVolumeKeyLevel
import org.junit.Assert.assertEquals
import org.junit.Test

class VolumeStepsTest {
    @Test fun singlePressesTraverseBoostLevelsInBothDirections() {
        assertEquals(100, nextVolumeKeyLevel(98, 1, 300, false))
        assertEquals(150, nextVolumeKeyLevel(100, 1, 300, false))
        assertEquals(200, nextVolumeKeyLevel(150, 1, 300, false))
        assertEquals(300, nextVolumeKeyLevel(200, 1, 300, false))
        assertEquals(200, nextVolumeKeyLevel(300, -1, 300, false))
        assertEquals(150, nextVolumeKeyLevel(200, -1, 300, false))
        assertEquals(100, nextVolumeKeyLevel(150, -1, 300, false))
    }

    @Test fun holdingAKeyDoesNotAutomaticallyCrossBoostLevels() {
        assertEquals(100, nextVolumeKeyLevel(95, 1, 300, true))
        assertEquals(100, nextVolumeKeyLevel(100, 1, 300, true))
        assertEquals(200, nextVolumeKeyLevel(200, -1, 300, true))
        assertEquals(90, nextVolumeKeyLevel(95, -1, 300, true))
    }

    @Test fun modesRespectLimitsAndExistingIntermediateValues() {
        assertEquals(100, nextVolumeKeyLevel(100, 1, 100, false))
        assertEquals(150, nextVolumeKeyLevel(150, 1, 150, false))
        assertEquals(150, nextVolumeKeyLevel(123, 1, 300, false))
        assertEquals(100, nextVolumeKeyLevel(123, -1, 300, false))
    }
}
