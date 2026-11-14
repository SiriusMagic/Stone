package com.zirakusa.stone.nativeapp

import android.graphics.Bitmap
import android.graphics.Color
import com.zirakusa.stone.nativeapp.playback.SystemArtworkOverlay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SystemArtworkOverlayTest {
    @Test fun `composes logo over a copy without changing original artwork`() {
        val original = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        original.eraseColor(Color.rgb(20, 40, 60))
        val logo = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888)
        logo.eraseColor(Color.WHITE)

        val result = SystemArtworkOverlay.composeForTest(original, logo)

        requireNotNull(result)
        assertEquals(Color.rgb(20, 40, 60), original.getPixel(5, 80))
        assertEquals(Color.WHITE, result.getPixel(5, 80))
        assertEquals(Color.rgb(20, 40, 60), result.getPixel(90, 10))
        assertNotEquals(original, result)

        result.recycle()
        logo.recycle()
        original.recycle()
    }
}
