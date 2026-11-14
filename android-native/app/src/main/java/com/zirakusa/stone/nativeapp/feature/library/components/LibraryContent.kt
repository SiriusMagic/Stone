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
    onRestorePlaylistCover: (String) -> Unit = {}) {
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
    val mediaSongs = songs.filter { if (tab == LibraryTab.Videos) it.isVideo else !it.isVideo }
    if (tab == LibraryTab.Videos) {
        VideoLibraryContent(mediaSongs, state, currentId, modifier, onPlay, onFolder,
            compact, videoLibraryLayout, onVideoLibraryLayoutChange, onVideoOptions)
        return
    }
    val visible = if (collection != null) mediaSongs.filter { groupKey(it) == collection } else mediaSongs
    var managing by remember(collection) { mutableStateOf(false) }
    var selectedIds by remember(collection) { mutableStateOf<Set<String>>(emptySet()) }
    var menuVisible by remember(collection) { mutableStateOf(false) }
    var deleteConfirmVisible by remember(collection) { mutableStateOf(false) }
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
                }
            }
            if (collection != null) item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (managing) {
                        StoneIconButton(StoneGlyph.Back, stringResource(R.string.back), {
                            managing = false; selectedIds = emptySet()
                        })
                        Text(pluralStringResource(R.plurals.selected_count, selectedIds.size, selectedIds.size), fontWeight = FontWeight.Bold)
                    } else {
                        StoneIconButton(StoneGlyph.Back, stringResource(R.string.back), { select(null) })
                        Text(collection, Modifier.weight(1f))
                        IconButton(onClick = { menuVisible = true }) {
                            Icon(Icons.Default.MoreVert, stringResource(R.string.more_options))
                        }
                    }
                }
                if (managing) Row(Modifier.fillMaxWidth().clickable {
                    selectedIds = if (selectedIds.size == visible.size) emptySet() else visible.map { it.id }.toSet()
                }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selectedIds.size == visible.size && visible.isNotEmpty(), null)
                    Text(stringResource(R.string.select_all), color = StonePalette.Yellow, fontWeight = FontWeight.Bold)
                }
            }
            if (visible.isEmpty() && !state.scanning) item {
                if (searching) Text(stringResource(R.string.no_results), Modifier.padding(vertical = 32.dp), color = StonePalette.Muted)
                else LibraryEmptyState(videos = tab == LibraryTab.Videos)
            }
            if (grouped && collection == null) {
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
            } else items(visible, key = { it.id }) { song ->
                if (managing) Row(Modifier.fillMaxWidth().clickable {
                    selectedIds = if (song.id in selectedIds) selectedIds - song.id else selectedIds + song.id
                }, verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(song.id in selectedIds, null)
                    Box(Modifier.weight(1f)) {
                        LibrarySongRow(song, song.id == currentId, false, compact = compact) { }
                    }
                } else LibrarySongRow(song, song.id == currentId, canPlay, compact = compact) { onPlay(song, visible) }
            }
        }
    }
    if (managing) Surface(color = StonePalette.Surface, tonalElevation = 8.dp) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            TextButton(enabled = selectedIds.isNotEmpty(), onClick = { deleteConfirmVisible = true }) {
                Icon(Icons.Default.Delete, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.delete))
            }
        }
    }
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
    onVideoOptions: (Song) -> Unit) {
    var folderBrowser by rememberSaveable { mutableStateOf(false) }
    var selectedFolder by rememberSaveable { mutableStateOf<String?>(null) }
    val visible = selectedFolder?.let { folder -> videos.filter { it.folder == folder } } ?: videos
    val effectiveLayout = if (compact) VideoLibraryLayout.GRID else videoLibraryLayout
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            if (selectedFolder != null) IconButton(onClick = { selectedFolder = null; folderBrowser = true }) {
                Icon(Icons.Default.ArrowBack, stringResource(R.string.back))
            }
            Text(if (selectedFolder != null) selectedFolder.orEmpty()
                else pluralStringResource(R.plurals.video_count, videos.size, videos.size), Modifier.weight(1f),
                color = StonePalette.SongSubtitle, fontSize = 15.sp)
            IconButton(onClick = onFolder, enabled = !state.scanning) {
                Icon(Icons.Default.CreateNewFolder, stringResource(R.string.choose_video_folder))
            }
            IconButton(onClick = {
                folderBrowser = !folderBrowser
                if (!folderBrowser) selectedFolder = null
            }) { Icon(if (folderBrowser) Icons.Default.VideoLibrary else Icons.Default.Folder,
                stringResource(R.string.video_folders)) }
            if (!compact && selectedFolder == null) {
                IconButton(
                    onClick = {
                        onVideoLibraryLayoutChange(
                            if (videoLibraryLayout == VideoLibraryLayout.GRID) VideoLibraryLayout.LIST
                            else VideoLibraryLayout.GRID
                        )
                    },
                    modifier = Modifier.testTag("video-layout-toggle")
                ) {
                    Icon(
                        if (videoLibraryLayout == VideoLibraryLayout.GRID) Icons.Default.ViewList
                        else Icons.Default.ViewModule,
                        stringResource(
                            if (videoLibraryLayout == VideoLibraryLayout.GRID)
                                R.string.video_layout_list else R.string.video_layout_grid
                        )
                    )
                }
            }
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
                    modifier = Modifier.fillMaxSize().testTag("video-grid"),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp,
                        top = 4.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(visible, key = { it.id }) { video ->
                        VideoGridCard(video, selected = video.id == currentId,
                            play = { onPlay(video, visible) },
                            onOptions = { onVideoOptions(video) })
                    }
                }
            visible.isNotEmpty() && effectiveLayout == VideoLibraryLayout.LIST ->
                LazyColumn(
                    modifier = Modifier.fillMaxSize().testTag("video-list"),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp)
                ) {
                    items(visible, key = { it.id }) { video ->
                        VideoListRow(video, selected = video.id == currentId,
                            play = { onPlay(video, visible) },
                            onOptions = { onVideoOptions(video) })
                    }
                }
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp)) {
                item {
                    if (!state.scanning) LibraryEmptyState(videos = true)
                }
            }
        }
    }
}

@Composable private fun PlaylistLibrary(state: LibraryState, collection: String?, select: (String?) -> Unit,
    canPlay: Boolean, currentId: String, onPlay: (Song, List<Song>) -> Unit, modifier: Modifier,
    onCreate: (String) -> Unit, onRemove: (String, Song) -> Unit, compact: Boolean,
    onSetPlaylistCover: (String, Uri) -> Unit, onRestorePlaylistCover: (String) -> Unit) {
    var createVisible by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var coverTarget by remember { mutableStateOf<String?>(null) }
    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val target = coverTarget
        coverTarget = null
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
            item { Button(onClick = { createVisible = true }, Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, null); Text(stringResource(R.string.new_playlist)) } }
            items(labels) { (id, label) ->
                PlaylistRow(
                    title = stringResource(label), count = tracks(id).size,
                    coverUri = state.playlistCovers[id], coverSongs = tracks(id).take(4),
                    playing = tracks(id).any { it.id == currentId }, compact = compact,
                    open = { select(id) }, onCoverOptions = { coverTarget = id }
                )
            }
            items(state.playlists, key = { it.id }) { playlist ->
                val playlistTracks = tracks(playlist.id)
                PlaylistRow(
                    title = playlist.name, count = playlistTracks.size,
                    coverUri = state.playlistCovers[playlist.id], coverSongs = playlistTracks.take(4),
                    playing = playlistTracks.any { it.id == currentId }, compact = compact,
                    open = { select(playlist.id) }, onCoverOptions = { coverTarget = playlist.id }
                )
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
                TextButton(onClick = { coverTarget = null; coverPicker.launch("image/*") }) {
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

@Composable private fun PlaylistRow(title: String, count: Int, coverUri: String?, coverSongs: List<Song>,
    playing: Boolean, compact: Boolean, open: () -> Unit, onCoverOptions: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable(onClick = open),
        shape = MaterialTheme.shapes.medium,
        color = if (playing) StonePalette.PlayingCover.copy(alpha = .28f) else StonePalette.Surface,
        tonalElevation = if (playing) 3.dp else 0.dp,
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            PlaylistArtwork(coverUri, coverSongs, Modifier.size(if (compact) 52.dp else 64.dp))
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(title, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(pluralStringResource(R.plurals.song_count, count, count), color = StonePalette.Muted)
            }
            if (playing) Icon(Icons.Default.Equalizer, stringResource(R.string.playing),
                tint = StonePalette.Yellow, modifier = Modifier.padding(end = 4.dp))
            IconButton(onClick = onCoverOptions) {
                Icon(Icons.Default.MoreVert, stringResource(R.string.playlist_cover_options))
            }
            Icon(Icons.Default.ChevronRight, null, tint = StonePalette.Muted)
        }
    }
}

@Composable private fun PlaylistArtwork(customUri: String?, songs: List<Song>, modifier: Modifier) {
    val shape = MaterialTheme.shapes.medium
    Box(modifier.clip(shape).background(StonePalette.Cover), contentAlignment = Alignment.Center) {
        if (!customUri.isNullOrBlank()) {
            StoneArtwork(customUri, 0L, Modifier.fillMaxSize())
        } else if (songs.isEmpty()) {
            Icon(Icons.Default.QueueMusic, stringResource(R.string.empty_playlist),
                tint = StonePalette.Yellow, modifier = Modifier.size(28.dp))
        } else {
            val tiles = songs.take(4)
            if (tiles.size == 1) {
                StoneArtwork(tiles.first().artworkSource, tiles.first().modifiedMs, Modifier.fillMaxSize())
            } else {
                Column(Modifier.fillMaxSize()) {
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        tiles.take(2).forEach { song ->
                            StoneArtwork(song.artworkSource, song.modifiedMs, Modifier.weight(1f).fillMaxHeight())
                        }
                    }
                    if (tiles.size > 2) Row(Modifier.weight(1f).fillMaxWidth()) {
                        tiles.drop(2).take(2).forEach { song ->
                            StoneArtwork(song.artworkSource, song.modifiedMs, Modifier.weight(1f).fillMaxHeight())
                        }
                    }
                }
            }
        }
    }
}
