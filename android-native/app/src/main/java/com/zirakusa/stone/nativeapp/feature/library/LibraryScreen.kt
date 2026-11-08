package com.zirakusa.stone.nativeapp.feature.library

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zirakusa.stone.core.Song
import com.zirakusa.stone.nativeapp.R
import com.zirakusa.stone.nativeapp.feature.library.components.*
import com.zirakusa.stone.nativeapp.data.persistence.StoneSettingsStore
import com.zirakusa.stone.nativeapp.playback.PlaybackViewModel
import com.zirakusa.stone.nativeapp.playback.AudioMode
import com.zirakusa.stone.nativeapp.playback.HardwareAcceleration
import com.zirakusa.stone.nativeapp.feature.player.SleepSheet
import com.zirakusa.stone.nativeapp.data.transfer.LocalTransferUiState
import java.text.Collator
import kotlinx.coroutines.delay

private val homeShortcutKeys = listOf("search", "scan", "sort", "sleep")
private val defaultVisibleHomeShortcutKeys = homeShortcutKeys.toSet() + QuickActionRegistry.defaultVisibleIds
private val allQuickActionIds = homeShortcutKeys + QuickActionRegistry.preferredIds +
    QuickActionRegistry.operational.map { it.id }.filterNot { it in homeShortcutKeys || it in QuickActionRegistry.preferredIds }
private val legacyDefinitions = listOf(
    QuickActionDefinition("search", "Buscar", "Buscar en la biblioteca", Icons.Default.Search, true),
    QuickActionDefinition("scan", "Escanear", "Actualizar biblioteca", Icons.Default.Refresh, true),
    QuickActionDefinition("sort", "Ordenar", "Ordenar biblioteca", Icons.Default.Sort, true),
    QuickActionDefinition("sleep", "Temporizador", "Temporizador para dormir", Icons.Default.Timer, true)
)

private fun Song.hasUsableLyrics(): Boolean =
    lyrics.any { it.isNotBlank() } || syncedLyrics.any { it.text.isNotBlank() }

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun LibraryScreen(state: LibraryState, canPlay: Boolean, onFolder: () -> Unit,
    onRefresh: () -> Unit, onPlay: (Song, List<Song>) -> Unit, modifier: Modifier = Modifier,
    currentId: String = "", onCreatePlaylist: (String) -> Unit = {},
    onRemoveFromPlaylist: (String, Song) -> Unit = { _, _ -> },
    onRestoreDeleted: (Song) -> Unit = {},
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
    artistDestination: String? = null, onArtistDestinationConsumed: () -> Unit = {},
    onDelete: (Song) -> Unit = {}, onAudioOnly: (Song) -> Unit = {},
    onOpenCoverEditor: () -> Unit = {}) {
    var query by rememberSaveable { mutableStateOf("") }
    var searchVisible by rememberSaveable { mutableStateOf(false) }
    var tab by rememberSaveable { mutableStateOf(LibraryTab.Songs) }
    var collection by rememberSaveable { mutableStateOf<String?>(null) }
    var sheet by rememberSaveable { mutableStateOf<String?>(null) }
    var sort by rememberSaveable { mutableStateOf(LibrarySort.Title) }
    var descending by rememberSaveable { mutableStateOf(false) }
    var moreQuickActions by rememberSaveable { mutableStateOf(false) }
    var feedbackQuickActionId by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    var shortcuts by remember(settingsStore) { mutableStateOf(allQuickActionIds.associateWith {
        val profile = settingsStore?.shortcutProfile() ?: "custom"
        if (it in QuickActionRegistry.pinnedIds && profile != "custom") true
        else settingsStore?.shortcutVisible(it, it in defaultVisibleHomeShortcutKeys)
            ?: (it in homeShortcutKeys)
    }) }
    val quickActions = remember(shortcuts, settingsStore) {
        if (settingsStore == null) return@remember emptyList()
        val definitions = legacyDefinitions + QuickActionRegistry.operational
        val ordered = settingsStore.shortcutOrder(allQuickActionIds)
        val profile = settingsStore.shortcutProfile()
        val profileOrdered = if (profile != "custom") {
            QuickActionRegistry.pinnedIds + ordered.filterNot { it in QuickActionRegistry.pinnedIds }
        } else ordered
        profileOrdered.mapNotNull { id -> definitions.find { it.id == id }?.takeIf { shortcuts[id] == true } }
    }
    var homeTool by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(artistDestination) {
        artistDestination?.takeIf { it.isNotBlank() }?.let { artist ->
            tab = LibraryTab.Artists
            collection = artist
            onArtistDestinationConsumed()
        }
    }
    val playbackState = playbackViewModel?.state?.collectAsState()?.value
    val activeQuickActionIds = setOfNotNull(
        when (playbackState?.audioMode) {
            AudioMode.BATTERY -> "audio_mode_battery"
            AudioMode.BALANCED -> "audio_mode_balanced"
            AudioMode.HIFI -> "audio_mode_hifi"
            null -> null
        }
    )
    LaunchedEffect(feedbackQuickActionId) {
        val id = feedbackQuickActionId ?: return@LaunchedEffect
        delay(900)
        if (feedbackQuickActionId == id) feedbackQuickActionId = null
    }
    val highlightedQuickActionIds = if (feedbackQuickActionId == null) activeQuickActionIds
    else activeQuickActionIds + feedbackQuickActionId!!
    fun showQuickActionConfirmation(id: String) {
        val label = (legacyDefinitions + QuickActionRegistry.definitions)
            .firstOrNull { it.id == id }?.label ?: id
        val modeAction = id.startsWith("audio_mode_") || id.startsWith("hardware_accel_")
        val message = if (modeAction) {
            context.getString(R.string.quick_action_activated, label)
        } else {
            context.getString(R.string.quick_action_confirmed, label)
        }
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
    val scan = { if (state.hasFolder) onRefresh() else onFolder() }
    val dispatchQuickAction: (String) -> Unit = { id ->
        // Every shortcut gets immediate visual feedback, including one-shot actions such as
        // scanning or opening a collection. Stateful modes remain highlighted by their real state.
        feedbackQuickActionId = id
        showQuickActionConfirmation(id)
        when (id) {
            "search" -> { searchVisible = true }
            "folder" -> onFolder()
            "scan" -> scan()
            "sort" -> sheet = "sort"
            "sleep" -> homeTool = "sleep"
            "shuffle" -> playbackViewModel?.shuffle()
            "repeat" -> playbackViewModel?.repeat()
            "audio_mode_battery" -> {
                playbackViewModel?.setPlaybackProfile(AudioMode.BATTERY, HardwareAcceleration.ADAPTIVE)
            }
            "audio_mode_balanced" -> {
                playbackViewModel?.setPlaybackProfile(AudioMode.BALANCED, HardwareAcceleration.AUTOMATIC)
            }
            "audio_mode_hifi" -> {
                playbackViewModel?.setPlaybackProfile(AudioMode.HIFI, HardwareAcceleration.FULL)
            }
            "hardware_acceleration" -> {
                onOpenSettings(); onSettingsPage("hardware-acceleration"); onSettingsSheetVisible(true)
            }
            "diagnostics" -> {
                onOpenSettings(); onSettingsPage("system-diagnostics"); onSettingsSheetVisible(true)
            }
            "playlists" -> { tab = LibraryTab.Playlists; collection = null }
            "favorites", "history" -> { tab = LibraryTab.Playlists; collection = id }
        }
        moreQuickActions = false
    }
    val songs = remember(state.songs, state.playCounts, query, sort, descending) {
        val collator = Collator.getInstance()
        val filtered = state.songs.filter { it.matches(query) }
        val ordered = when (sort) {
            LibrarySort.Duration -> filtered.sortedBy { it.durationMs }
            LibrarySort.Size -> filtered.sortedBy { it.size }
            LibrarySort.MostPlayed -> filtered.sortedWith(
                compareByDescending<Song> { state.playCounts[it.id] ?: 0 }
                    .thenBy { it.title.lowercase() })
            LibrarySort.Lyrics -> filtered.sortedWith { first, second ->
                // Ascending means lyrics available first; reversing the complete result below
                // gives the user the inverse grouping (songs without lyrics first).
                val lyricsOrder = second.hasUsableLyrics().compareTo(first.hasUsableLyrics())
                if (lyricsOrder != 0) lyricsOrder else collator.compare(first.title, second.title)
            }
            else -> filtered.sortedWith { a, b ->
                fun key(song: Song) = when (sort) {
                    LibrarySort.Artist -> song.artist
                    LibrarySort.Album -> song.album
                    else -> song.title
                }
                collator.compare(key(a), key(b))
            }
        }
        if (descending) ordered.reversed() else ordered
    }
    BackHandler(enabled = collection != null || searchVisible) {
        if (collection != null) collection = null else { searchVisible = false; query = "" }
    }
    Column(modifier) {
        LibraryHeader(state.scanning, { searchVisible = !searchVisible; if (!searchVisible) query = "" },
            onFolder, scan, { sheet = "sort" }, {
                onOpenSettings(); onSettingsSheetVisible(true)
            }, onSleep = { homeTool = "sleep" },
            showSearch = shortcuts["search"] != false, showFolder = false,
            showScan = shortcuts["scan"] != false,
            showSort = shortcuts["sort"] != false, showEqualizer = false,
            showSleep = shortcuts["sleep"] != false, quickActions = quickActions,
            onQuickAction = dispatchQuickAction, onMoreQuickActions = { moreQuickActions = true },
            activeQuickActionIds = highlightedQuickActionIds,
            sleepMinutes = playbackState?.sleepMinutes ?: 0,
            sleepEndAtMs = playbackState?.sleepEndAtMs ?: 0L)
        if (searchVisible) LibrarySearch(query) { query = it }
        LibraryTabs(tab) { tab = it; collection = null }
        if (state.scanning) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (state.failed) Text(stringResource(R.string.scan_failed), Modifier.padding(horizontal = 16.dp),
            color = MaterialTheme.colorScheme.error)
        LibraryContent(state, songs, tab, collection, { collection = it }, query.isNotEmpty(),
            canPlay, currentId, onPlay, onFolder, Modifier.weight(1f), onCreatePlaylist,
            onRemoveFromPlaylist, onDelete, onAudioOnly)
    }
    LibrarySheets(sheet,
        sort, descending, { sort = it; sheet = null }, { descending = !descending },
        { sheet = null }, state.deletedSongs, onRestoreDeleted)
    if (settingsSheetVisible) SettingsPanel(theme, onThemeChange, language, onLanguageChange,
        settingsStore, settingsPage, onSettingsPage, { onSettingsSheetVisible(false) }, onFolder,
        scan, { sheet = it }, playbackViewModel, onExport, onImport, onShare,
        onExportPackage, onImportPackage, onTransfer, transferState, onHostTransfer,
        onReceiveTransfer, onStopTransfer) { key, visible ->
        shortcuts = shortcuts + (key to visible)
    }
    if (homeTool == "sleep" && playbackViewModel != null && playbackState != null)
        SleepSheet(playbackState, playbackViewModel) { homeTool = null }
    if (moreQuickActions) ModalBottomSheet(onDismissRequest = { moreQuickActions = false }) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Text(stringResource(R.string.more_options), style = MaterialTheme.typography.titleLarge)
            quickActions.drop(8).forEach { action ->
                TextButton(onClick = { dispatchQuickAction(action.id) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(action.icon, action.label); Spacer(Modifier.width(14.dp)); Text(action.label, Modifier.weight(1f))
                }
            }
        }
    }
}
