package com.zirakusa.stone.nativeapp.feature.library.components

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zirakusa.stone.core.Song
import com.zirakusa.stone.nativeapp.R
import com.zirakusa.stone.nativeapp.feature.library.LibraryState
import com.zirakusa.stone.nativeapp.ui.components.*
import com.zirakusa.stone.nativeapp.ui.theme.StonePalette

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun LibraryContent(state: LibraryState, songs: List<Song>, tab: LibraryTab,
    collection: String?, select: (String?) -> Unit, searching: Boolean, canPlay: Boolean,
    currentId: String, onPlay: (Song, List<Song>) -> Unit, onFolder: () -> Unit,
    onScan: () -> Unit, modifier: Modifier = Modifier, onCreatePlaylist: (String) -> Unit = {},
    onRemoveFromPlaylist: (String, Song) -> Unit = { _, _ -> },
    onDelete: (Song) -> Unit = {}, onHide: (Song) -> Unit = {}, onAudioOnly: (Song) -> Unit = {}) {
    if (tab == LibraryTab.Playlists) {
        PlaylistLibrary(state, collection, select, canPlay, currentId, onPlay, modifier,
            onCreatePlaylist, onRemoveFromPlaylist)
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
        VideoLibraryContent(mediaSongs, state, modifier, onPlay, onFolder, onScan, onDelete, onHide, onAudioOnly)
        return
    }
    val visible = if (collection != null) mediaSongs.filter { groupKey(it) == collection } else mediaSongs
    var managing by remember(collection) { mutableStateOf(false) }
    var selectedIds by remember(collection) { mutableStateOf<Set<String>>(emptySet()) }
    var menuVisible by remember(collection) { mutableStateOf(false) }
    var deleteConfirmVisible by remember(collection) { mutableStateOf(false) }
    Column(modifier.fillMaxWidth()) {
    LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp)) {
        run {
            if (collection != null) item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (managing) {
                        StoneIconButton(StoneGlyph.Back, stringResource(R.string.back), {
                            managing = false; selectedIds = emptySet()
                        })
                        Text(stringResource(R.string.selected_count, selectedIds.size), fontWeight = FontWeight.Bold)
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
                        LibraryCover(glyph = glyph)
                        Column(Modifier.weight(1f)) {
                            Text(name, fontSize = 16.sp)
                            Text(stringResource(R.string.song_count, tracks.size), color = StonePalette.SongSubtitle, fontSize = 13.sp)
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
                        LibrarySongRow(song, song.id == currentId, false) { }
                    }
                } else LibrarySongRow(song, song.id == currentId, canPlay) { onPlay(song, visible) }
            }
            if (!searching && !state.hasFolder) item {
                LibraryScanActions(state.scanning, state.songs.isEmpty(), onFolder, onScan,
                    videos = tab == LibraryTab.Videos)
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
        title = { Text(stringResource(R.string.delete)) },
        text = { Text(stringResource(R.string.delete_selected_message, selectedIds.size)) },
        confirmButton = { TextButton(onClick = {
            visible.filter { it.id in selectedIds }.forEach(onDelete)
            deleteConfirmVisible = false; managing = false; selectedIds = emptySet()
        }) { Text(stringResource(R.string.delete)) } },
        dismissButton = { TextButton(onClick = { deleteConfirmVisible = false }) {
            Text(stringResource(R.string.cancel))
        } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun VideoLibraryContent(videos: List<Song>, state: LibraryState, modifier: Modifier,
    onPlay: (Song, List<Song>) -> Unit, onFolder: () -> Unit, onScan: () -> Unit,
    onDelete: (Song) -> Unit, onHide: (Song) -> Unit, onAudioOnly: (Song) -> Unit) {
    var folderBrowser by remember { mutableStateOf(false) }
    var selectedFolder by remember { mutableStateOf<String?>(null) }
    val visible = selectedFolder?.let { folder -> videos.filter { it.folder == folder } } ?: videos
    var selectedVideo by remember { mutableStateOf<Song?>(null) }
    var deleteConfirmVisible by remember { mutableStateOf(false) }
    var infoVideo by remember { mutableStateOf<Song?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            if (selectedFolder != null) IconButton(onClick = { selectedFolder = null; folderBrowser = true }) {
                Icon(Icons.Default.ArrowBack, stringResource(R.string.back))
            }
            Text(if (selectedFolder != null) selectedFolder.orEmpty()
                else stringResource(R.string.video_count, videos.size), Modifier.weight(1f),
                color = StonePalette.SongSubtitle, fontSize = 15.sp)
            IconButton(onClick = {
                folderBrowser = !folderBrowser
                if (!folderBrowser) selectedFolder = null
            }) { Icon(if (folderBrowser) Icons.Default.VideoLibrary else Icons.Default.Folder,
                stringResource(R.string.video_folders)) }
        }
        when {
            folderBrowser -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 18.dp)) {
                items(videos.groupBy { it.folder }.toList(), key = { it.first }) { (folder, items) ->
                    Row(Modifier.fillMaxWidth().clickable { selectedFolder = folder; folderBrowser = false }
                        .padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Folder, null, tint = StonePalette.Yellow, modifier = Modifier.size(30.dp))
                        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                            Text(folder, fontWeight = FontWeight.SemiBold)
                            Text(stringResource(R.string.video_count, items.size), color = StonePalette.SongSubtitle,
                                fontSize = 13.sp)
                        }
                        Icon(Icons.Default.ChevronRight, null, tint = StonePalette.Muted)
                    }
                }
            }
            visible.isNotEmpty() -> LazyVerticalGrid(columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp,
                    top = 4.dp, bottom = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(visible, key = { it.id }) { video ->
                    VideoGridCard(video, { onPlay(video, visible) }) { selectedVideo = video }
                }
            }
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp)) {
                item {
                    if (!state.scanning) LibraryEmptyState(videos = true)
                    if (!state.hasFolder) LibraryScanActions(state.scanning, state.songs.isEmpty(), onFolder,
                        onScan, videos = true)
                }
            }
        }
    }
    selectedVideo?.let { video ->
        ModalBottomSheet(onDismissRequest = { selectedVideo = null }) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                Text(video.title, fontWeight = FontWeight.Bold, maxLines = 2,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(bottom = 12.dp))
                TextButton(onClick = {
                    selectedVideo = null
                    com.zirakusa.stone.nativeapp.data.library.SongActions.share(context, video.uri, video.title)
                }) { Icon(Icons.Default.Share, null); Spacer(Modifier.width(12.dp)); Text(stringResource(R.string.share)) }
                TextButton(onClick = { selectedVideo = null }) {
                    Icon(Icons.Default.PictureInPictureAlt, null); Spacer(Modifier.width(12.dp));
                    Text(stringResource(R.string.floating_window_unavailable))
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                TextButton(onClick = { selectedVideo = null }) {
                    Icon(Icons.Default.Repeat, null); Spacer(Modifier.width(12.dp)); Text(stringResource(R.string.playback_mode))
                }
                TextButton(onClick = { selectedVideo = null }) {
                    Icon(Icons.Default.Fullscreen, null); Spacer(Modifier.width(12.dp)); Text(stringResource(R.string.video_scale))
                }
                TextButton(onClick = { val item = selectedVideo; selectedVideo = null; item?.let(onAudioOnly) }) {
                    Icon(Icons.Default.Headphones, null); Spacer(Modifier.width(12.dp)); Text(stringResource(R.string.audio_only))
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                TextButton(onClick = { selectedVideo = null }) {
                    Icon(Icons.Default.ClosedCaption, null); Spacer(Modifier.width(12.dp)); Text(stringResource(R.string.subtitles_cc))
                }
                TextButton(onClick = { selectedVideo = null }) {
                    Icon(Icons.Default.PlaylistAdd, null); Spacer(Modifier.width(12.dp)); Text(stringResource(R.string.add_to_playlist))
                }
                TextButton(onClick = { selectedVideo = null }) {
                    Icon(Icons.Default.Lock, null); Spacer(Modifier.width(12.dp)); Text(stringResource(R.string.private_folder))
                }
                TextButton(onClick = { infoVideo = selectedVideo; selectedVideo = null }) {
                    Icon(Icons.Default.Info, null); Spacer(Modifier.width(12.dp)); Text(stringResource(R.string.information))
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                TextButton(onClick = { val item = selectedVideo; selectedVideo = null; item?.let(onHide) }) {
                    Icon(Icons.Default.VisibilityOff, null); Spacer(Modifier.width(12.dp)); Text(stringResource(R.string.hide))
                }
                TextButton(onClick = { deleteConfirmVisible = true }) {
                    Icon(Icons.Default.Delete, null); Spacer(Modifier.width(12.dp)); Text(stringResource(R.string.delete))
                }
            }
        }
    }
    if (deleteConfirmVisible) AlertDialog(onDismissRequest = { deleteConfirmVisible = false },
        title = { Text(stringResource(R.string.delete_song_title)) },
        text = { Text(stringResource(R.string.delete_song_message)) },
        confirmButton = { TextButton(onClick = {
            selectedVideo?.let(onDelete); selectedVideo = null; deleteConfirmVisible = false
        }) { Text(stringResource(R.string.delete)) } },
        dismissButton = { TextButton(onClick = { deleteConfirmVisible = false }) { Text(stringResource(R.string.cancel)) } })
    infoVideo?.let { video -> AlertDialog(onDismissRequest = { infoVideo = null },
        title = { Text(stringResource(R.string.information)) },
        text = { Column { Text(video.title); Text(video.folder, color = StonePalette.Muted); Text(video.uri, color = StonePalette.Muted, fontSize = 12.sp) } },
        confirmButton = { TextButton(onClick = { infoVideo = null }) { Text(stringResource(R.string.dismiss)) } }) }
}

@Composable private fun PlaylistLibrary(state: LibraryState, collection: String?, select: (String?) -> Unit,
    canPlay: Boolean, currentId: String, onPlay: (Song, List<Song>) -> Unit, modifier: Modifier,
    onCreate: (String) -> Unit, onRemove: (String, Song) -> Unit) {
    var createVisible by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
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
            items(labels) { (id, label) -> PlaylistRow(stringResource(label), tracks(id).size) { select(id) } }
            items(state.playlists, key = { it.id }) { playlist -> PlaylistRow(playlist.name, playlist.songIds.size) { select(playlist.id) } }
        } else {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { select(null) }) { Icon(Icons.Default.ArrowBack, stringResource(R.string.back)) }
                    Column(Modifier.weight(1f)) { Text(collectionTitle, fontSize = 24.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Black)
                        Text(stringResource(R.string.song_count, currentTracks.size), color = StonePalette.Muted) }
                    FilledIconButton(enabled = currentTracks.isNotEmpty(), onClick = { onPlay(currentTracks.first(), currentTracks) }) {
                        Icon(Icons.Default.PlayArrow, stringResource(R.string.play)) }
                }
            }
            items(currentTracks, key = { it.id }) { song ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { LibrarySongRow(song, song.id == currentId, canPlay) { onPlay(song, currentTracks) } }
                    if (collection.startsWith("custom-")) IconButton(onClick = { onRemove(collection, song) }) {
                        Icon(Icons.Default.RemoveCircleOutline, stringResource(R.string.remove_from_playlist)) }
                }
            }
        }
    }
    if (createVisible) AlertDialog(onDismissRequest = { createVisible = false },
        title = { Text(stringResource(R.string.new_playlist)) }, text = { OutlinedTextField(name, { name = it },
            label = { Text(stringResource(R.string.playlist_name)) }, singleLine = true) },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onCreate(name); name = ""; createVisible = false }) {
            Text(stringResource(R.string.create)) } }, dismissButton = { TextButton(onClick = { createVisible = false }) {
            Text(stringResource(R.string.cancel)) } })
}

@Composable private fun PlaylistRow(title: String, count: Int, open: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = open).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        LibraryCover(glyph = StoneGlyph.Music)
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) { Text(title, fontSize = 17.sp)
            Text(stringResource(R.string.song_count, count), color = StonePalette.Muted) }
        Icon(Icons.Default.ChevronRight, null)
    }
}
