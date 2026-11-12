package com.zirakusa.stone.nativeapp.ui

import android.app.Activity
import android.content.res.Configuration
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zirakusa.stone.nativeapp.R
import com.zirakusa.stone.core.LandscapeLayoutMode
import com.zirakusa.stone.core.homeLayout
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

@Composable fun StoneApp(library: LibraryViewModel = viewModel(), playback: PlaybackViewModel = viewModel(),
    isPictureInPicture: Boolean = false,
    windowOrientation: Int = Configuration.ORIENTATION_UNDEFINED) {
    val context = LocalContext.current
    val settingsStore = remember(context) { StoneSettingsStore(context.applicationContext) }
    LaunchedEffect(context) { ArtworkLoader.configure(context.applicationContext) }
    val initialSettings = remember(settingsStore) { settingsStore.read() }
    var theme by rememberSaveable { mutableStateOf(initialSettings.theme) }
    var language by rememberSaveable { mutableStateOf(initialSettings.language) }
    var settingsPage by rememberSaveable { mutableStateOf<String?>(null) }
    var settingsVisible by rememberSaveable { mutableStateOf(false) }
    var landscapeMode by rememberSaveable { mutableStateOf(initialSettings.landscapeLayoutMode) }
    var videoScale by rememberSaveable { mutableStateOf(VideoScale.fromKey(initialSettings.videoScale)) }
    var videoUnlockMethod by rememberSaveable {
        mutableStateOf(VideoUnlockMethod.fromKey(initialSettings.videoUnlockMethod))
    }
    val configurationLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val libraryScreens = rememberSaveableStateHolder()
    val playerSheets = rememberPlayerSheetsState()
    val state by library.state.collectAsStateWithLifecycle()
    val player by playback.state.collectAsStateWithLifecycle()
    val volumeUi by StonePlaybackService.volumeUi.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val transferManager = remember(context) { LocalTransferManager(context.applicationContext) }
    var transferState by remember { mutableStateOf(LocalTransferUiState()) }
    var bluetoothTransferJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    DisposableEffect(transferManager) {
        onDispose {
            transferManager.stop()
            bluetoothTransferJob?.cancel()
        }
    }
    var playerOpen by rememberSaveable { mutableStateOf(false) }
    var audioOnlyId by rememberSaveable { mutableStateOf<String?>(null) }
    var artistDestination by rememberSaveable { mutableStateOf<String?>(null) }
    var lastRecordedId by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(player.favorite, player.currentId) { library.syncFavorites() }
    LaunchedEffect(player.currentId, state.songs) {
        if (player.currentId.isNotEmpty() && player.currentId != lastRecordedId) {
            state.songs.find { it.id == player.currentId }?.let { library.recordPlay(it); lastRecordedId = it.id }
        }
    }
    val playSong: (com.zirakusa.stone.core.Song, List<com.zirakusa.stone.core.Song>) -> Unit = { song, songs ->
        audioOnlyId = null
        playback.play(song, songs)
    }
    val currentSong = state.songs.find { it.id == player.currentId }?.let { song ->
        if (song.id == audioOnlyId && song.isVideo) song.copy(isVideo = false) else song
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
            val songs = (state.songs + state.deletedSongs).distinctBy { it.id }
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
    StoneTheme(theme) { BoxWithConstraints(Modifier.fillMaxSize()
        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))) {
    // BoxWithConstraints is the source of truth for split-screen/freeform
    // windows. The explicit Activity value covers devices that handle rotation
    // through configChanges and do not refresh LocalConfiguration immediately.
    val landscape = maxWidth > maxHeight || windowOrientation == Configuration.ORIENTATION_LANDSCAPE ||
        (windowOrientation == Configuration.ORIENTATION_UNDEFINED && configurationLandscape)
    val layout = homeLayout(maxWidth.value, landscape, playerOpen, currentSong?.isVideo == true,
        isPictureInPicture, landscapeMode)
    Scaffold(contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical), bottomBar = {
        if (layout.showMiniPlayer) MiniPlayer(player, { playerOpen = true }, playback::toggle,
            currentSong, playback::previous, playback::next)
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (player.failed) Text(stringResource(if (player.ready) R.string.playback_failed
                else R.string.playback_connect_failed), color = MaterialTheme.colorScheme.error)
            if (layout.fullPlayer) PlayerScreen(player, currentSong, playback,
                { playerOpen = false }, library::delete, { library.refresh() },
                state.playlists, library::createPlaylist, library::addToPlaylist, Modifier.weight(1f),
                onArtist = {
                    artistDestination = player.artist
                    playerOpen = false
                }, isPictureInPicture = isPictureInPicture, onAudioOnly = { song ->
                    audioOnlyId = song.id
                    playback.play(song.copy(isVideo = false), state.songs)
                    playerOpen = true
                }, sheets = playerSheets, videoScale = videoScale,
                videoUnlockMethod = videoUnlockMethod,
                onVideoScaleChange = {
                    videoScale = it
                    settingsStore.setVideoScale(it.key)
                })
            else libraryScreens.SaveableStateProvider("library") {
            LibraryScreen(state, player.ready, { picker.launch(null) }, { library.refresh() },
                playSong, Modifier.weight(1f), player.currentId, library::createPlaylist,
                library::removeFromPlaylist, library::restoreDeleted,
                { exportPicker.launch("stone-backup-${System.currentTimeMillis()}.json") },
                { importPicker.launch(arrayOf("application/json", "text/plain")) },
                {
                    bluetoothTransferJob?.cancel()
                    bluetoothTransferJob = shareStoneLibrary(
                        context, state.songs + state.deletedSongs, scope
                    ) { transferState = it }
                },
                { packageExportPicker.launch("stone-library-${System.currentTimeMillis()}.musp") },
                { packageImportPicker.launch(arrayOf(StonePackage.MIME, "application/zip", "application/octet-stream")) },
                { settingsPage = "wifi-transfer" },
                onAudioOnly = { song ->
                    audioOnlyId = song.id
                    playback.play(song.copy(isVideo = false), state.songs)
                    playerOpen = true
                },
                transferState = transferState,
                onHostTransfer = {
                    scope.launch {
                        transferState = LocalTransferUiState(busy = true,
                            message = context.getString(R.string.transfer_packaging_indeterminate))
                        val packageFile = File(context.cacheDir, "stone-transfer.musp")
                        val songs = (state.songs + state.deletedSongs).distinctBy { it.id }
                        var lastProgressAt = 0L
                        val result = withContext(Dispatchers.IO) { runCatching {
                            StonePackage.exportToFile(context, packageFile, songs) { done, total ->
                                if (total > 0L) {
                                    val now = System.currentTimeMillis()
                                    if (now - lastProgressAt >= 100L || done >= total) {
                                        lastProgressAt = now
                                        val progress = (done.toDouble() / total).toFloat().coerceIn(0f, 1f)
                                        scope.launch {
                                            if (transferState.busy) transferState = LocalTransferUiState(
                                                busy = true,
                                                progress = progress,
                                                message = context.getString(
                                                    R.string.transfer_packaging,
                                                    (progress * 100).toInt()
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                            transferManager.startHost(packageFile)
                        } }
                        result.onSuccess { host ->
                            transferState = LocalTransferUiState(address = host.address, code = host.code,
                                message = context.getString(R.string.transfer_package_ready,
                                    packageFile.length() / 1024 / 1024))
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
                    bluetoothTransferJob?.cancel()
                    bluetoothTransferJob = null
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
                onDelete = library::delete,
                layout = layout, landscapeLayoutMode = landscapeMode,
                onLandscapeLayoutChange = {
                    settingsStore.setLandscapeLayoutMode(it)
                    landscapeMode = it
                    settingsVisible = false
                    settingsPage = null
                },
                videoScale = videoScale,
                onVideoScaleChange = {
                    videoScale = it
                    settingsStore.setVideoScale(it.key)
                },
                videoUnlockMethod = videoUnlockMethod,
                onVideoUnlockMethodChange = {
                    videoUnlockMethod = it
                    settingsStore.setVideoUnlockMethod(it.key)
                },
                onHome = { playerOpen = false },
                onEqualizer = { playerSheets.tool = PlayerTool.Equalizer },
                playerContent = { showPanel ->
                    LibraryPlaybackPane(showPanel, player, currentSong, playback, playerSheets,
                        onOpen = if (landscapeMode == LandscapeLayoutMode.LIBRARY_ONLY || currentSong?.isVideo == true)
                            ({ playerOpen = true }) else null,
                        state.playlists, library::createPlaylist, library::addToPlaylist, library::delete,
                        onMetadataChanged = { library.refresh() })
                },
                onOpenCoverEditor = {
                    if (player.currentId.isNotBlank()) {
                        playerSheets.tool = PlayerTool.Cover
                        playerOpen = true
                    } else {
                        android.widget.Toast.makeText(context, R.string.cover_editor_requires_song,
                            android.widget.Toast.LENGTH_SHORT).show()
                    }
                })
            }
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
