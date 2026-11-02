package com.zirakusa.stone.nativeapp.ui

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zirakusa.stone.nativeapp.R
import com.zirakusa.stone.nativeapp.feature.library.*
import com.zirakusa.stone.nativeapp.feature.player.*
import com.zirakusa.stone.nativeapp.playback.PlaybackViewModel
import com.zirakusa.stone.nativeapp.playback.StonePlaybackService
import com.zirakusa.stone.nativeapp.data.persistence.StoneBackup
import com.zirakusa.stone.nativeapp.data.persistence.StoneSettingsStore
import com.zirakusa.stone.nativeapp.data.library.ArtworkLoader
import com.zirakusa.stone.nativeapp.ui.theme.StoneTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay

@Composable fun StoneApp(library: LibraryViewModel = viewModel(), playback: PlaybackViewModel = viewModel()) {
    val context = LocalContext.current
    val settingsStore = remember(context) { StoneSettingsStore(context.applicationContext) }
    LaunchedEffect(context) { ArtworkLoader.configure(context.applicationContext) }
    var theme by rememberSaveable { mutableStateOf(settingsStore.read().theme) }
    var language by rememberSaveable { mutableStateOf(settingsStore.read().language) }
    var settingsPage by rememberSaveable { mutableStateOf<String?>(null) }
    var settingsVisible by rememberSaveable { mutableStateOf(false) }
    var energySaver by rememberSaveable { mutableStateOf(false) }
    val state by library.state.collectAsStateWithLifecycle()
    val player by playback.state.collectAsStateWithLifecycle()
    val volumeUi by StonePlaybackService.volumeUi.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var playerOpen by rememberSaveable { mutableStateOf(false) }
    var lastRecordedId by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(player.favorite, player.currentId) { library.syncFavorites() }
    LaunchedEffect(player.currentId, state.songs) {
        if (player.currentId.isNotEmpty() && player.currentId != lastRecordedId) {
            state.songs.find { it.id == player.currentId }?.let { library.recordPlay(it); lastRecordedId = it.id }
        }
    }
    val playSong: (com.zirakusa.stone.core.Song, List<com.zirakusa.stone.core.Song>) -> Unit = { song, songs ->
        playback.play(song, songs)
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) {
        it?.let(library::selectFolder)
    }
    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { scope.launch { val success = withContext(Dispatchers.IO) { runCatching { StoneBackup.export(context, it) }.isSuccess }
            android.widget.Toast.makeText(context, if (success) R.string.backup_exported else R.string.backup_failed,
                android.widget.Toast.LENGTH_LONG).show() } }
    }
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { scope.launch { val success = withContext(Dispatchers.IO) { runCatching { StoneBackup.import(context, it) }.isSuccess }
            if (success) library.reloadUserData()
            android.widget.Toast.makeText(context, if (success) R.string.backup_imported else R.string.backup_failed,
                android.widget.Toast.LENGTH_LONG).show() } }
    }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, library) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) library.onForeground()
            if (event == Lifecycle.Event.ON_STOP) { library.onBackground(); playback.persist() }
        }
        owner.lifecycle.addObserver(observer)
        if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) library.onForeground()
        onDispose { owner.lifecycle.removeObserver(observer); library.onBackground(); playback.persist() }
    }
    StoneTheme(theme) { Box(Modifier.fillMaxSize()) { Scaffold(bottomBar = {
        if (!playerOpen && !energySaver) MiniPlayer(player, { playerOpen = true }, playback::toggle,
            state.songs.find { it.id == player.currentId })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (player.failed) Text(stringResource(if (player.ready) R.string.playback_failed
                else R.string.playback_connect_failed), color = MaterialTheme.colorScheme.error)
            if (energySaver) EnergySaverScreen(player, state.songs.find { it.id == player.currentId }, playback) { energySaver = false }
            else if (playerOpen) PlayerScreen(player, state.songs.find { it.id == player.currentId }, playback,
                { playerOpen = false }, library::hide, library::delete, { library.refresh() },
                state.playlists, library::createPlaylist, library::addToPlaylist, Modifier.weight(1f))
            else LibraryScreen(state, player.ready, { picker.launch(null) }, { library.refresh() },
                playSong, Modifier.weight(1f), player.currentId, library::createPlaylist,
                library::removeFromPlaylist, library::restoreHidden, library::restoreDeleted,
                { exportPicker.launch("stone-backup-${System.currentTimeMillis()}.json") },
                { importPicker.launch(arrayOf("application/json", "text/plain")) },
                { shareStoneBackup(context) },
                onOpenSettings = { settingsPage = null }, theme = theme,
                onThemeChange = { theme = it; settingsStore.setTheme(it); (context as? Activity)?.recreate() }, language = language,
                onLanguageChange = { language = it; settingsStore.setLanguage(it); (context as? Activity)?.recreate() },
                settingsStore = settingsStore, settingsPage = settingsPage,
                onSettingsPage = { settingsPage = it }, playbackViewModel = playback,
                settingsSheetVisible = settingsVisible, onSettingsSheetVisible = {
                    settingsVisible = it
                    if (!it) settingsPage = null
                })
        }
    }
        if (volumeUi.visible) {
            LaunchedEffect(volumeUi.revision) {
                delay(1800)
                StonePlaybackService.hideVolumeOverlay(volumeUi.revision)
            }
            VolumeOverlay(volumeUi, Modifier.align(Alignment.TopCenter).padding(top = 38.dp))
        }
    } }
}

private fun shareStoneBackup(context: android.content.Context) {
    val text = runCatching { StoneBackup.exportText(context) }.getOrNull() ?: return
    context.startActivity(android.content.Intent.createChooser(
        android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(android.content.Intent.EXTRA_SUBJECT, "Stone - respaldo de playlists")
            putExtra(android.content.Intent.EXTRA_TEXT, text)
        }, "Compartir respaldo de Stone"))
}
