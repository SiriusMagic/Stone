package com.zirakusa.stone.nativeapp.feature.player

import android.content.pm.ActivityInfo
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zirakusa.stone.core.Song
import com.zirakusa.stone.nativeapp.R
import com.zirakusa.stone.nativeapp.playback.PlaybackState
import com.zirakusa.stone.nativeapp.playback.PlaybackViewModel
import com.zirakusa.stone.nativeapp.ui.theme.StonePalette

/**
 * Portrait video controls: title bar (list, ⋮), a carousel of round buttons (the ones chosen in
 * Ajustes › Vídeo, plus lock and night mode), the timeline with the speed pill, and
 * floating window | previous | play | next | full screen.
 */
@android.annotation.SuppressLint("SourceLockedOrientationActivity")
@Composable internal fun PortraitVideoChrome(song: Song, state: PlaybackState, progress: Pair<Long, Long>,
    playback: PlaybackViewModel, visible: Boolean, videoControls: VideoControlSettings, onBack: () -> Unit,
    onQueue: () -> Unit, onOptions: () -> Unit, onAudioOnly: () -> Unit, onScaleToggle: () -> Unit,
    onSpeed: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var locked by remember { mutableStateOf(false) }
    var night by rememberSaveable { mutableStateOf(false) }
    val activity = remember(context) { context.findActivity() }
    Box(modifier.fillMaxSize()) {
        if (night) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .45f)))
        if (locked) {
            Box(Modifier.fillMaxSize().clickable(remember { MutableInteractionSource() }, null) {})
            IconButton(onClick = { locked = false }, Modifier.align(Alignment.CenterStart).padding(start = 24.dp)
                .background(Color(0x66000000), CircleShape)) {
                Icon(Icons.Default.Lock, stringResource(R.string.video_unlock), tint = Color.White)
            }
            return@Box
        }
        AnimatedVisibility(visible, Modifier.matchParentSize()) {
            Box(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxWidth().align(Alignment.TopCenter).background(Brush.verticalGradient(
                    listOf(Color(0xCC000000), Color.Transparent))).windowInsetsPadding(WindowInsets.statusBars).padding(vertical = 8.dp)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBackIosNew, stringResource(R.string.close_player), tint = Color.White) }
                        Text(song.title, Modifier.weight(1f).basicMarquee(), color = Color.White, fontSize = 18.sp, maxLines = 1)
                        IconButton(onClick = onQueue) { Icon(Icons.AutoMirrored.Filled.PlaylistPlay, stringResource(R.string.queue), tint = Color.White) }
                        IconButton(onClick = onOptions) { Icon(Icons.Default.MoreVert, stringResource(R.string.more_options), tint = Color.White) }
                    }
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        val carousel = videoControls.visibleIds.filter { it != VideoControlId.Queue && it != VideoControlId.Options }
                        VideoControlRow(carousel, Modifier.wrapContentWidth(), onAudioOnly = onAudioOnly, onScaleToggle = onScaleToggle,
                            onRotate = { activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE },
                            arrangement = Arrangement.Start, playback = playback, state = state, circle = true)
                        CircleAction(Icons.Default.LockOpen, R.string.video_lock) { locked = true }
                        CircleAction(Icons.Default.Bedtime, R.string.video_night_mode, active = night) { night = !night }
                    }
                }
                Column(Modifier.fillMaxWidth().align(Alignment.BottomCenter).background(Brush.verticalGradient(
                    listOf(Color.Transparent, Color(0xCC000000)))).windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) { VideoTimeline(song, progress, playback) }
                        Text(speedPill(state.speed), Modifier.padding(start = 10.dp).border(2.dp, Color.White, RoundedCornerShape(16.dp))
                            .clickable(onClick = onSpeed).padding(horizontal = 10.dp, vertical = 4.dp), color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { enterVideoPictureInPicture(context, playback.player()) }) {
                            Icon(Icons.Default.PictureInPictureAlt, stringResource(R.string.video_floating_window), tint = Color.White)
                        }
                        IconButton(onClick = playback::previous) { Icon(Icons.Default.SkipPrevious, stringResource(R.string.previous), Modifier.size(34.dp), tint = Color.White) }
                        IconButton(onClick = playback::toggle, Modifier.size(68.dp).border(3.dp, Color.White, CircleShape)) {
                            Icon(if (state.playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                                stringResource(if (state.playing) R.string.pause else R.string.play), Modifier.size(40.dp), tint = Color.White)
                        }
                        IconButton(onClick = playback::next) { Icon(Icons.Default.SkipNext, stringResource(R.string.next), Modifier.size(34.dp), tint = Color.White) }
                        IconButton(onClick = { activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE }) {
                            Icon(Icons.Default.Fullscreen, stringResource(R.string.video_rotate), Modifier.size(30.dp), tint = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun CircleAction(icon: ImageVector, label: Int, active: Boolean = false, onClick: () -> Unit) {
    FilledIconButton(onClick = onClick, modifier = Modifier.padding(horizontal = 6.dp).size(52.dp),
        colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White.copy(alpha = .14f))) {
        Icon(icon, stringResource(label), tint = if (active) StonePalette.Yellow else Color.White)
    }
}

private fun speedPill(speed: Float) = "%.1fX".format(speed)

