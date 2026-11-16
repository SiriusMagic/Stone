@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.zirakusa.stone.nativeapp.feature.player

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.Player
import com.zirakusa.stone.nativeapp.playback.PlaybackState
import com.zirakusa.stone.nativeapp.playback.PlaybackViewModel
import com.zirakusa.stone.nativeapp.playback.StonePlaybackService
import com.zirakusa.stone.nativeapp.ui.theme.StonePalette

/**
 * Renders the user-configurable secondary video actions in their saved order. The first
 * actions are handled by the screen (callbacks); seek, repeat, subtitles, floating window and
 * mute act on [playback] directly and are shown only where it is given.
 */
@Composable internal fun VideoControlRow(
    controls: List<VideoControlId>,
    modifier: Modifier = Modifier,
    onAudioOnly: (() -> Unit)? = null,
    onOptions: (() -> Unit)? = null,
    onQueue: (() -> Unit)? = null,
    onScaleToggle: (() -> Unit)? = null,
    onRotate: (() -> Unit)? = null,
    arrangement: Arrangement.Horizontal = Arrangement.SpaceEvenly,
    playback: PlaybackViewModel? = null,
    state: PlaybackState? = null,
    /** Round dark buttons (portrait carousel) instead of bare icons. */
    circle: Boolean = false,
    /**
     * False when the caller already scrolls this row (portrait carousel): a scrolling row inside
     * another one is measured with infinite width and crashes the app.
     */
    scrollable: Boolean = true,
) {
    val context = LocalContext.current
    val player = playback?.player()
    // Subtitle selection is not part of PlaybackState; bump to redraw after toggling it.
    var subtitleRevision by remember { mutableIntStateOf(0) }
    var volumeBeforeMute by remember { mutableIntStateOf(0) }
    val subtitlesOff = remember(subtitleRevision, player) {
        player?.trackSelectionParameters?.disabledTrackTypes?.contains(C.TRACK_TYPE_TEXT) == true
    }
    val muted = state?.volumePercent == 0
    Row(
        if (scrollable) modifier.fillMaxWidth().horizontalScroll(rememberScrollState()) else modifier,
        horizontalArrangement = arrangement,
    ) {
        controls.forEach { id ->
            val definition = VideoControlRegistry.definition(id)
            val action: (() -> Unit)? = when (id) {
                VideoControlId.AudioOnly -> onAudioOnly
                VideoControlId.Options -> onOptions
                VideoControlId.Queue -> onQueue
                VideoControlId.Scale -> onScaleToggle
                VideoControlId.Rotate -> onRotate
                VideoControlId.Rewind -> playback?.let { { it.seek(it.progress().first - 10_000L) } }
                VideoControlId.Forward -> playback?.let { { it.seek(it.progress().first + 10_000L) } }
                VideoControlId.Repeat -> player?.let { { it.repeatMode = (it.repeatMode + 1) % 3 } }
                VideoControlId.Subtitles -> player?.let { target -> {
                    target.trackSelectionParameters = target.trackSelectionParameters.buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !subtitlesOff).build()
                    subtitleRevision++
                } }
                VideoControlId.FloatingWindow -> player?.let { { enterVideoPictureInPicture(context, it) } }
                VideoControlId.Mute -> playback?.let { {
                    if (muted) StonePlaybackService.setVolumePercent(volumeBeforeMute.takeIf { it > 0 } ?: 30)
                    else { volumeBeforeMute = state?.volumePercent ?: 0; StonePlaybackService.setVolumePercent(0) }
                } }
            }
            val active = when (id) {
                VideoControlId.Repeat -> state != null && state.repeat != Player.REPEAT_MODE_OFF
                VideoControlId.Subtitles -> player != null && !subtitlesOff &&
                    player.currentTracks.groups.any { it.type == C.TRACK_TYPE_TEXT }
                VideoControlId.Mute -> muted
                else -> false
            }
            val icon = if (id == VideoControlId.Repeat && state?.repeat == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne
                else definition.icon
            action?.let {
                if (circle) FilledIconButton(onClick = it, modifier = Modifier.padding(horizontal = 6.dp).size(52.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White.copy(alpha = .14f))) {
                    Icon(icon, stringResource(definition.label), tint = if (active) StonePalette.Yellow else Color.White)
                } else IconButton(onClick = it, modifier = Modifier.size(48.dp)) {
                    Icon(icon, stringResource(definition.label), tint = if (active) StonePalette.Yellow else Color.White)
                }
            }
        }
    }
}
