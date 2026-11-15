@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.zirakusa.stone.nativeapp.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import com.zirakusa.stone.core.Song
import com.zirakusa.stone.nativeapp.R
import com.zirakusa.stone.nativeapp.feature.library.components.formatVideoDuration
import com.zirakusa.stone.nativeapp.playback.PlaybackState
import com.zirakusa.stone.nativeapp.playback.PlaybackViewModel
import com.zirakusa.stone.nativeapp.ui.ImmersiveWindowEffect
import com.zirakusa.stone.nativeapp.ui.theme.StonePalette

private val PanelColor = Color(0xFF0E0D12)

/**
 * Video ⋮ menu. Portrait: bottom sheet. Landscape: right side panel so the video stays
 * visible on the left. Items: floating window, scale, repeat, subtitles, audio track,
 * playlist, information (tag editor) and delete.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun VideoOptionsSheet(state: PlaybackState, song: Song, playback: PlaybackViewModel,
    landscape: Boolean, videoScale: VideoScale?, onVideoScaleChange: (VideoScale) -> Unit,
    onSelect: (PlayerTool) -> Unit, onClose: () -> Unit) {
    val player = playback.player()
    var picker by remember { mutableStateOf<Int?>(null) }
    val content: @Composable () -> Unit = {
        VideoOptionsContent(state, song, player, videoScale, onVideoScaleChange, onSelect,
            onPickTrack = { picker = it })
    }
    if (landscape) {
        Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false)) {
            ImmersiveWindowEffect()
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxHeight().clickable(
                    interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
                    contentAlignment = Alignment.CenterEnd) {
                    Icon(Icons.Default.ChevronRight, null, tint = Color(0x99FFFFFF), modifier = Modifier.size(40.dp))
                }
                Box(Modifier.fillMaxHeight().widthIn(min = 300.dp, max = 400.dp).fillMaxWidth(.40f).background(PanelColor)
                    .windowInsetsPadding(WindowInsets.displayCutout)) { content() }
            }
        }
    } else {
        ModalBottomSheet(onDismissRequest = onClose, containerColor = PanelColor) {
            ImmersiveWindowEffect()
            content()
        }
    }
    picker?.let { type ->
        TrackPickerDialog(player, type) { picker = null }
    }
}

@Composable private fun VideoOptionsContent(state: PlaybackState, song: Song, player: Player?,
    videoScale: VideoScale?, onVideoScaleChange: (VideoScale) -> Unit, onSelect: (PlayerTool) -> Unit,
    onPickTrack: (Int) -> Unit) {
    val height = player?.videoSize?.height ?: 0
    val tracks = player?.currentTracks ?: Tracks.EMPTY
    LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(start = 24.dp, end = 16.dp, bottom = 28.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(song.title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 2,
                        overflow = TextOverflow.Ellipsis, color = Color.White)
                    Text(listOfNotNull(height.takeIf { it > 0 }?.let { "${it}P" },
                        formatVideoDuration(song.durationMs)).joinToString(" | "),
                        color = StonePalette.Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                }
                IconButton(onClick = { onSelect(PlayerTool.Share) }) {
                    Icon(Icons.Default.Share, stringResource(R.string.share), tint = Color.White)
                }
            }
            HorizontalDivider(color = StonePalette.Border)
        }
        item { OptionRow(Icons.Default.PictureInPictureAlt, stringResource(R.string.floating_window)) { onSelect(PlayerTool.FloatingWindow) } }
        if (videoScale != null) item {
            OptionRow(Icons.Default.AspectRatio, stringResource(R.string.video_scale_toggle), scaleLabel(videoScale)) {
                onVideoScaleChange(videoScale.next())
            }
        }
        item {
            OptionRow(Icons.Default.Repeat, stringResource(R.string.play_mode), stringResource(when (state.repeat) {
                Player.REPEAT_MODE_ONE -> R.string.repeat_one
                Player.REPEAT_MODE_ALL -> R.string.repeat_all
                else -> R.string.repeat_off
            })) { player?.let { it.repeatMode = (it.repeatMode + 1) % 3 } }
        }
        item {
            OptionRow(Icons.Default.ClosedCaption, stringResource(R.string.subtitles),
                selectedTrackLabel(tracks, C.TRACK_TYPE_TEXT) ?: stringResource(R.string.track_off)) { onPickTrack(C.TRACK_TYPE_TEXT) }
        }
        item {
            OptionRow(Icons.Default.GraphicEq, stringResource(R.string.audio_track),
                selectedTrackLabel(tracks, C.TRACK_TYPE_AUDIO) ?: "—") { onPickTrack(C.TRACK_TYPE_AUDIO) }
        }
        item { HorizontalDivider(Modifier.padding(vertical = 6.dp), color = StonePalette.Border) }
        item { OptionRow(Icons.Default.PlaylistAdd, stringResource(R.string.add_to_playlist)) { onSelect(PlayerTool.Playlist) } }
        item { OptionRow(Icons.Default.Edit, stringResource(R.string.information)) { onSelect(PlayerTool.Tags) } }
        item { OptionRow(Icons.Default.Delete, stringResource(R.string.delete)) { onSelect(PlayerTool.Delete) } }
    }
}

@Composable private fun OptionRow(icon: ImageVector, label: String, value: String? = null, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Color(0xFFCFCBD6), modifier = Modifier.size(24.dp))
        Text(label, Modifier.weight(1f).padding(horizontal = 18.dp), color = Color.White, fontSize = 16.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        value?.let { Text(it, color = StonePalette.Muted, fontSize = 14.sp, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 160.dp)) }
    }
}

@Composable private fun scaleLabel(scale: VideoScale) = stringResource(when (scale) {
    VideoScale.AUTO -> R.string.video_scale_short_auto
    VideoScale.FIT -> R.string.video_scale_short_fit
    VideoScale.ZOOM -> R.string.video_scale_short_zoom
    VideoScale.FILL -> R.string.video_scale_short_fill
})

private fun trackLabel(group: Tracks.Group, index: Int, position: Int): String {
    val format = group.getTrackFormat(index)
    val language = format.language?.takeIf { it.isNotBlank() && it != "und" }
    return format.label ?: language?.let { java.util.Locale.forLanguageTag(it).displayLanguage.ifBlank { it } }
        ?: "Pista $position"
}

private fun selectedTrackLabel(tracks: Tracks, type: Int): String? {
    var position = 0
    tracks.groups.filter { it.type == type }.forEach { group ->
        for (index in 0 until group.length) {
            position++
            if (group.isTrackSelected(index)) return trackLabel(group, index, position)
        }
    }
    return null
}

/** Lists the tracks of [type]; subtitles also offer "off". */
@Composable private fun TrackPickerDialog(player: Player?, type: Int, close: () -> Unit) {
    val groups = player?.currentTracks?.groups?.filter { it.type == type }.orEmpty()
    AlertDialog(onDismissRequest = close, containerColor = PanelColor,
        title = { ImmersiveWindowEffect(); Text(stringResource(if (type == C.TRACK_TYPE_TEXT) R.string.subtitles else R.string.audio_track)) },
        text = {
            Column {
                if (type == C.TRACK_TYPE_TEXT) TextButton(onClick = {
                    player?.let { it.trackSelectionParameters = it.trackSelectionParameters.buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build() }
                    close()
                }) { Text(stringResource(R.string.track_off)) }
                if (groups.isEmpty()) Text(stringResource(R.string.track_none), color = StonePalette.Muted)
                var position = 0
                groups.forEach { group ->
                    for (index in 0 until group.length) {
                        position++
                        if (!group.isTrackSupported(index)) continue
                        val label = trackLabel(group, index, position)
                        TextButton(onClick = {
                            player?.let { it.trackSelectionParameters = it.trackSelectionParameters.buildUpon()
                                .setTrackTypeDisabled(type, false)
                                .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, index)).build() }
                            close()
                        }) {
                            Text((if (group.isTrackSelected(index)) "✓ " else "") + label,
                                color = if (group.isTrackSelected(index)) StonePalette.Yellow else Color.White)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = close) { Text(stringResource(R.string.cancel)) } })
}
