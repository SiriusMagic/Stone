package com.zirakusa.stone.nativeapp

import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.zirakusa.stone.core.Song
import com.zirakusa.stone.nativeapp.feature.player.MiniPlayer
import com.zirakusa.stone.nativeapp.feature.player.PlayerScreen
import com.zirakusa.stone.nativeapp.playback.PlaybackState
import com.zirakusa.stone.nativeapp.playback.PlaybackViewModel
import com.zirakusa.stone.nativeapp.playback.QueueEntry
import com.zirakusa.stone.nativeapp.ui.theme.StoneLook
import com.zirakusa.stone.nativeapp.ui.theme.StoneTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Draws the portrait music player and the mini player while a song plays (every cover shape). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "es-w411dp-h891dp-xhdpi")
class PortraitPlayerCrashTest {
    @get:Rule val compose = createComposeRule()
    private val song = Song("content://fixture/a.mp3", "Metamorphosis", "Interworld", "Album", "Music", 142_000, 1, 1)
    private val state = PlaybackState(ready = true, title = song.title, artist = song.artist, playing = true,
        queue = listOf(QueueEntry(song.id, song.title, song.artist)), index = 0, currentId = song.id)

    private var service: org.robolectric.android.controller.ServiceController<com.zirakusa.stone.nativeapp.playback.StonePlaybackService>? = null

    // Robolectric cannot bind a MediaSessionService by itself: hand it the real service's binder.
    private fun connectPlayback(): PlaybackViewModel {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val controller = org.robolectric.Robolectric.buildService(com.zirakusa.stone.nativeapp.playback.StonePlaybackService::class.java).create()
        service = controller
        val component = android.content.ComponentName(app, com.zirakusa.stone.nativeapp.playback.StonePlaybackService::class.java)
        val binder = controller.get().onBind(android.content.Intent(androidx.media3.session.MediaSessionService.SERVICE_INTERFACE).setComponent(component))
        org.robolectric.Shadows.shadowOf(app).setComponentNameAndServiceForBindService(component, binder)
        return PlaybackViewModel(app)
    }

    // One media session per process: release it so the next test can create its own.
    @org.junit.After fun release() { service?.destroy() }

    @Test fun portraitPlayerAndMiniPlayerWhilePlaying() {
        val playback = connectPlayback()
        compose.mainClock.autoAdvance = false
        compose.setContent { StoneTheme { Surface {
            PlayerScreen(state, song, playback, back = {}, onDelete = {}, onMetadataChanged = {}, playlists = emptyList(),
                onCreatePlaylist = {}, onAddToPlaylist = { _, _ -> })
            MiniPlayer(state, {}, {}, song = song, onPlayMode = {}, progress = { .3f })
        } } }
        for (shape in StoneLook.ARTWORK_SHAPES) {
            StoneLook.artworkShape = shape
            repeat(30) { compose.mainClock.advanceTimeBy(100) }
        }
        StoneLook.artworkShape = "square"
    }

    /** Opening a video from the mini player in portrait (the landscape player already worked). */
    @Test fun portraitVideoPlayerWhilePlaying() {
        val playback = connectPlayback()
        val video = song.copy(uri = "content://fixture/v.mp4", title = "Clip", isVideo = true)
        val videoState = state.copy(title = video.title, queue = listOf(QueueEntry(video.id, video.title, video.artist)),
            currentId = video.id)
        compose.mainClock.autoAdvance = false
        compose.setContent { StoneTheme { Surface {
            PlayerScreen(videoState, video, playback, back = {}, onDelete = {}, onMetadataChanged = {}, playlists = emptyList(),
                onCreatePlaylist = {}, onAddToPlaylist = { _, _ -> })
        } } }
        repeat(40) { compose.mainClock.advanceTimeBy(100) }
    }
}
