package com.zirakusa.stone.nativeapp.feature.player

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.zirakusa.stone.core.Song
import com.zirakusa.stone.nativeapp.data.library.SongActions
import com.zirakusa.stone.nativeapp.data.library.StonePlaylist
import com.zirakusa.stone.nativeapp.playback.PlaybackState
import com.zirakusa.stone.nativeapp.playback.PlaybackViewModel

/** Shared by the full player and the home dock; only the visible presentation owns this host. */
@Composable fun PlayerSheetsHost(sheets: PlayerSheetsState, state: PlaybackState,
    song: Song?, playback: PlaybackViewModel, progress: Pair<Long, Long>,
    playlists: List<StonePlaylist>, onCreatePlaylist: (String) -> Unit,
    onAddToPlaylist: (String, Song) -> Unit, onDelete: (Song) -> Unit,
    onMetadataChanged: () -> Unit, onEditComplete: () -> Unit = {}) {
    val context = LocalContext.current
    if (sheets.queueVisible) QueueSheet(state, playback) { sheets.queueVisible = false }
    if (sheets.lyricsVisible && song != null) LyricsSheet(song, state, playback, progress) { sheets.lyricsVisible = false }
    if (sheets.optionsVisible) PlayerOptionsSheet(state, song, { sheets.optionsVisible = false }) { selected ->
        when (selected) {
            PlayerTool.Share -> song?.let { runCatching { SongActions.share(context, it.uri, it.title) }
                .onFailure { error -> Toast.makeText(context, error.message, Toast.LENGTH_LONG).show() } }
            PlayerTool.FloatingWindow -> enterVideoPictureInPicture(context, playback.player())
            else -> sheets.tool = selected
        }
    }
    val close = { sheets.tool = null }
    val edited = { sheets.tool = null; onMetadataChanged(); onEditComplete() }
    when (sheets.tool) {
        PlayerTool.Speed -> SpeedSheet(state, playback, close)
        PlayerTool.Equalizer -> EqualizerSheet(state, playback, progress, song = song, close = close)
        PlayerTool.Cover -> song?.let { TagEditorSheet(it, initialTab = 0, close = close, saved = edited) }
        PlayerTool.Sleep -> SleepSheet(state, playback, close)
        PlayerTool.Tags -> song?.let { TagEditorSheet(it, close = close, saved = edited) }
        PlayerTool.Ringtone -> song?.let { RingtoneSheet(it, close) }
        PlayerTool.Playlist -> song?.let { PlaylistPickerSheet(it, playlists, onCreatePlaylist, onAddToPlaylist, close) }
        PlayerTool.Delete -> ConfirmSongAction({
            song?.let { playback.remove(it.id); onDelete(it) }; close(); onEditComplete()
        }, close)
        else -> Unit
    }
}
