package com.zirakusa.stone.nativeapp.feature.library.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zirakusa.stone.core.Song
import com.zirakusa.stone.nativeapp.R
import com.zirakusa.stone.nativeapp.data.library.StonePlaylist
import com.zirakusa.stone.nativeapp.ui.ImmersiveWindowEffect
import com.zirakusa.stone.nativeapp.ui.theme.StonePalette

/** "N seleccionado" header with back and select-all, shown after a long press. */
@Composable internal fun SelectionHeader(selected: Int, total: Int, onExit: () -> Unit, onToggleAll: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onExit) { Icon(Icons.Default.ArrowBackIosNew, stringResource(R.string.back)) }
            Text(pluralStringResource(R.plurals.selected_count, selected, selected), fontSize = 20.sp,
                fontWeight = FontWeight.Bold)
        }
        Row(Modifier.fillMaxWidth().clickable(onClick = onToggleAll).padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected == total && total > 0, null,
                colors = RadioButtonDefaults.colors(selectedColor = StonePalette.Blue))
            Text(stringResource(R.string.select_all), color = StonePalette.Blue, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        }
    }
}

/** Bottom actions for the selection. Songs: add to, play next, share, delete. Videos: share, delete. */
@Composable internal fun SelectionActionBar(videos: Boolean, enabled: Boolean, onAddTo: () -> Unit,
    onPlayNext: () -> Unit, onShare: () -> Unit, onDelete: () -> Unit) {
    Surface(color = StonePalette.Surface, shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp), tonalElevation = 8.dp) {
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly) {
            if (!videos) {
                SelectionAction(Icons.Default.PlaylistAdd, R.string.selection_add_to, enabled, onAddTo)
                SelectionAction(Icons.Default.QueuePlayNext, R.string.selection_play_next, enabled, onPlayNext)
            }
            SelectionAction(Icons.Default.Share, R.string.share, enabled, onShare)
            SelectionAction(Icons.Default.Delete, R.string.delete, enabled, onDelete)
        }
    }
}

@Composable private fun RowScope.SelectionAction(icon: ImageVector, label: Int, enabled: Boolean, onClick: () -> Unit) {
    Column(Modifier.weight(1f).clickable(enabled = enabled, onClick = onClick).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = if (enabled) StonePalette.Blue else StonePalette.Muted, modifier = Modifier.size(28.dp))
        Text(stringResource(label), color = if (enabled) StonePalette.Secondary else StonePalette.Muted,
            fontSize = 12.sp, textAlign = TextAlign.Center, maxLines = 2, modifier = Modifier.padding(top = 4.dp))
    }
}

/** Adds every selected song to one playlist. */
@Composable internal fun MultiPlaylistPicker(playlists: List<StonePlaylist>, songs: List<Song>,
    onAdd: (String, Song) -> Unit, close: () -> Unit) {
    AlertDialog(onDismissRequest = close,
        title = { ImmersiveWindowEffect(); Text(stringResource(R.string.add_to_playlist)) },
        text = {
            Column {
                if (playlists.isEmpty()) Text(stringResource(R.string.selection_no_playlists), color = StonePalette.Muted)
                playlists.forEach { playlist ->
                    TextButton(onClick = { songs.forEach { onAdd(playlist.id, it) }; close() }) { Text(playlist.name) }
                }
            }
        },
        confirmButton = { TextButton(onClick = close) { Text(stringResource(R.string.cancel)) } })
}

/** Pencil shown next to the only selected item; opens the tag editor. */
@Composable internal fun EditTagsButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) { Icon(Icons.Default.Edit, stringResource(R.string.tag_editor), tint = StonePalette.Secondary) }
}
