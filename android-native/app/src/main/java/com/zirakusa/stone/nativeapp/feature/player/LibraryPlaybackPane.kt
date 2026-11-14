package com.zirakusa.stone.nativeapp.feature.player

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.runtime.Composable
import com.zirakusa.stone.nativeapp.R
import com.zirakusa.stone.core.Song
import com.zirakusa.stone.nativeapp.data.library.StonePlaylist
import com.zirakusa.stone.nativeapp.playback.PlaybackState
import com.zirakusa.stone.nativeapp.playback.PlaybackViewModel

@Composable fun LibraryPlaybackPane(showPanel: Boolean, state: PlaybackState, song: Song?,
    playback: PlaybackViewModel, sheets: PlayerSheetsState, onOpen: (() -> Unit)?,
    playlists: List<StonePlaylist>, onCreatePlaylist: (String) -> Unit,
    onAddToPlaylist: (String, Song) -> Unit, onDelete: (Song) -> Unit,
    onMetadataChanged: () -> Unit, optionsAsSidePanel: Boolean = false,
    videoTab: Boolean = false, videoPreviewSong: Song? = null,
    videoControls: VideoControlSettings = VideoControlSettings.defaults(),
    onSelectVideo: (Song) -> Unit = {}, onAudioOnly: (Song) -> Unit = {},
    videoScale: VideoScale = VideoScale.AUTO,
    onVideoScaleChange: (VideoScale) -> Unit = {}) {
    val progress = if ((showPanel && state.queue.isNotEmpty()) || sheets.visible)
        rememberPlayerProgress(state, playback) else 0L to 0L
    val videoMode = videoTab || song?.isVideo == true
    val previewSong = if (videoTab) videoPreviewSong else song
    val optionsTarget = sheets.optionsSong ?: song
    val optionExtras = if (optionsTarget?.isVideo == true) listOf(
        PlayerOptionExtra(Icons.Default.Headphones, R.string.audio_only) {
            val target = sheets.optionsSong ?: song
            sheets.optionsVisible = false
            sheets.optionsSong = null
            target?.let(onAudioOnly)
        }
    ) else emptyList()
    val activeVideo = previewSong?.isVideo == true && song?.isVideo == true &&
        previewSong.id == song.id && state.currentId == song.id
    if (showPanel && videoMode) {
        LandscapeVideoPreviewPanel(
            state = state,
            song = previewSong,
            active = activeVideo,
            progress = progress,
            playback = playback,
            videoScale = videoScale,
            controls = videoControls,
            onSelectVideo = onSelectVideo,
            onOpenFullscreen = { onOpen?.invoke() },
            onAudioOnly = onAudioOnly,
            onOptions = {
                sheets.optionsSong = null
                sheets.optionsSidePanel = optionsAsSidePanel
                sheets.optionsVisible = true
            },
            onQueue = { sheets.queueVisible = true },
            onScaleToggle = {
                onVideoScaleChange(videoScale.next())
            },
        )
    } else if (showPanel) LandscapePlayerPanel(state, song, progress, CompactPlayerActions(
            playback::toggle, playback::previous, playback::next, playback::shuffle,
            playback::repeat, { playback.toggleFavorite() }, playback::seek,
            {
                sheets.optionsSong = null
                sheets.optionsSidePanel = optionsAsSidePanel
                sheets.optionsVisible = true
            },
            { sheets.lyricsVisible = true },
            { sheets.queueVisible = true }, onOpen
        ))
    PlayerSheetsHost(sheets, state, song, playback, progress, playlists, onCreatePlaylist,
        onAddToPlaylist, onDelete, onMetadataChanged, extraActions = optionExtras)
}
