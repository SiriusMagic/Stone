package com.zirakusa.stone.nativeapp.feature.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zirakusa.stone.core.Song
import com.zirakusa.stone.nativeapp.R
import com.zirakusa.stone.nativeapp.feature.library.components.*
import com.zirakusa.stone.nativeapp.data.persistence.StoneSettingsStore
import com.zirakusa.stone.nativeapp.playback.PlaybackViewModel
import com.zirakusa.stone.nativeapp.feature.player.EqualizerSheet
import com.zirakusa.stone.nativeapp.feature.player.SleepSheet
import com.zirakusa.stone.nativeapp.data.transfer.LocalTransferUiState
import java.text.Collator

private val homeShortcutKeys = listOf("search", "folder", "scan", "downloads", "sort", "equalizer", "sleep")

@Composable fun LibraryScreen(state: LibraryState, canPlay: Boolean, onFolder: () -> Unit,
    onRefresh: () -> Unit, onPlay: (Song, List<Song>) -> Unit, modifier: Modifier = Modifier,
    currentId: String = "", onCreatePlaylist: (String) -> Unit = {},
    onRemoveFromPlaylist: (String, Song) -> Unit = { _, _ -> },
    onRestoreHidden: (Song) -> Unit = {}, onRestoreDeleted: (Song) -> Unit = {},
    onExport: () -> Unit = {}, onImport: () -> Unit = {}, onShare: () -> Unit = {},
    onExportPackage: () -> Unit = {}, onImportPackage: () -> Unit = {}, onTransfer: () -> Unit = {},
    transferState: LocalTransferUiState = LocalTransferUiState(),
    onHostTransfer: () -> Unit = {}, onReceiveTransfer: (String, String) -> Unit = { _, _ -> },
    onStopTransfer: () -> Unit = {},
    theme: String = "dark", onThemeChange: (String) -> Unit = {}, language: String = "es",
    onLanguageChange: (String) -> Unit = {}, settingsStore: StoneSettingsStore? = null,
    settingsPage: String? = null, onSettingsPage: (String?) -> Unit = {},
    playbackViewModel: PlaybackViewModel? = null, onOpenSettings: () -> Unit = {},
    settingsSheetVisible: Boolean = false, onSettingsSheetVisible: (Boolean) -> Unit = {},
    artistDestination: String? = null, onArtistDestinationConsumed: () -> Unit = {}) {
    var query by rememberSaveable { mutableStateOf("") }
    var searchVisible by rememberSaveable { mutableStateOf(false) }
    var tab by rememberSaveable { mutableStateOf(LibraryTab.Songs) }
    var collection by rememberSaveable { mutableStateOf<String?>(null) }
    var sheet by rememberSaveable { mutableStateOf<String?>(null) }
    var sort by rememberSaveable { mutableStateOf(LibrarySort.Title) }
    var descending by rememberSaveable { mutableStateOf(false) }
    var shortcuts by remember(settingsStore) { mutableStateOf(homeShortcutKeys.associateWith {
        settingsStore?.shortcutVisible(it) ?: true
    }) }
    var homeTool by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(artistDestination) {
        artistDestination?.takeIf { it.isNotBlank() }?.let { artist ->
            tab = LibraryTab.Artists
            collection = artist
            onArtistDestinationConsumed()
        }
    }
    val playbackState = playbackViewModel?.state?.collectAsState()?.value
    val scan = { if (state.hasFolder) onRefresh() else onFolder() }
    val songs = remember(state.songs, query, sort, descending) {
        val collator = Collator.getInstance()
        val filtered = state.songs.filter { it.matches(query) }
        val ordered = if (sort == LibrarySort.Duration) filtered.sortedBy { it.durationMs }
        else filtered.sortedWith { a, b ->
            fun key(song: Song) = when (sort) {
                LibrarySort.Artist -> song.artist
                LibrarySort.Album -> song.album
                else -> song.title
            }
            collator.compare(key(a), key(b))
        }
        if (descending) ordered.reversed() else ordered
    }
    BackHandler(enabled = collection != null || searchVisible) {
        if (collection != null) collection = null else { searchVisible = false; query = "" }
    }
    Column(modifier) {
        LibraryHeader(state.scanning, { searchVisible = !searchVisible; if (!searchVisible) query = "" },
            onFolder, scan, { sheet = "downloads" }, { sheet = "sort" }, {
                onOpenSettings(); onSettingsSheetVisible(true)
            }, onEqualizer = { homeTool = "equalizer" }, onSleep = { homeTool = "sleep" },
            showSearch = shortcuts["search"] != false, showFolder = shortcuts["folder"] != false,
            showScan = shortcuts["scan"] != false, showDownloads = shortcuts["downloads"] != false,
            showSort = shortcuts["sort"] != false, showEqualizer = shortcuts["equalizer"] != false,
            showSleep = shortcuts["sleep"] != false)
        if (searchVisible) LibrarySearch(query) { query = it }
        LibraryTabs(tab) { tab = it; collection = null }
        if (state.scanning) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (state.failed) Text(stringResource(R.string.scan_failed), Modifier.padding(horizontal = 16.dp),
            color = MaterialTheme.colorScheme.error)
        LibraryContent(state, songs, tab, collection, { collection = it }, query.isNotEmpty(),
            canPlay, currentId, onPlay, onFolder, scan, Modifier.weight(1f), onCreatePlaylist,
            onRemoveFromPlaylist)
    }
    LibrarySheets(sheet,
        sort, descending, { sort = it; sheet = null }, { descending = !descending },
        onFolder, scan, { sheet = null }, state.hiddenSongs, state.deletedSongs,
        { sheet = "hidden" }, { sheet = "deleted" }, onRestoreHidden, onRestoreDeleted,
        onExport, onImport, onOpenSettings, theme, onThemeChange, language, onLanguageChange,
        null, onSettingsPage)
    if (settingsSheetVisible) SettingsPanel(theme, onThemeChange, language, onLanguageChange,
        settingsStore, settingsPage, onSettingsPage, { onSettingsSheetVisible(false) }, onFolder,
        scan, { sheet = it }, playbackViewModel, onExport, onImport, onShare,
        onExportPackage, onImportPackage, onTransfer, transferState, onHostTransfer,
        onReceiveTransfer, onStopTransfer) { key, visible ->
        shortcuts = shortcuts + (key to visible)
    }
    if (homeTool == "equalizer" && playbackViewModel != null && playbackState != null)
        EqualizerSheet(playbackState, playbackViewModel,
            song = state.songs.find { it.id == playbackState.currentId }) { homeTool = null }
    if (homeTool == "sleep" && playbackViewModel != null && playbackState != null)
        SleepSheet(playbackState, playbackViewModel) { homeTool = null }
}
