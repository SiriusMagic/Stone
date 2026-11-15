package com.zirakusa.stone.nativeapp.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.res.stringResource
import com.zirakusa.stone.core.Song
import com.zirakusa.stone.nativeapp.R
import com.zirakusa.stone.nativeapp.automation.AutomationHost
import com.zirakusa.stone.nativeapp.automation.StoneAutomation
import com.zirakusa.stone.nativeapp.feature.library.LibraryState
import com.zirakusa.stone.nativeapp.feature.library.LibraryViewModel
import com.zirakusa.stone.nativeapp.playback.AudioMode
import com.zirakusa.stone.nativeapp.playback.PlaybackState
import com.zirakusa.stone.nativeapp.playback.PlaybackViewModel

/**
 * Registers the running UI as the automation host (so agents use the same view models as
 * the buttons) and shows the confirmation dialog for actions that modify files.
 */
@Composable internal fun AutomationBridge(playback: PlaybackViewModel, library: LibraryViewModel,
    libraryState: LibraryState, playbackState: PlaybackState, theme: String, language: String,
    onTheme: (String) -> Unit, onLanguage: (String) -> Unit) {
    val currentLibrary by rememberUpdatedState(libraryState)
    val currentPlayback by rememberUpdatedState(playbackState)
    val currentTheme by rememberUpdatedState(theme)
    val currentLanguage by rememberUpdatedState(language)
    val setTheme by rememberUpdatedState(onTheme)
    val setLanguage by rememberUpdatedState(onLanguage)
    DisposableEffect(playback, library) {
        val host = object : AutomationHost {
            override val playback get() = currentPlayback
            override val songs get() = currentLibrary.songs
            override val playlists get() = currentLibrary.playlists
            override val theme get() = currentTheme
            override val language get() = currentLanguage
            override fun progress() = playback.progress()
            override fun toggle() = playback.toggle()
            override fun next() = playback.next()
            override fun previous() = playback.previous()
            override fun seek(positionMs: Long) = playback.seek(positionMs)
            override fun setSpeed(speed: Float) = playback.setSpeed(speed)
            override fun setVolume(percent: Int) = playback.setVolumePercent(percent)
            override fun play(song: Song, queue: List<Song>) = playback.play(song, queue)
            override fun playNext(songs: List<Song>) = playback.playNext(songs)
            override fun setAudioMode(mode: AudioMode) = playback.setAudioMode(mode)
            override fun createPlaylist(name: String) = library.createPlaylist(name)
            override fun addToPlaylist(playlistId: String, song: Song) = library.addToPlaylist(playlistId, song)
            override fun setTheme(key: String) = setTheme(key)
            override fun setLanguage(code: String) = setLanguage(code)
            override fun refreshLibrary() = library.refresh()
        }
        StoneAutomation.host = host
        onDispose { if (StoneAutomation.host === host) StoneAutomation.host = null }
    }
    val pending by StoneAutomation.confirmation.collectAsState()
    pending?.let { request ->
        AlertDialog(onDismissRequest = { StoneAutomation.answer(request, false) },
            title = { ImmersiveWindowEffect(); Text(stringResource(R.string.api_confirm_title)) },
            text = { Text(request.summary) },
            confirmButton = { TextButton(onClick = { StoneAutomation.answer(request, true) }) {
                Text(stringResource(R.string.api_confirm_allow)) } },
            dismissButton = { TextButton(onClick = { StoneAutomation.answer(request, false) }) {
                Text(stringResource(R.string.api_confirm_deny)) } })
    }
}
