package com.zirakusa.stone.nativeapp.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.zirakusa.stone.nativeapp.ui.theme.LauncherIcon
import com.zirakusa.stone.core.Song
import com.zirakusa.stone.nativeapp.R
import com.zirakusa.stone.nativeapp.automation.AutomationHost
import com.zirakusa.stone.nativeapp.automation.StoneAutomation
import com.zirakusa.stone.nativeapp.feature.library.LibraryState
import com.zirakusa.stone.nativeapp.feature.library.LibraryViewModel
import com.zirakusa.stone.nativeapp.feature.library.LibraryViewState
import com.zirakusa.stone.nativeapp.feature.library.components.LibrarySort
import com.zirakusa.stone.nativeapp.playback.AudioMode
import com.zirakusa.stone.nativeapp.playback.HardwareAcceleration
import com.zirakusa.stone.nativeapp.playback.PlaybackState
import com.zirakusa.stone.nativeapp.playback.PlaybackViewModel

/**
 * Registers the running UI as the automation host (so agents use the same view models as
 * the buttons) and shows the confirmation dialog for actions that modify files.
 */
@Composable internal fun AutomationBridge(playback: PlaybackViewModel, library: LibraryViewModel,
    libraryState: LibraryState, playbackState: PlaybackState, theme: String, language: String,
    onTheme: (String) -> Unit, onLanguage: (String) -> Unit,
    videoScale: String = "auto", onVideoScale: (String) -> Unit = {}) {
    val activity = LocalContext.current.findActivity()
    val context = LocalContext.current.applicationContext
    val currentVideoScale by rememberUpdatedState(videoScale)
    val setVideoScale by rememberUpdatedState(onVideoScale)
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
            override fun setSleepTimer(minutes: Int, finishTrack: Boolean, fadeOut: Boolean) {
                playback.setSleepFadeOut(fadeOut); playback.setSleepTimer(minutes, finishTrack)
            }
            override fun extendSleepTimer(minutes: Int) = playback.extendSleepTimer(minutes)
            override fun setLauncherIcon(key: String) = LauncherIcon.apply(context, LauncherIcon.from(key))
            override val favoriteIds get() = currentLibrary.favoriteIds
            override val librarySort get() = LibraryViewState.sort.name.lowercase() to LibraryViewState.descending
            override val videoScale get() = currentVideoScale
            override fun setShuffle(enabled: Boolean) = playback.setShuffle(enabled)
            override fun setRepeat(mode: Int) = playback.setRepeat(mode)
            override fun playQueueIndex(index: Int) = playback.playAt(index)
            override fun moveQueue(from: Int, to: Int) = playback.move(from, to)
            override fun removeFromQueue(index: Int) = playback.removeAt(index)
            override fun clearQueue() = playback.clearQueue()
            override fun toggleFavorite() { playback.toggleFavorite(); library.syncFavorites() }
            override fun setEqualizerEnabled(enabled: Boolean) = playback.setEqualizerEnabled(enabled)
            override fun setEqualizerBand(index: Int, value: Int) = playback.setEqualizerBand(index, value)
            override fun setEqualizerPreset(name: String, values: List<Int>) = playback.setEqualizerPreset(name, values)
            override fun setEqualizerByGenre(enabled: Boolean) = playback.setEqualizerByGenre(enabled)
            override fun removeFromPlaylist(playlistId: String, song: Song) = library.removeFromPlaylist(playlistId, song)
            override fun sortLibrary(key: String, descending: Boolean) {
                LibrarySort.entries.firstOrNull { it.name.equals(key, ignoreCase = true) }?.let { LibraryViewState.sort = it }
                LibraryViewState.descending = descending
            }
            override fun setVideoScale(key: String) = setVideoScale(key)
            override fun setScreenBrightness(percent: Int?): Boolean {
                val window = activity?.window ?: return false
                window.attributes = window.attributes.apply {
                    screenBrightness = percent?.let { (it / 100f).coerceIn(.02f, 1f) }
                        ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                }
                return true
            }
            override fun setFavorite(songId: String, favorite: Boolean) {
                playback.setFavorite(songId, favorite); library.syncFavorites()
            }
            override fun renamePlaylist(playlistId: String, name: String) = library.renamePlaylist(playlistId, name)
            override fun deletePlaylist(playlistId: String) = library.deletePlaylist(playlistId)
            override fun setHardwareAcceleration(key: String) {
                HardwareAcceleration.entries.firstOrNull { it.key == key }?.let(playback::setHardwareAcceleration)
            }
            override fun setPlayWithOthers(enabled: Boolean) = playback.setPlayWithOthers(enabled)
            override fun setOrientation(key: String): Boolean {
                val target = activity ?: return false
                target.requestedOrientation = when (key) {
                    "portrait" -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                    "landscape" -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }
                return true
            }
        }
        StoneAutomation.host = host
        onDispose { if (StoneAutomation.host === host) StoneAutomation.host = null }
    }
    // Android's own dialog for MediaStore files, asked once per batch of agent edits.
    val writeRequest by StoneAutomation.writeRequest.collectAsState()
    val currentWrite by rememberUpdatedState(writeRequest)
    val writeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        currentWrite?.let { StoneAutomation.answerWriteAccess(it, result.resultCode == Activity.RESULT_OK) }
    }
    LaunchedEffect(writeRequest) {
        writeRequest?.let { request ->
            runCatching { writeLauncher.launch(IntentSenderRequest.Builder(request.sender).build()) }
                .onFailure { StoneAutomation.answerWriteAccess(request, false) }
        }
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

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
