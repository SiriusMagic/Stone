package com.zirakusa.stone.nativeapp

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.zirakusa.stone.core.LandscapeLayoutMode
import com.zirakusa.stone.nativeapp.data.persistence.StoneSettingsStore
import com.zirakusa.stone.nativeapp.feature.player.VideoControlId
import com.zirakusa.stone.nativeapp.feature.player.VideoControlSettings
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HorizontalSettingsTest {
    @Test fun horizontalPreferenceSurvivesReopeningTheStoreWithoutChangingShortcuts() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = StoneSettingsStore(context)
        assertEquals(LandscapeLayoutMode.ALWAYS, store.read().landscapeLayoutMode)
        store.setShortcutProfile("custom")
        store.setShortcutVisible("scan", false)
        store.setLandscapeLayoutMode(LandscapeLayoutMode.LIBRARY_ONLY)
        val reopened = StoneSettingsStore(context)
        assertEquals(LandscapeLayoutMode.LIBRARY_ONLY, reopened.read().landscapeLayoutMode)
        assertFalse(reopened.shortcutVisible("scan"))
        assertEquals("custom", reopened.shortcutProfile())
    }

    @Test fun videoControlsKeepTheirOrderAndVisibilityAfterReopeningTheStore() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = StoneSettingsStore(context)
        store.setVideoControlOrder(listOf("queue", "audio_only"))
        store.setVideoControlVisible("options", false)

        val settings = VideoControlSettings.from(StoneSettingsStore(context))
        assertEquals(
            listOf(VideoControlId.Queue, VideoControlId.AudioOnly, VideoControlId.Options,
                VideoControlId.Scale, VideoControlId.Lock, VideoControlId.Fullscreen),
            settings.order,
        )
        assertEquals(listOf(VideoControlId.Queue, VideoControlId.AudioOnly,
            VideoControlId.Scale, VideoControlId.Lock, VideoControlId.Fullscreen), settings.visibleIds)
    }
}
