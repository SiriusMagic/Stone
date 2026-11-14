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
        store.setShortcutVisible("sort", false)
        store.setLandscapeLayoutMode(LandscapeLayoutMode.LIBRARY_ONLY)
        val reopened = StoneSettingsStore(context)
        assertEquals(LandscapeLayoutMode.LIBRARY_ONLY, reopened.read().landscapeLayoutMode)
        assertFalse(reopened.shortcutVisible("sort"))
        assertEquals("custom", reopened.shortcutProfile())
        store.setShortcutVisible("sort", true)
    }

    @Test fun videoControlsKeepTheirOrderAndVisibilityAfterReopeningTheStore() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = StoneSettingsStore(context)
        store.setVideoControlOrder(listOf("queue", "audio_only"))
        store.setVideoControlVisible("options", false)

        val settings = VideoControlSettings.from(StoneSettingsStore(context))
        assertEquals(
            listOf(VideoControlId.Queue, VideoControlId.AudioOnly, VideoControlId.Options,
                VideoControlId.Scale, VideoControlId.Lock),
            settings.order,
        )
        assertEquals(listOf(VideoControlId.Queue, VideoControlId.AudioOnly,
            VideoControlId.Scale, VideoControlId.Lock), settings.visibleIds)
    }

    @Test fun videoLayoutAndImmersivePreferencesHaveSafeDefaultsAndPersist() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = StoneSettingsStore(context)
        val defaults = store.read()
        assertEquals("auto", defaults.videoScale)
        assertEquals("grid", defaults.videoLibraryLayout)
        assertTrue(defaults.immersivePlayers)
        assertFalse(defaults.immersiveLibrary)

        store.setVideoLibraryLayout("list")
        store.setImmersivePlayers(false)
        store.setImmersiveLibrary(true)
        val reopened = StoneSettingsStore(context).read()
        assertEquals("list", reopened.videoLibraryLayout)
        assertFalse(reopened.immersivePlayers)
        assertTrue(reopened.immersiveLibrary)

        // Do not leak this test's preference changes into other persistence tests.
        store.setVideoLibraryLayout("grid")
        store.setImmersivePlayers(true)
        store.setImmersiveLibrary(false)
    }
}
