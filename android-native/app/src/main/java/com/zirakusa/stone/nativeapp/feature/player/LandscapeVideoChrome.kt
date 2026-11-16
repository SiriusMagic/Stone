package com.zirakusa.stone.nativeapp.feature.player

import android.content.pm.ActivityInfo
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.C
import androidx.media3.common.Player
import com.zirakusa.stone.core.Song
import com.zirakusa.stone.nativeapp.R
import com.zirakusa.stone.nativeapp.playback.PlaybackState
import com.zirakusa.stone.nativeapp.playback.PlaybackViewModel
import com.zirakusa.stone.nativeapp.playback.StonePlaybackService
import com.zirakusa.stone.nativeapp.ui.theme.StonePalette

/** Which right-hand panel is open over the landscape video. */
internal enum class VideoPanel { None, Menu, List }

/**
 * Landscape video controls: title bar (HD, subtitles, list, menu), labelled quick actions on both
 * sides (Girar, Tamaño | Repetir, Segundo plano, Mudo), timeline and the transport row with
 * lock, ±10 s, speed and floating window. Owns the lock, night mode and side-panel state.
 */
@android.annotation.SuppressLint("SourceLockedOrientationActivity")
@Composable internal fun LandscapeVideoChrome(song: Song, state: PlaybackState, progress: Pair<Long, Long>,
    playback: PlaybackViewModel, visible: Boolean, onBack: () -> Unit, onAudioOnly: () -> Unit,
    onScaleToggle: () -> Unit, onTool: (PlayerTool) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val player = playback.player()
    var locked by remember { mutableStateOf(false) }
    var night by rememberSaveableNight()
    var panel by remember { mutableStateOf(VideoPanel.None) }
    var subtitleRevision by remember { mutableIntStateOf(0) }
    var volumeBeforeMute by remember { mutableIntStateOf(0) }
    val subtitlesOff = remember(subtitleRevision, player) {
        player?.trackSelectionParameters?.disabledTrackTypes?.contains(C.TRACK_TYPE_TEXT) == true
    }
    val hasSubtitles = player?.currentTracks?.groups?.any { it.type == C.TRACK_TYPE_TEXT } == true
    val hd = (player?.videoSize?.height ?: 0) >= 720
    val muted = state.volumePercent == 0
    val activity = remember(context) { context.findActivityOrNull() }

    Box(modifier.fillMaxSize()) {
        // Night mode: a dark veil over the picture (controls stay bright).
        if (night) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .45f)))
        if (locked) {
            // Locked: touches go nowhere except the padlock.
            Box(Modifier.fillMaxSize().clickable(remember { MutableInteractionSource() }, null) {})
            IconButton(onClick = { locked = false }, Modifier.align(Alignment.CenterStart).padding(start = 32.dp)
                .background(Color(0x66000000), CircleShape)) {
                Icon(Icons.Default.Lock, stringResource(R.string.video_unlock), tint = Color.White)
            }
            return@Box
        }
        AnimatedVisibility(visible, Modifier.matchParentSize()) {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color(0x99000000), .25f to Color.Transparent,
                .7f to Color.Transparent, 1f to Color(0xCC000000))).windowInsetsPadding(WindowInsets.displayCutout)) {
                Row(Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBackIosNew, stringResource(R.string.close_player), tint = Color.White) }
                    Text(song.title, Modifier.weight(1f).padding(start = 4.dp), color = Color.White, fontSize = 18.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (hd) Text("HD", Modifier.padding(horizontal = 8.dp).border(2.dp, Color.White, RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 1.dp), color = Color.White, fontWeight = FontWeight.Black, fontSize = 13.sp)
                    if (hasSubtitles) IconButton(onClick = {
                        player?.let { it.trackSelectionParameters = it.trackSelectionParameters.buildUpon()
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !subtitlesOff).build() }
                        subtitleRevision++
                    }) { Icon(if (subtitlesOff) Icons.Default.SubtitlesOff else Icons.Default.Subtitles, stringResource(R.string.video_subtitles), tint = Color.White) }
                    IconButton(onClick = { panel = VideoPanel.List }) {
                        Icon(Icons.AutoMirrored.Filled.PlaylistPlay, stringResource(R.string.queue), tint = Color.White)
                    }
                    IconButton(onClick = { panel = VideoPanel.Menu }) { Icon(Icons.Default.MoreVert, stringResource(R.string.more_options), tint = Color.White) }
                }
                Column(Modifier.align(Alignment.CenterStart).padding(start = 24.dp)) {
                    SideAction(Icons.Default.ScreenRotation, R.string.video_rotate, labelFirst = false) {
                        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                    }
                    SideAction(Icons.Default.AspectRatio, R.string.video_size, labelFirst = false, onClick = onScaleToggle)
                }
                Column(Modifier.align(Alignment.CenterEnd).padding(end = 24.dp), horizontalAlignment = Alignment.End) {
                    SideAction(if (state.repeat == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                        R.string.repeat, active = state.repeat != Player.REPEAT_MODE_OFF) {
                        playback.setRepeat(if (state.repeat == Player.REPEAT_MODE_ONE) Player.REPEAT_MODE_OFF else Player.REPEAT_MODE_ONE)
                    }
                    SideAction(Icons.Default.Headphones, R.string.video_background_play, onClick = onAudioOnly)
                    SideAction(if (muted) Icons.Default.VolumeOff else Icons.Default.VolumeMute, R.string.video_mute, active = muted) {
                        if (muted) StonePlaybackService.setVolumePercent(volumeBeforeMute.takeIf { it > 0 } ?: 30)
                        else { volumeBeforeMute = state.volumePercent; StonePlaybackService.setVolumePercent(0) }
                    }
                }
                Column(Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(horizontal = 20.dp, vertical = 8.dp)) {
                    VideoTimeline(song, progress, playback)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { locked = true; panel = VideoPanel.None }) {
                            Icon(Icons.Default.LockOpen, stringResource(R.string.video_lock), tint = Color.White)
                        }
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = playback::previous) { Icon(Icons.Default.SkipPrevious, stringResource(R.string.previous), Modifier.size(34.dp), tint = Color.White) }
                        IconButton(onClick = { seekVideoBy(playback, progress, -10_000L) }) { Icon(Icons.Default.Replay10, null, Modifier.size(30.dp), tint = Color.White) }
                        IconButton(onClick = playback::toggle, Modifier.padding(horizontal = 14.dp).size(64.dp).border(3.dp, Color.White, CircleShape)) {
                            Icon(if (state.playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                                stringResource(if (state.playing) R.string.pause else R.string.play), Modifier.size(38.dp), tint = Color.White)
                        }
                        IconButton(onClick = { seekVideoBy(playback, progress, 10_000L) }) { Icon(Icons.Default.Forward10, null, Modifier.size(30.dp), tint = Color.White) }
                        IconButton(onClick = playback::next) { Icon(Icons.Default.SkipNext, stringResource(R.string.next), Modifier.size(34.dp), tint = Color.White) }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { onTool(PlayerTool.Speed) }) {
                            Text(speedText(state.speed), color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp)
                        }
                        IconButton(onClick = { enterVideoPictureInPicture(context, player) }) {
                            Icon(Icons.Default.PictureInPictureAlt, stringResource(R.string.video_floating_window), tint = Color.White)
                        }
                    }
                }
            }
        }
        AnimatedVisibility(panel != VideoPanel.None, Modifier.align(Alignment.CenterEnd),
            enter = slideInHorizontally { it }, exit = slideOutHorizontally { it }) {
            when (panel) {
                VideoPanel.List -> VideoListPanel(state, playback, onClose = { panel = VideoPanel.None })
                else -> VideoMenuPanel(song, state, playback, night, onNight = { night = it },
                    onTool = { panel = VideoPanel.None; onTool(it) }, onAudioOnly = { panel = VideoPanel.None; onAudioOnly() },
                    onClose = { panel = VideoPanel.None })
            }
        }
    }
}

/** Night mode survives rotation within the session. */
@Composable private fun rememberSaveableNight() = androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }

/** Icon + label, as in the reference: label after the icon on the left side, before it on the right. */
@Composable private fun SideAction(icon: ImageVector, label: Int, active: Boolean = false, labelFirst: Boolean = true,
    onClick: () -> Unit) {
    val tint = if (active) StonePalette.Yellow else Color.White
    Row(Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        if (labelFirst) Text(stringResource(label), Modifier.padding(end = 12.dp), color = tint, fontSize = 16.sp)
        Icon(icon, null, tint = tint, modifier = Modifier.size(28.dp))
        if (!labelFirst) Text(stringResource(label), Modifier.padding(start = 12.dp), color = tint, fontSize = 16.sp)
    }
}

private fun speedText(speed: Float) = if (speed % 1f == 0f) "${speed.toInt()}x" else "${speed}x"

private tailrec fun android.content.Context.findActivityOrNull(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivityOrNull()
    else -> null
}
