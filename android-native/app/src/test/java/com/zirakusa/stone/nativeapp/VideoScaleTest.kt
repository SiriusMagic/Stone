package com.zirakusa.stone.nativeapp

import androidx.media3.ui.AspectRatioFrameLayout
import com.zirakusa.stone.nativeapp.feature.player.VideoScale
import org.junit.Assert.assertEquals
import org.junit.Test

class VideoScaleTest {
    @Test fun automaticScaleFillsHorizontalSourcesAndFitsVerticalSources() {
        assertEquals(VideoScale.ZOOM, VideoScale.AUTO.resolve(1920, 1080, false))
        assertEquals(VideoScale.FIT, VideoScale.AUTO.resolve(1080, 1920, true))
        assertEquals(VideoScale.ZOOM, VideoScale.AUTO.resolve(0, 0, true))
        assertEquals(VideoScale.FIT, VideoScale.AUTO.resolve(0, 0, false))
    }

    @Test fun scaleButtonCyclesThroughZoomFillAndFit() {
        assertEquals(VideoScale.ZOOM, VideoScale.AUTO.next())
        assertEquals(VideoScale.FILL, VideoScale.ZOOM.next())
        assertEquals(VideoScale.FIT, VideoScale.FILL.next())
        assertEquals(VideoScale.ZOOM, VideoScale.FIT.next())
    }

    @Test fun fillUsesMedia3StretchModeAndZoomPreservesAspectRatio() {
        assertEquals(AspectRatioFrameLayout.RESIZE_MODE_FILL, VideoScale.FILL.resizeMode)
        assertEquals(AspectRatioFrameLayout.RESIZE_MODE_ZOOM, VideoScale.ZOOM.resizeMode)
    }
}
