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

    @Test fun portraitPlayerAndMiniPlayerWhilePlaying() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        // Robolectric cannot bind a MediaSessionService by itself: hand it the real service's binder.
        val service = org.robolectric.Robolectric.buildService(com.zirakusa.stone.nativeapp.playback.StonePlaybackService::class.java).create().get()
        val component = android.content.ComponentName(app, com.zirakusa.stone.nativeapp.playback.StonePlaybackService::class.java)
        val binder = service.onBind(android.content.Intent(androidx.media3.session.MediaSessionService.SERVICE_INTERFACE).setComponent(component))
        org.robolectric.Shadows.shadowOf(app).setComponentNameAndServiceForBindService(component, binder)
        val playback = PlaybackViewModel(app)
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
}
