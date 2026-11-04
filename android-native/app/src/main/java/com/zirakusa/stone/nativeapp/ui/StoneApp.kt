package com.zirakusa.stone.nativeapp.ui

import android.app.Activity
import android.content.Intent
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
import androidx.core.content.FileProvider
import com.zirakusa.stone.nativeapp.R
import com.zirakusa.stone.nativeapp.feature.library.*
import com.zirakusa.stone.nativeapp.feature.player.*
import com.zirakusa.stone.nativeapp.playback.PlaybackViewModel
import com.zirakusa.stone.nativeapp.playback.StonePlaybackService
import com.zirakusa.stone.nativeapp.data.persistence.StoneBackup
import com.zirakusa.stone.nativeapp.data.persistence.StoneSettingsStore
import com.zirakusa.stone.nativeapp.data.transfer.StonePackage
import com.zirakusa.stone.nativeapp.data.transfer.LocalTransferManager
import com.zirakusa.stone.nativeapp.data.transfer.LocalTransferUiState
import java.io.File
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
    val transferManager = remember(context) { LocalTransferManager(context.applicationContext) }
    var transferState by remember { mutableStateOf(LocalTransferUiState()) }
    DisposableEffect(transferManager) { onDispose { transferManager.stop() } }
    var playerOpen by rememberSaveable { mutableStateOf(false) }
    var artistDestination by rememberSaveable { mutableStateOf<String?>(null) }
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
    val packageExportPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(StonePackage.MIME)) { uri ->
        uri?.let { target -> scope.launch {
            val songs = (state.songs + state.hiddenSongs + state.deletedSongs).distinctBy { it.id }
            val success = withContext(Dispatchers.IO) { runCatching {
                StonePackage.export(context, target, songs)
            }.isSuccess }
            android.widget.Toast.makeText(context,
                if (success) R.string.musp_exported else R.string.musp_failed,
                android.widget.Toast.LENGTH_LONG).show()
        } }
    }
    val packageImportPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { source -> scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { StonePackage.import(context, source) } }
            library.reloadUserData()
            android.widget.Toast.makeText(context,
                result.getOrNull()?.message ?: context.getString(R.string.musp_failed),
                android.widget.Toast.LENGTH_LONG).show()
        } }
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
                state.playlists, library::createPlaylist, library::addToPlaylist, Modifier.weight(1f),
                onArtist = {
                    artistDestination = player.artist
                    playerOpen = false
                })
            else LibraryScreen(state, player.ready, { picker.launch(null) }, { library.refresh() },
                playSong, Modifier.weight(1f), player.currentId, library::createPlaylist,
                library::removeFromPlaylist, library::restoreHidden, library::restoreDeleted,
                { exportPicker.launch("stone-backup-${System.currentTimeMillis()}.json") },
                { importPicker.launch(arrayOf("application/json", "text/plain")) },
                { shareStoneLibrary(context, state.songs + state.hiddenSongs + state.deletedSongs, scope) },
                { packageExportPicker.launch("stone-library-${System.currentTimeMillis()}.musp") },
                { packageImportPicker.launch(arrayOf(StonePackage.MIME, "application/zip", "application/octet-stream")) },
                { settingsPage = "wifi-transfer" },
                onHide = library::hide,
                onAudioOnly = { song ->
                    library.audioOnly(song)
                    val audioQueue = state.songs.map { item ->
                        if (item.id == song.id) item.copy(isVideo = false) else item
                    }
                    playback.play(song.copy(isVideo = false), audioQueue)
                    playerOpen = true
                },
                transferState = transferState,
                onHostTransfer = {
                    scope.launch {
                        transferState = LocalTransferUiState(busy = true, message = "Preparando paquete…")
                        val packageFile = File(context.cacheDir, "stone-transfer.musp")
                        val songs = (state.songs + state.hiddenSongs + state.deletedSongs).distinctBy { it.id }
                        val result = withContext(Dispatchers.IO) { runCatching {
                            StonePackage.exportToFile(context, packageFile, songs)
                            transferManager.startHost(packageFile)
                        } }
                        result.onSuccess { host ->
                            transferState = LocalTransferUiState(address = host.address, code = host.code,
                                message = "Paquete listo: ${packageFile.length() / 1024 / 1024} MB")
                        }.onFailure { error ->
                            transferState = LocalTransferUiState(message = error.message ?: "No se pudo preparar la transferencia")
                        }
                    }
                },
                onReceiveTransfer = { address, code ->
                    scope.launch {
                        transferState = LocalTransferUiState(busy = true, message = "Descargando paquete…")
                        val packageFile = File(context.cacheDir, "stone-received.musp")
                        val result = withContext(Dispatchers.IO) { runCatching {
                            transferManager.download(address, code, packageFile) { done, total ->
                                if (total > 0) scope.launch {
                                    transferState = transferState.copy(progress = done.toFloat() / total)
                                }
                            }
                            StonePackage.importFile(context, packageFile)
                        } }
                        packageFile.delete()
                        library.reloadUserData()
                        transferState = LocalTransferUiState(message = result.getOrNull()?.message
                            ?: result.exceptionOrNull()?.message ?: "No se pudo recibir el paquete")
                    }
                },
                onStopTransfer = {
                    transferManager.stop()
                    transferState = LocalTransferUiState(message = "Transferencia cancelada")
                },
                onOpenSettings = { settingsPage = null }, theme = theme,
                onThemeChange = { theme = it; settingsStore.setTheme(it); (context as? Activity)?.recreate() }, language = language,
                onLanguageChange = { language = it; settingsStore.setLanguage(it); (context as? Activity)?.recreate() },
                settingsStore = settingsStore, settingsPage = settingsPage,
                onSettingsPage = { settingsPage = it }, playbackViewModel = playback,
                settingsSheetVisible = settingsVisible, onSettingsSheetVisible = {
                    settingsVisible = it
                    if (!it) settingsPage = null
                }, artistDestination = artistDestination,
                onArtistDestinationConsumed = { artistDestination = null },
                onDelete = library::delete)
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

private fun shareStoneLibrary(context: android.content.Context,
    songs: List<com.zirakusa.stone.core.Song>, scope: kotlinx.coroutines.CoroutineScope) {
    scope.launch {
        val packageFile = File(context.cacheDir, "stone-bluetooth.musp")
        val result = withContext(Dispatchers.IO) { runCatching {
            StonePackage.exportToFile(context, packageFile, songs.distinctBy { it.id })
        } }
        result.onSuccess {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", packageFile)
            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = StonePackage.MIME
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }, context.getString(R.string.bluetooth_transfer)))
        }.onFailure {
            android.widget.Toast.makeText(context, it.message ?: context.getString(R.string.musp_failed),
                android.widget.Toast.LENGTH_LONG).show()
        }
    }
}
