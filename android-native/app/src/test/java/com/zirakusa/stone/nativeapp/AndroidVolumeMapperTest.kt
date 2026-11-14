package com.zirakusa.stone.nativeapp

import com.zirakusa.stone.nativeapp.playback.AndroidVolumeRange
import org.junit.Assert.assertEquals
import org.junit.Test

class AndroidVolumeMapperTest {
    private val range = AndroidVolumeRange(minLevel = 0, maxLevel = 15)

    @Test fun nativeRangeMapsItsEndpointsExactly() {
        assertEquals(0, range.levelToPercent(0))
        assertEquals(100, range.levelToPercent(15))
        assertEquals(0, range.percentToLevel(0))
        assertEquals(15, range.percentToLevel(100))
    }

    @Test fun conversionUsesNearestDiscreteAndroidLevel() {
        assertEquals(8, range.percentToLevel(50))
        assertEquals(53, range.levelToPercent(8))
        assertEquals(7, range.percentToLevel(45))
    }

    @Test fun conversionClampsOutsideNativeRange() {
        assertEquals(0, range.percentToLevel(-20))
        assertEquals(15, range.percentToLevel(140))
        assertEquals(0, range.levelToPercent(-1))
        assertEquals(100, range.levelToPercent(99))
    }
}
