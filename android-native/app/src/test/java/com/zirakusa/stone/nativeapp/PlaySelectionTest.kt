package com.zirakusa.stone.nativeapp

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import androidx.media3.session.MediaSessionService
import androidx.test.core.app.ApplicationProvider
import com.zirakusa.stone.core.Song
import com.zirakusa.stone.nativeapp.playback.control.PlaybackViewModel
import com.zirakusa.stone.nativeapp.playback.StonePlaybackService
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config
import android.os.Looper
import java.time.Duration

/** Tapping a song must highlight only that song: never the first one of the list in between. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlaySelectionTest {
    private var service: ServiceController<StonePlaybackService>? = null
    @After fun release() { service?.destroy() }

    @Test fun tappedSongIsTheOnlyHighlightedOne() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val controller = Robolectric.buildService(StonePlaybackService::class.java).create(); service = controller
        val component = ComponentName(app, StonePlaybackService::class.java)
        shadowOf(app).setComponentNameAndServiceForBindService(component,
            controller.get().onBind(Intent(MediaSessionService.SERVICE_INTERFACE).setComponent(component)))
        val playback = PlaybackViewModel(app)
        repeat(50) { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(20)) }
        val songs = (1..200).map { Song("content://fixture/$it.mp3", "Song $it", "A", "B", "M", 100_000, 1, 1) }
        val seen = mutableListOf<String>()
        fun record() { playback.state.value.currentId.let { if (seen.lastOrNull() != it) seen += it } }
        playback.play(songs[0], songs)
        repeat(100) { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(10)) }
        seen.clear()
        playback.play(songs[2], songs)
        record()
        repeat(200) { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(10)); record() }
        assertEquals(listOf(songs[2].id), seen)
    }

    @Test fun guardShowsTheTappedSongUntilThePlayerIsReadyWithIt() {
        var now = 0L
        val guard = com.zirakusa.stone.nativeapp.playback.control.RequestedItemGuard(3_000L) { now }
        assertEquals("first", guard.shown("first", ready = true)) // nothing requested
        guard.request("third")
        assertEquals("third", guard.shown("third", ready = false)) // controller's own guess
        assertEquals("third", guard.shown("first", ready = true))  // the jump the user saw
        assertEquals("third", guard.shown("third", ready = true))  // loaded: released
        assertEquals("fourth", guard.shown("fourth", ready = true)) // auto-advance later is real
        guard.request("broken")
        now = 3_001L
        assertEquals("first", guard.shown("first", ready = false)) // a broken file never pins forever
        guard.request("x"); guard.clear()
        assertEquals("first", guard.shown("first", ready = false)) // next/previous take over
    }
}
