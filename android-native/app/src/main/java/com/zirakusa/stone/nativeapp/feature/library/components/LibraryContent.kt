package com.zirakusa.stone.nativeapp.feature.library.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zirakusa.stone.core.Song
import com.zirakusa.stone.nativeapp.R
import com.zirakusa.stone.nativeapp.feature.library.LibraryState
import com.zirakusa.stone.nativeapp.ui.components.*
import com.zirakusa.stone.nativeapp.ui.theme.StoneLook
import com.zirakusa.stone.nativeapp.ui.theme.StonePalette
import com.zirakusa.stone.nativeapp.ui.ImmersiveWindowEffect

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun LibraryContent(state: LibraryState, songs: List<Song>, tab: LibraryTab,
    collection: String?, select: (String?) -> Unit, searching: Boolean, canPlay: Boolean,
    currentId: String, onPlay: (Song, List<Song>) -> Unit, onFolder: () -> Unit,
    modifier: Modifier = Modifier, onCreatePlaylist: (String) -> Unit = {},
    onRemoveFromPlaylist: (String, Song) -> Unit = { _, _ -> },
    onDelete: (Song) -> Unit = {}, onAudioOnly: (Song) -> Unit = {},
    onOpenFolders: () -> Unit = {}, compact: Boolean = false,
    onVideoOptions: (Song) -> Unit = {},
    videoLibraryLayout: VideoLibraryLayout = VideoLibraryLayout.GRID,
    onVideoLibraryLayoutChange: (VideoLibraryLayout) -> Unit = {},
    onSetPlaylistCover: (String, Uri) -> Unit = { _, _ -> },
    onRestorePlaylistCover: (String) -> Unit = {},
    selectionActions: LibrarySelectionActions = LibrarySelectionActions()) {
    if (tab == LibraryTab.Playlists) {
        PlaylistLibrary(state, collection, select, canPlay, currentId, onPlay, modifier,
            onCreatePlaylist, onRemoveFromPlaylist, compact,
            onSetPlaylistCover, onRestorePlaylistCover)
        return
    }
    val groupKey: (Song) -> String = when (tab) {
        LibraryTab.Folders -> { song -> song.folder }
        LibraryTab.Artists -> { song -> song.artist }
        LibraryTab.Albums -> { song -> song.album }
        else -> { _ -> "" }
    }
    val grouped = tab in listOf(LibraryTab.Folders, LibraryTab.Artists, LibraryTab.Albums)
    val mediaSongs = songs.filter(tab::accepts)
    if (tab.isVideo) {
        VideoLibraryContent(mediaSongs, state, currentId, modifier, onPlay, onFolder,
            compact, videoLibraryLayout, onVideoLibraryLayoutChange, onVideoOptions, onDelete, selectionActions.onTagsSaved)
        return
    }
    val visible = if (collection != null) mediaSongs.filter { groupKey(it) == collection } else mediaSongs
    val songLayout = VideoLibraryLayout.fromKey(StoneLook.songLayout)
    val dense = compact || songLayout == VideoLibraryLayout.COMPACT
    var managing by remember(collection) { mutableStateOf(false) }
    var selectedIds by remember(collection) { mutableStateOf<Set<String>>(emptySet()) }
    var menuVisible by remember(collection) { mutableStateOf(false) }
    var deleteConfirmVisible by remember(collection) { mutableStateOf(false) }
    var pickPlaylist by remember { mutableStateOf(false) }
    var tagSong by remember { mutableStateOf<Song?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    androidx.activity.compose.BackHandler(enabled = managing) { managing = false; selectedIds = emptySet() }
    Column(modifier.fillMaxWidth()) {
    LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("library-list"), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp)) {
        run {
            if (!compact && tab == LibraryTab.Songs && collection == null) item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(pluralStringResource(R.plurals.song_count, visible.size, visible.size), Modifier.weight(1f),
                        color = StonePalette.SongSubtitle, fontSize = 15.sp)
                    IconButton(onClick = onFolder, enabled = !state.scanning) {
                        Icon(Icons.Default.CreateNewFolder, stringResource(R.string.choose_folder))
                    }
                    IconButton(onClick = onOpenFolders) {
                        Icon(Icons.Default.Folder, stringResource(R.string.folders))
                    }
                    LayoutSelector(songLayout, { StoneLook.chooseSongLayout(it.key) })
                }
            }
            if (!compact && tab == LibraryTab.Folders && collection == null) item {
                // Same header as Songs: count + add-folder icon at the start of the section.
                val folderCount = remember(mediaSongs) { mediaSongs.mapTo(HashSet(), groupKey).size }
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(pluralStringResource(R.plurals.folder_count, folderCount, folderCount), Modifier.weight(1f),
                        color = StonePalette.SongSubtitle, fontSize = 15.sp)
                    IconButton(onClick = onFolder, enabled = !state.scanning) {
                        Icon(Icons.Default.CreateNewFolder, stringResource(R.string.choose_folder))
                    }
                }
            }
            if (managing) item {
                SelectionHeader(selectedIds.size, visible.size, onExit = { managing = false; selectedIds = emptySet() },
                    onToggleAll = {
                        selectedIds = if (selectedIds.size == visible.size) emptySet() else visible.map { it.id }.toSet()
                    })
            } else if (collection != null) item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StoneIconButton(StoneGlyph.Back, stringResource(R.string.back), { select(null) })
                    Text(collection, Modifier.weight(1f))
                    IconButton(onClick = { menuVisible = true }) {
                        Icon(Icons.Default.MoreVert, stringResource(R.string.more_options))
                    }
                }
            }
            if (visible.isEmpty() && !state.scanning) item {
                if (searching) Text(stringResource(R.string.no_results), Modifier.padding(vertical = 32.dp), color = StonePalette.Muted)
                else LibraryEmptyState(videos = tab.isVideo)
            }
            if (grouped && collection == null && !managing) {
                items(mediaSongs.groupBy(groupKey).toList(), key = { it.first }) { (name, tracks) ->
                    if (tab == LibraryTab.Albums) {
                        AlbumCard(name, tracks) { select(name) }
                    } else if (tab == LibraryTab.Artists) {
                        ArtistCard(name, tracks) { select(name) }
                    } else {
                    val glyph = when (tab) {
                        LibraryTab.Artists -> StoneGlyph.Person
                        LibraryTab.Albums -> StoneGlyph.Album
                        else -> StoneGlyph.Folder
                    }
                    Row(Modifier.fillMaxWidth().clickable { select(name) }.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        LibraryCover(glyph = glyph, compact = compact)
                        Column(Modifier.weight(1f)) {
                            Text(name, fontSize = 16.sp)
                            Text(pluralStringResource(R.plurals.song_count, tracks.size, tracks.size), color = StonePalette.SongSubtitle, fontSize = 13.sp)
                        }
                        StoneIcon(StoneGlyph.Forward, color = StonePalette.Muted)
                    }
                    }
                }
            } else if (songLayout == VideoLibraryLayout.GRID && !compact) {
                // "Grande": two big covers per row.
                items(visible.chunked(2), key = { it.first().id }) { pair ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        pair.forEach { song ->
                            Box(Modifier.weight(1f)) {
                                SongGridCard(song, current = song.id == currentId,
                                    checked = if (managing) song.id in selectedIds else null,
                                    onEdit = if (managing && selectedIds.size == 1 && song.id in selectedIds) {
                                        { tagSong = song }
                                    } else null,
                                    onLongClick = { if (!managing) { managing = true; selectedIds = setOf(song.id) } },
                                    onClick = {
                                        if (managing) selectedIds = if (song.id in selectedIds) selectedIds - song.id
                                            else selectedIds + song.id
                                        else if (canPlay) onPlay(song, visible)
                                    })
                            }
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            } else items(visible, key = { it.id }) { song ->
                if (managing) Row(Modifier.fillMaxWidth().clickable {
                    selectedIds = if (song.id in selectedIds) selectedIds - song.id else selectedIds + song.id
                }, verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(song.id in selectedIds, null,
                        colors = RadioButtonDefaults.colors(selectedColor = StonePalette.Blue))
                    Box(Modifier.weight(1f)) {
                        LibrarySongRow(song, song.id == currentId, false, compact = dense,
                            trailing = if (selectedIds.size == 1 && song.id in selectedIds) {
                                { EditTagsButton { tagSong = song } }
                            } else null) { }
                    }
                } else LibrarySongRow(song, song.id == currentId, canPlay, compact = dense,
                    onLongClick = { managing = true; selectedIds = setOf(song.id) }) { onPlay(song, visible) }
            }
        }
    }
    val selectedSongs = visible.filter { it.id in selectedIds }
    if (managing) SelectionActionBar(videos = false, enabled = selectedIds.isNotEmpty(),
        onAddTo = { pickPlaylist = true },
        onPlayNext = { selectionActions.onPlayNext(selectedSongs); managing = false; selectedIds = emptySet() },
        onShare = { runCatching { com.zirakusa.stone.nativeapp.data.library.SongActions.shareMany(context, selectedSongs) } },
        onDelete = { deleteConfirmVisible = true })
    }
    if (menuVisible) ModalBottomSheet(onDismissRequest = { menuVisible = false }) {
        ImmersiveWindowEffect()
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
            TextButton(onClick = { menuVisible = false; managing = true }) {
                Icon(Icons.Default.CheckCircle, null); Spacer(Modifier.width(12.dp)); Text(stringResource(R.string.manage))
            }
            TextButton(onClick = {
                menuVisible = false; selectedIds = visible.map { it.id }.toSet(); managing = true; deleteConfirmVisible = true
            }) {
                Icon(Icons.Default.Delete, null); Spacer(Modifier.width(12.dp)); Text(stringResource(R.string.delete_album))
            }
        }
    }
    if (pickPlaylist) MultiPlaylistPicker(state.playlists, visible.filter { it.id in selectedIds },
        selectionActions.onAddToPlaylist) { pickPlaylist = false; managing = false; selectedIds = emptySet() }
    tagSong?.let { song ->
        com.zirakusa.stone.nativeapp.feature.player.TagEditorSheet(song, close = { tagSong = null },
            saved = { tagSong = null; managing = false; selectedIds = emptySet(); selectionActions.onTagsSaved() })
    }
    if (deleteConfirmVisible) AlertDialog(onDismissRequest = { deleteConfirmVisible = false },
        title = { ImmersiveWindowEffect(); Text(stringResource(R.string.delete)) },
        text = { Text(pluralStringResource(R.plurals.delete_selected_message, selectedIds.size, selectedIds.size)) },
        confirmButton = { TextButton(onClick = {
            visible.filter { it.id in selectedIds }.forEach(onDelete)
            deleteConfirmVisible = false; managing = false; selectedIds = emptySet()
        }) { Text(stringResource(R.string.delete)) } },
        dismissButton = { TextButton(onClick = { deleteConfirmVisible = false }) {
            Text(stringResource(R.string.cancel))
        } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun VideoLibraryContent(videos: List<Song>, state: LibraryState, currentId: String, modifier: Modifier,
    onPlay: (Song, List<Song>) -> Unit, onFolder: () -> Unit,
    compact: Boolean, videoLibraryLayout: VideoLibraryLayout,
    onVideoLibraryLayoutChange: (VideoLibraryLayout) -> Unit,
    onVideoOptions: (Song) -> Unit, onDelete: (Song) -> Unit, onTagsSaved: () -> Unit) {
    val selection = remember { VideoSelectionState() }
    var folderBrowser by rememberSaveable { mutableStateOf(false) }
    var selectedFolder by rememberSaveable { mutableStateOf<String?>(null) }
    val visible = selectedFolder?.let { folder -> videos.filter { it.folder == folder } } ?: videos
    val effectiveLayout = if (compact) VideoLibraryLayout.GRID else videoLibraryLayout
    VideoSelectionDialogs(selection, visible, onDelete, onTagsSaved)
    Column(modifier.fillMaxWidth()) {
        if (selection.active) Box(Modifier.padding(horizontal = 12.dp)) {
            SelectionHeader(selection.ids.size, visible.size, onExit = selection::exit, onToggleAll = {
                selection.ids = if (selection.ids.size == visible.size) emptySet() else visible.map { it.id }.toSet()
            })
        } else Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            if (selectedFolder != null) IconButton(onClick = { selectedFolder = null; folderBrowser = true }) {
                Icon(Icons.Default.ArrowBack, stringResource(R.string.back))
            }
            Text(if (selectedFolder != null) selectedFolder.orEmpty()
                else pluralStringResource(R.plurals.video_count,
                    videos.size, videos.size), Modifier.weight(1f),
                color = StonePalette.SongSubtitle, fontSize = 15.sp)
            IconButton(onClick = onFolder, enabled = !state.scanning) {
                Icon(Icons.Default.CreateNewFolder, stringResource(R.string.choose_video_folder))
            }
            IconButton(onClick = {
                folderBrowser = !folderBrowser
                if (!folderBrowser) selectedFolder = null
            }) { Icon(if (folderBrowser) Icons.Default.VideoLibrary else Icons.Default.Folder,
                stringResource(R.string.video_folders)) }
            if (!compact && selectedFolder == null) LayoutSelector(videoLibraryLayout, onVideoLibraryLayoutChange,
                Modifier.testTag("video-layout-toggle"))
        }
        when {
            folderBrowser -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 18.dp)) {
                items(videos.groupBy { it.folder }.toList(), key = { it.first }) { (folder, items) ->
                    Row(Modifier.fillMaxWidth().clickable { selectedFolder = folder; folderBrowser = false }
                        .padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Folder, null, tint = StonePalette.Yellow, modifier = Modifier.size(30.dp))
                        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                            Text(folder, fontWeight = FontWeight.SemiBold)
                            Text(pluralStringResource(R.plurals.video_count, items.size, items.size), color = StonePalette.SongSubtitle,
                                fontSize = 13.sp)
                        }
                        Icon(Icons.Default.ChevronRight, null, tint = StonePalette.Muted)
                    }
                }
            }
            visible.isNotEmpty() && effectiveLayout == VideoLibraryLayout.GRID ->
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(170.dp),
                    modifier = Modifier.weight(1f).fillMaxWidth().testTag("video-grid"),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp,
                        top = 4.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(visible, key = { it.id }) { video ->
                        val edit: (() -> Unit)? = if (selection.editable(video)) { { selection.editing = video } } else null
                        VideoGridCard(video, selected = video.id == currentId,
                            play = { selection.click(video) { onPlay(video, visible) } },
                            onOptions = { onVideoOptions(video) }, checked = selection.checked(video),
                            onLongClick = { if (!selection.active) selection.start(video) },
                            onEdit = edit)
                    }
                }
            visible.isNotEmpty() ->
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth().testTag("video-list"),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp)
                ) {
                    items(visible, key = { it.id }) { video ->
                        VideoListRow(video, selected = video.id == currentId,
                            play = { selection.click(video) { onPlay(video, visible) } },
                            onOptions = { onVideoOptions(video) }, checked = selection.checked(video),
                            onLongClick = { if (!selection.active) selection.start(video) },
                            onEdit = if (selection.editable(video)) { { selection.editing = video } } else null,
                            dense = effectiveLayout == VideoLibraryLayout.COMPACT)
                    }
                }
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp)) {
                item {
                    if (!state.scanning) LibraryEmptyState(videos = true)
                }
            }
        }
        VideoSelectionBar(selection, visible)
    }
}

@Composable private fun PlaylistLibrary(state: LibraryState, collection: String?, select: (String?) -> Unit,
    canPlay: Boolean, currentId: String, onPlay: (Song, List<Song>) -> Unit, modifier: Modifier,
    onCreate: (String) -> Unit, onRemove: (String, Song) -> Unit, compact: Boolean,
    onSetPlaylistCover: (String, Uri) -> Unit, onRestorePlaylistCover: (String) -> Unit) {
    var createVisible by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var coverTarget by remember { mutableStateOf<String?>(null) }
    // Kept apart from the dialog target (and saved): closing the dialog must not forget which
    // playlist the gallery result belongs to.
    var coverPickTarget by rememberSaveable { mutableStateOf<String?>(null) }
    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val target = coverPickTarget
        coverPickTarget = null
        if (target != null && uri != null) onSetPlaylistCover(target, uri)
    }
    val audio = state.songs.filterNot { it.isVideo }
    fun tracks(key: String): List<Song> = when (key) {
        "favorites" -> audio.filter { it.id in state.favoriteIds }
        "recent" -> state.recentIds.mapNotNull { id -> audio.find { it.id == id } }
        "most" -> audio.filter { (state.playCounts[it.id] ?: 0) > 0 }
            .sortedByDescending { state.playCounts[it.id] ?: 0 }
        "lyrics" -> audio.filter { it.lyrics.isNotEmpty() }
        else -> state.playlists.find { it.id == key }?.songIds.orEmpty().mapNotNull { id -> audio.find { it.id == id } }
    }
    val labels = listOf("favorites" to R.string.favorite_songs, "recent" to R.string.recently_played,
        "most" to R.string.most_played, "lyrics" to R.string.with_lyrics)
    val currentTracks = collection?.let(::tracks).orEmpty()
    val collectionTitle = collection?.let { key -> labels.find { it.first == key }?.second?.let { stringResource(it) }
        ?: state.playlists.find { it.id == key }?.name.orEmpty() }.orEmpty()
    LazyColumn(modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
        if (collection == null) {
            item { NewPlaylistCard { createVisible = true } }
            items(labels) { (id, label) ->
                val list = tracks(id)
                PlaylistCard(id, stringResource(label), list, state.playlistCovers[id], list.any { it.id == currentId },
                    compact, open = { select(id) }, onCoverOptions = { coverTarget = id })
            }
            items(state.playlists, key = { it.id }) { playlist ->
                val list = tracks(playlist.id)
                PlaylistCard(playlist.id, playlist.name, list, state.playlistCovers[playlist.id], list.any { it.id == currentId },
                    compact, open = { select(playlist.id) }, onCoverOptions = { coverTarget = playlist.id })
            }
        } else {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { select(null) }) { Icon(Icons.Default.ArrowBack, stringResource(R.string.back)) }
                    Column(Modifier.weight(1f)) { Text(collectionTitle, fontSize = 24.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Black)
                        Text(pluralStringResource(R.plurals.song_count, currentTracks.size, currentTracks.size), color = StonePalette.Muted) }
                    FilledIconButton(enabled = currentTracks.isNotEmpty(), onClick = { onPlay(currentTracks.first(), currentTracks) }) {
                        Icon(Icons.Default.PlayArrow, stringResource(R.string.play)) }
                }
            }
            items(currentTracks, key = { it.id }) { song ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { LibrarySongRow(song, song.id == currentId, canPlay, compact = compact) { onPlay(song, currentTracks) } }
                    if (collection.startsWith("custom-")) IconButton(onClick = { onRemove(collection, song) }) {
                        Icon(Icons.Default.RemoveCircleOutline, stringResource(R.string.remove_from_playlist)) }
                }
            }
        }
    }
    if (createVisible) AlertDialog(onDismissRequest = { createVisible = false },
        title = { ImmersiveWindowEffect(); Text(stringResource(R.string.new_playlist)) }, text = { OutlinedTextField(name, { name = it },
            label = { Text(stringResource(R.string.playlist_name)) }, singleLine = true) },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onCreate(name); name = ""; createVisible = false }) {
            Text(stringResource(R.string.create)) } }, dismissButton = { TextButton(onClick = { createVisible = false }) {
            Text(stringResource(R.string.cancel)) } })
    if (coverTarget != null) {
        val target = coverTarget!!
        val hasCustom = state.playlistCovers[target].orEmpty().isNotBlank()
        AlertDialog(
            onDismissRequest = { coverTarget = null },
            title = { ImmersiveWindowEffect(); Text(stringResource(R.string.playlist_cover_title)) },
            text = { Text(stringResource(R.string.playlist_cover_message)) },
            confirmButton = {
                TextButton(onClick = { coverPickTarget = target; coverTarget = null; coverPicker.launch("image/*") }) {
                    Text(stringResource(R.string.choose_cover))
                }
            },
            dismissButton = {
                TextButton(enabled = hasCustom, onClick = {
                    onRestorePlaylistCover(target); coverTarget = null
                }) { Text(stringResource(R.string.restore_automatic_cover)) }
            }
        )
    }
}

/** Callbacks used by long-press selection in the library lists. */
class LibrarySelectionActions(
    val onAddToPlaylist: (String, Song) -> Unit = { _, _ -> },
    val onPlayNext: (List<Song>) -> Unit = {},
    val onTagsSaved: () -> Unit = {},
)
