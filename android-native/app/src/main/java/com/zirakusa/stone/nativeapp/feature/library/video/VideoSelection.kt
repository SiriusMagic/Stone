package com.zirakusa.stone.nativeapp.feature.library.video

import androidx.activity.compose.BackHandler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.zirakusa.stone.core.Song
import com.zirakusa.stone.nativeapp.R
import com.zirakusa.stone.nativeapp.data.library.user.SongActions
import com.zirakusa.stone.nativeapp.feature.player.sheets.TagEditorSheet
import com.zirakusa.stone.nativeapp.ui.system.ImmersiveWindowEffect
import com.zirakusa.stone.nativeapp.feature.library.components.SelectionActionBar

/** Long-press selection for video grids/lists (Videos, Shorts, Movies). */
@Stable internal class VideoSelectionState {
    var active by mutableStateOf(false)
    var ids by mutableStateOf(emptySet<String>())
    var editing by mutableStateOf<Song?>(null)
    var confirmDelete by mutableStateOf(false)

    fun start(song: Song) { active = true; ids = setOf(song.id) }
    fun toggle(song: Song) { ids = if (song.id in ids) ids - song.id else ids + song.id }
    fun exit() { active = false; ids = emptySet() }
    fun checked(song: Song): Boolean? = if (active) song.id in ids else null
    fun editable(song: Song): Boolean = active && ids.size == 1 && song.id in ids
}

/** Click/long-click behaviour shared by every video card. */
internal fun VideoSelectionState.click(song: Song, play: () -> Unit) = if (active) toggle(song) else play()

/** Header, action bar and dialogs for the selection; place it around the video list. */
@Composable internal fun VideoSelectionDialogs(selection: VideoSelectionState, videos: List<Song>,
    onDelete: (Song) -> Unit, onTagsSaved: () -> Unit) {
    BackHandler(enabled = selection.active) { selection.exit() }
    selection.editing?.let { song ->
        TagEditorSheet(song, close = { selection.editing = null },
            saved = { selection.editing = null; selection.exit(); onTagsSaved() })
    }
    if (selection.confirmDelete) AlertDialog(onDismissRequest = { selection.confirmDelete = false },
        title = { ImmersiveWindowEffect(); Text(stringResource(R.string.delete)) },
        text = { Text(pluralStringResource(R.plurals.delete_selected_message, selection.ids.size, selection.ids.size)) },
        confirmButton = { TextButton(onClick = {
            videos.filter { it.id in selection.ids }.forEach(onDelete)
            selection.confirmDelete = false; selection.exit()
        }) { Text(stringResource(R.string.delete)) } },
        dismissButton = { TextButton(onClick = { selection.confirmDelete = false }) { Text(stringResource(R.string.cancel)) } })
}

@Composable internal fun VideoSelectionBar(selection: VideoSelectionState, videos: List<Song>) {
    val context = LocalContext.current
    if (selection.active) SelectionActionBar(videos = true, enabled = selection.ids.isNotEmpty(),
        onAddTo = {}, onPlayNext = {},
        onShare = { runCatching { SongActions.shareMany(context, videos.filter { it.id in selection.ids }) } },
        onDelete = { selection.confirmDelete = true })
}
