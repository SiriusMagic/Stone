package com.zirakusa.stone.nativeapp.feature.player

import android.widget.Toast
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.zirakusa.stone.core.Song
import com.zirakusa.stone.nativeapp.data.library.SongActions
import com.zirakusa.stone.nativeapp.data.library.StonePlaylist
import com.zirakusa.stone.nativeapp.playback.PlaybackState
import com.zirakusa.stone.nativeapp.playback.PlaybackViewModel

/** Shared by the full player and the home dock; only the visible presentation owns this host. */
@Composable internal fun PlayerSheetsHost(sheets: PlayerSheetsState, state: PlaybackState,
    song: Song?, playback: PlaybackViewModel, progress: Pair<Long, Long>,
    playlists: List<StonePlaylist>, onCreatePlaylist: (String) -> Unit,
    onAddToPlaylist: (String, Song) -> Unit, onDelete: (Song) -> Unit,
    onMetadataChanged: () -> Unit, onEditComplete: () -> Unit = {},
    extraActions: List<PlayerOptionExtra> = emptyList()) {
    val context = LocalContext.current
    val targetSong = sheets.actionSong ?: sheets.optionsSong ?: song
    if (sheets.queueVisible) QueueSheet(state, playback) { sheets.queueVisible = false }
    if (sheets.lyricsVisible && song != null) LyricsSheet(song, state, playback, progress) { sheets.lyricsVisible = false }
    if (sheets.optionsVisible && !sheets.optionsSidePanel) PlayerOptionsSheet(
        state = state,
        song = targetSong,
        extraActions = extraActions,
        onClose = {
            sheets.optionsVisible = false
            sheets.optionsSong = null
        },
        onSelect = { selected ->
            val selectedSong = sheets.actionSong ?: sheets.optionsSong ?: song
            sheets.optionsVisible = false
            sheets.optionsSong = null
            selectPlayerTool(context, sheets, selected, selectedSong, playback)
        }
    )
    val close = { sheets.tool = null; sheets.actionSong = null }
    val edited = { sheets.tool = null; sheets.actionSong = null; onMetadataChanged(); onEditComplete() }
    when (sheets.tool) {
        PlayerTool.Speed -> SpeedSheet(state, playback, close)
        PlayerTool.Equalizer -> EqualizerSheet(state, playback, progress, song = targetSong, close = close)
        PlayerTool.Cover -> targetSong?.let { TagEditorSheet(it, initialTab = 0, close = close, saved = edited) }
        PlayerTool.Sleep -> SleepSheet(state, playback, close)
        PlayerTool.Tags -> targetSong?.let { TagEditorSheet(it, close = close, saved = edited) }
        PlayerTool.Ringtone -> targetSong?.let { RingtoneSheet(it, close) }
        PlayerTool.Playlist -> targetSong?.let { PlaylistPickerSheet(it, playlists, onCreatePlaylist, onAddToPlaylist, close) }
        PlayerTool.Delete -> ConfirmSongAction({
            targetSong?.let { playback.remove(it.id); onDelete(it) }; close(); onEditComplete()
        }, close)
        else -> Unit
    }
}

/** Keeps option actions identical whether they came from a sheet or the horizontal side panel. */
internal fun selectPlayerTool(context: Context, sheets: PlayerSheetsState, selected: PlayerTool,
    song: Song?, playback: PlaybackViewModel) {
    sheets.actionSong = song
    when (selected) {
        PlayerTool.Share -> {
            song?.let { runCatching { SongActions.share(context, it.uri, it.title) }
                .onFailure { error -> Toast.makeText(context, error.message, Toast.LENGTH_LONG).show() } }
            sheets.actionSong = null
        }
        PlayerTool.FloatingWindow -> {
            enterVideoPictureInPicture(context, playback.player())
            sheets.actionSong = null
        }
        else -> sheets.tool = selected
    }
}
