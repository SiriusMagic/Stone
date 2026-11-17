package com.zirakusa.stone.nativeapp.feature.library.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zirakusa.stone.core.Song
import com.zirakusa.stone.nativeapp.R
import com.zirakusa.stone.nativeapp.feature.library.LibraryState
import com.zirakusa.stone.nativeapp.ui.components.*
import com.zirakusa.stone.nativeapp.ui.theme.StonePalette
import com.zirakusa.stone.nativeapp.ui.system.ImmersiveWindowEffect
import com.zirakusa.stone.nativeapp.feature.settings.api.color
import com.zirakusa.stone.nativeapp.feature.settings.api.label

@Composable internal fun PlaylistLibrary(state: LibraryState, collection: String?, select: (String?) -> Unit,
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
        "lyrics" -> audio.filter(com.zirakusa.stone.nativeapp.data.library.lyrics.LyricsIndex::has)
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
