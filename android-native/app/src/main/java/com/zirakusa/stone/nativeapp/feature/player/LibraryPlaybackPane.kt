package com.zirakusa.stone.nativeapp.feature.player

import androidx.compose.runtime.Composable
import com.zirakusa.stone.core.Song
import com.zirakusa.stone.nativeapp.data.library.StonePlaylist
import com.zirakusa.stone.nativeapp.playback.PlaybackState
import com.zirakusa.stone.nativeapp.playback.PlaybackViewModel

@Composable fun LibraryPlaybackPane(showPanel: Boolean, state: PlaybackState, song: Song?,
    playback: PlaybackViewModel, sheets: PlayerSheetsState, onOpen: (() -> Unit)?,
    playlists: List<StonePlaylist>, onCreatePlaylist: (String) -> Unit,
    onAddToPlaylist: (String, Song) -> Unit, onDelete: (Song) -> Unit,
    onMetadataChanged: () -> Unit, optionsAsSidePanel: Boolean = false) {
    val progress = if ((showPanel && state.queue.isNotEmpty()) || sheets.visible)
        rememberPlayerProgress(state, playback) else 0L to 0L
    if (showPanel) LandscapePlayerPanel(state, song, progress, CompactPlayerActions(
        playback::toggle, playback::previous, playback::next, playback::shuffle,
        playback::repeat, { playback.toggleFavorite() }, playback::seek,
        { sheets.optionsSidePanel = optionsAsSidePanel; sheets.optionsVisible = true },
        { sheets.lyricsVisible = true },
        { sheets.queueVisible = true }, onOpen
    ))
    PlayerSheetsHost(sheets, state, song, playback, progress, playlists, onCreatePlaylist,
        onAddToPlaylist, onDelete, onMetadataChanged)
}
