package com.zirakusa.stone.nativeapp.ui

import androidx.compose.ui.graphics.Color
import com.zirakusa.stone.nativeapp.ui.theme.stoneBackdrop
import android.app.Activity
import android.content.res.Configuration
import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.ui.Alignment
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zirakusa.stone.nativeapp.MainActivity
import com.zirakusa.stone.nativeapp.R
import com.zirakusa.stone.core.LandscapeLayoutMode
import com.zirakusa.stone.core.homeLayout
import com.zirakusa.stone.nativeapp.feature.library.*
import com.zirakusa.stone.nativeapp.feature.library.components.VideoLibraryLayout
import com.zirakusa.stone.nativeapp.feature.library.components.FeedbackKind
import com.zirakusa.stone.nativeapp.feature.library.components.FeedbackReportSheet
import com.zirakusa.stone.nativeapp.feature.player.*
import com.zirakusa.stone.nativeapp.playback.PlaybackViewModel
import com.zirakusa.stone.nativeapp.playback.StonePlaybackService
import com.zirakusa.stone.nativeapp.data.persistence.StoneSettingsStore
import com.zirakusa.stone.nativeapp.data.library.ArtworkLoader
import com.zirakusa.stone.nativeapp.ui.theme.StoneTheme
import kotlinx.coroutines.delay

@Composable fun StoneApp(library: LibraryViewModel = viewModel(), playback: PlaybackViewModel = viewModel(),
    isPictureInPicture: Boolean = false,
    windowOrientation: Int = Configuration.ORIENTATION_UNDEFINED) {
    val context = LocalContext.current
    val systemBarsController = (context as? MainActivity)?.immersiveSystemBars
    val settingsStore = remember(context) { StoneSettingsStore(context.applicationContext) }
    LaunchedEffect(context) { ArtworkLoader.configure(context.applicationContext) }
    val initialSettings = remember(settingsStore) { settingsStore.read() }
    var theme by rememberSaveable { mutableStateOf(initialSettings.theme) }
    var language by rememberSaveable { mutableStateOf(initialSettings.language) }
    var settingsPage by rememberSaveable { mutableStateOf<String?>(null) }
    var settingsVisible by rememberSaveable { mutableStateOf(false) }
    var landscapeMode by rememberSaveable { mutableStateOf(com.zirakusa.stone.core.LandscapeLayoutMode.ALWAYS) }
    var videoScale by rememberSaveable { mutableStateOf(VideoScale.fromKey(initialSettings.videoScale)) }
    var videoLibraryLayout by rememberSaveable {
        mutableStateOf(VideoLibraryLayout.fromKey(initialSettings.videoLibraryLayout))
    }
    var immersiveEnabled by rememberSaveable { mutableStateOf(initialSettings.immersiveEnabled) }
    var shakeToReport by rememberSaveable { mutableStateOf(initialSettings.shakeToReport) }
    var shakeReportVisible by rememberSaveable { mutableStateOf(false) }
    var reportScreenshot by rememberSaveable { mutableStateOf<String?>(null) }
    var videoControlRevision by remember { mutableIntStateOf(0) }
    val videoControls = remember(settingsStore, videoControlRevision) {
        VideoControlSettings.from(settingsStore)
    }
    val configurationLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val libraryScreens = rememberSaveableStateHolder()
    val playerSheets = rememberPlayerSheetsState()
    val state by library.state.collectAsStateWithLifecycle()
    val player by playback.state.collectAsStateWithLifecycle()
    val volumeUi by StonePlaybackService.volumeUi.collectAsStateWithLifecycle()
    var playerOpen by rememberSaveable { mutableStateOf(false) }
    var audioOnlyId by rememberSaveable { mutableStateOf<String?>(null) }
    var lastVideoPreviewId by rememberSaveable { mutableStateOf(settingsStore.lastVideoPreviewId()) }
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
        if (song.isVideo) {
            lastVideoPreviewId = song.id
            settingsStore.setLastVideoPreviewId(song.id)
        }
        playback.play(song, songs)
    }
    SideEffect { com.zirakusa.stone.nativeapp.feature.player.AudioOnlySelection.id = audioOnlyId }
    val currentSong = state.songs.find { it.id == player.currentId }?.let { song ->
        if (song.id == audioOnlyId && song.isVideo) song.copy(isVideo = false) else song
    }
    val videoPreviewSong = currentSong?.takeIf { it.isVideo }
        ?: state.songs.find { it.id == lastVideoPreviewId && it.isVideo }
    val videoSongs = remember(state.songs) { state.songs.filter { it.isVideo } }
    val closeMiniOptions = {
        playerSheets.optionsVisible = false
        playerSheets.optionsSidePanel = false
        playerSheets.optionsSong = null
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) {
        it?.let(library::selectFolder)
    }
    // First launch: ask once for media access so the whole device is indexed like any
    // music player. Folders chosen with the picker keep working without it.
    val mediaPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()) {
        com.zirakusa.stone.nativeapp.data.library.DeviceMediaAccess.rescanRequests.tryEmit(Unit)
    }
    LaunchedEffect(Unit) {
        if (settingsStore.read().deviceScan && !settingsStore.mediaPermissionAsked() &&
            !com.zirakusa.stone.nativeapp.data.library.DeviceMediaAccess.audioGranted(context)) {
            settingsStore.setMediaPermissionAsked()
            mediaPermission.launch(com.zirakusa.stone.nativeapp.data.library.DeviceMediaAccess.permissions())
        }
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
    DisposableEffect(owner, shakeToReport) {
        val sensorManager = context.getSystemService(SensorManager::class.java)
        val sensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val detector = if (shakeToReport && sensor != null) ShakeDetector(onShake = {
            // Capture first, then open the sheet, so the screenshot shows the problem.
            if (!shakeReportVisible) com.zirakusa.stone.nativeapp.feature.library.components.ReportScreenshot
                .capture(context) { uri -> reportScreenshot = uri?.toString(); shakeReportVisible = true }
        }) else null
        fun register() {
            if (detector != null && sensor != null) {
                sensorManager.registerListener(detector, sensor, SensorManager.SENSOR_DELAY_UI)
            }
        }
        fun unregister() {
            if (detector != null) sensorManager?.unregisterListener(detector)
        }
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> register()
                Lifecycle.Event.ON_PAUSE -> unregister()
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(lifecycleObserver)
        if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) register()
        onDispose {
            owner.lifecycle.removeObserver(lifecycleObserver)
            unregister()
        }
    }
    StoneTheme(theme) {
    CompositionLocalProvider(LocalImmersiveSystemBarsController provides systemBarsController) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
    // BoxWithConstraints is the source of truth for split-screen/freeform
    // windows. The explicit Activity value covers devices that handle rotation
    // through configChanges and do not refresh LocalConfiguration immediately.
    val landscape = maxWidth > maxHeight || windowOrientation == Configuration.ORIENTATION_LANDSCAPE ||
        (windowOrientation == Configuration.ORIENTATION_UNDEFINED && configurationLandscape)
    val layout = homeLayout(maxWidth.value, landscape, playerOpen, currentSong?.isVideo == true,
        isPictureInPicture, landscapeMode)
    // One global setting controls the Activity, library, players, settings and
    // every registered Compose window. It must not depend on the current tab
    // or player layout.
    val immersiveRequest = immersiveEnabled && !isPictureInPicture
    LaunchedEffect(systemBarsController, immersiveRequest) {
        systemBarsController?.setImmersiveEnabled(immersiveRequest)
    }
    val systemBarsVisible = systemBarsController?.systemBarsVisible == true
    val imeVisible = systemBarsController?.imeVisible == true
    val useSystemInsets = !immersiveRequest || systemBarsVisible || imeVisible
    val appContentModifier = Modifier.fillMaxSize().then(
        if (useSystemInsets) Modifier.windowInsetsPadding(
            WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
        else Modifier
    )
    Box(Modifier.fillMaxSize().stoneBackdrop().then(appContentModifier)) {
    var miniPlayerHeightPx by remember { mutableIntStateOf(0) }
    val miniPlayerHeight = with(LocalDensity.current) { miniPlayerHeightPx.toDp() }
    LaunchedEffect(landscape, layout.fullPlayer, playerSheets.optionsSidePanel) {
        if (layout.fullPlayer || (!landscape && playerSheets.optionsSidePanel)) closeMiniOptions()
    }
    Scaffold(containerColor = Color.Transparent, contentWindowInsets = if (useSystemInsets)
        WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical)
        else WindowInsets(0, 0, 0, 0)) { padding ->
        Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(padding)
            .padding(bottom = if (layout.showMiniPlayer) miniPlayerHeight else 0.dp)) {
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
                videoControls = videoControls,
                onVideoScaleChange = {
                    videoScale = it
                    settingsStore.setVideoScale(it.key)
                })
            else libraryScreens.SaveableStateProvider("library") {
            LibraryScreen(state, player.ready, { picker.launch(null) },
                playSong, Modifier.weight(1f), player.currentId, library::createPlaylist,
                library::removeFromPlaylist, library::restoreDeleted,
                onDeletePermanently = library::deletePermanently,
                onMarkPurged = library::markPurged,
                selectionActions = remember(library, playback) {
                    com.zirakusa.stone.nativeapp.feature.library.components.LibrarySelectionActions(
                        library::addToPlaylist, playback::playNext, { library.refresh() })
                },
                onAudioOnly = { song ->
                    audioOnlyId = song.id
                    playback.play(song.copy(isVideo = false), state.songs)
                    playerOpen = true
                },
                onVideoOptions = { selected ->
                    playerSheets.optionsSong = selected
                    playerSheets.optionsSidePanel = landscape && !layout.fullPlayer
                    playerSheets.optionsVisible = true
                },
                onSetPlaylistCover = library::setPlaylistCoverFromUri,
                onRestorePlaylistCover = library::restorePlaylistCover,
                shakeToReport = shakeToReport,
                onShakeToReportChange = {
                    shakeToReport = it
                    settingsStore.setShakeToReport(it)
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
                onVideoControlChange = { videoControlRevision++ },
                videoLibraryLayout = videoLibraryLayout,
                onVideoLibraryLayoutChange = {
                    videoLibraryLayout = it
                    settingsStore.setVideoLibraryLayout(it.key)
                },
                immersiveEnabled = immersiveEnabled,
                onImmersiveEnabledChange = {
                    immersiveEnabled = it
                    settingsStore.setImmersiveEnabled(it)
                },
                onHome = { playerOpen = false },
                onEqualizer = { playerSheets.tool = PlayerTool.Equalizer },
                playerContent = { showPanel, videoTab ->
                    LibraryPlaybackPane(showPanel, player, currentSong, playback, playerSheets,
                        onOpen = if (landscapeMode == LandscapeLayoutMode.LIBRARY_ONLY ||
                            videoTab || currentSong?.isVideo == true)
                            ({ playerOpen = true }) else null,
                        state.playlists, library::createPlaylist, library::addToPlaylist, library::delete,
                        onMetadataChanged = { library.refresh() },
                        optionsAsSidePanel = landscape && !layout.fullPlayer,
                        videoTab = videoTab,
                        videoPreviewSong = videoPreviewSong,
                        videoControls = videoControls,
                        onSelectVideo = { selected -> playSong(selected, videoSongs) },
                        onAudioOnly = { song ->
                            audioOnlyId = song.id
                            playback.play(song.copy(isVideo = false), state.songs)
                            playerOpen = true
                        },
                        videoScale = videoScale,
                        onVideoScaleChange = {
                            videoScale = it
                            settingsStore.setVideoScale(it.key)
                        })
                })
            }
        }
        if (layout.showMiniPlayer) {
            val miniContainerModifier = Modifier.fillMaxSize().then(
                if (immersiveRequest) Modifier else Modifier.navigationBarsPadding()
            )
            Box(miniContainerModifier) {
                MiniPlayer(
                    state = player,
                    open = { playerOpen = true },
                    toggle = playback::toggle,
                    song = currentSong,
                    previous = playback::previous,
                    next = playback::next,
                    showOptions = true,
                    onOptions = {
                        playerSheets.optionsSong = null
                        playerSheets.optionsSidePanel = landscape && !layout.fullPlayer
                        playerSheets.optionsVisible = true
                    },
                    modifier = Modifier.align(Alignment.BottomCenter).onSizeChanged {
                        miniPlayerHeightPx = it.height
                    }
                )
            }
        }
        if (shakeReportVisible) FeedbackReportSheet(
            initialKind = FeedbackKind.BUG,
            screenLabel = if (layout.fullPlayer) "Reproductor" else "Biblioteca",
            initialAttachments = listOfNotNull(reportScreenshot?.let(android.net.Uri::parse)),
            close = { shakeReportVisible = false; reportScreenshot = null }
        )
        }
    }
    if (landscape && !layout.fullPlayer && playerSheets.optionsVisible &&
        playerSheets.optionsSidePanel) {
        val optionsTarget = playerSheets.optionsSong ?: currentSong
        MiniPlayerOptionsPanel(
            state = player,
            song = optionsTarget,
            onClose = closeMiniOptions,
            onSelect = { selected ->
                val target = playerSheets.optionsSong ?: currentSong
                closeMiniOptions()
                selectPlayerTool(context, playerSheets, selected, target, playback)
            },
            extraActions = buildList {
                if (optionsTarget?.isVideo == true) add(PlayerOptionExtra(Icons.Default.Headphones, R.string.audio_only) {
                    val target = playerSheets.optionsSong ?: currentSong
                    closeMiniOptions()
                    target?.let { video ->
                        audioOnlyId = video.id
                        playback.play(video.copy(isVideo = false), state.songs)
                        playerOpen = true
                    }
                })
                add(PlayerOptionExtra(Icons.Default.QueueMusic, R.string.queue) {
                    closeMiniOptions()
                    playerSheets.queueVisible = true
                })
                add(PlayerOptionExtra(Icons.Default.Tune, R.string.settings_playback) {
                    closeMiniOptions()
                    settingsPage = "playback"
                    settingsVisible = true
                })
                add(PlayerOptionExtra(Icons.Default.Settings, R.string.settings) {
                    closeMiniOptions()
                    settingsPage = null
                    settingsVisible = true
                })
            },
            immersive = immersiveRequest
        )
    }
        if (volumeUi.visible) {
            LaunchedEffect(volumeUi.revision) {
                delay(1800)
                StonePlaybackService.hideVolumeOverlay(volumeUi.revision)
            }
            VolumeOverlay(volumeUi, Modifier.align(Alignment.TopCenter).padding(top = 38.dp))
        }
    } } } }
}
