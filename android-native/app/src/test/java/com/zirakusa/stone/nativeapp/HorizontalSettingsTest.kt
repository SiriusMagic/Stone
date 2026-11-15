package com.zirakusa.stone.nativeapp

import com.zirakusa.stone.nativeapp.feature.library.components.LibraryTab
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.zirakusa.stone.core.LandscapeLayoutMode
import com.zirakusa.stone.nativeapp.data.persistence.StoneSettingsStore
import com.zirakusa.stone.nativeapp.feature.player.VideoControlId
import com.zirakusa.stone.nativeapp.feature.player.VideoControlSettings
import org.junit.Assert.assertFalse
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
            listOf(VideoControlId.Queue, VideoControlId.AudioOnly, VideoControlId.Scale, VideoControlId.Rotate),
            settings.order,
        )
        assertEquals(listOf(VideoControlId.Queue, VideoControlId.AudioOnly,
            VideoControlId.Scale, VideoControlId.Rotate), settings.visibleIds)
    }

    @Test fun videoLayoutAndImmersivePreferencesHaveSafeDefaultsAndPersist() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = StoneSettingsStore(context)
        val defaults = store.read()
        assertEquals("auto", defaults.videoScale)
        assertEquals("grid", defaults.videoLibraryLayout)
        assertTrue(defaults.immersiveEnabled)

        store.setVideoLibraryLayout("list")
        store.setImmersiveEnabled(false)
        val reopened = StoneSettingsStore(context).read()
        assertEquals("list", reopened.videoLibraryLayout)
        assertFalse(reopened.immersiveEnabled)

        // Do not leak this test's preference changes into other persistence tests.
        store.setVideoLibraryLayout("grid")
        store.setImmersiveEnabled(true)
    }

    @Test fun videoTabsSplitByDuration() {
        fun video(ms: Long) = com.zirakusa.stone.core.Song("content://v/$ms.mp4", "v", "a", "", "f", ms, 1, 1, isVideo = true)
        assertTrue(LibraryTab.Shorts.accepts(video(60_000)))
        assertTrue(LibraryTab.Videos.accepts(video(90_000)))
        assertTrue(LibraryTab.Videos.accepts(video(3_600_000)))
        assertTrue(LibraryTab.Movies.accepts(video(3_700_000)))
        assertTrue(LibraryTab.Videos.accepts(video(0)))
        assertFalse(LibraryTab.Videos.accepts(video(60_000)))
    }
}
