package com.zirakusa.stone.nativeapp.feature.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zirakusa.stone.core.Song
import com.zirakusa.stone.nativeapp.R
import com.zirakusa.stone.nativeapp.playback.PlaybackState
import com.zirakusa.stone.nativeapp.playback.PlaybackViewModel
import com.zirakusa.stone.nativeapp.ui.components.SleepTimerIndicator
import com.zirakusa.stone.nativeapp.ui.theme.StonePalette

private val Glass = Color(0x99121116)

/** Top bar: back + title in a pill, then speed and ⋮ as round buttons. */
@Composable internal fun VideoTopBar(song: Song, state: PlaybackState, onBack: () -> Unit,
    onSpeed: () -> Unit, onOptions: (() -> Unit)?, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.displayCutout).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.weight(1f).height(52.dp).background(Glass, CircleShape).padding(end = 18.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBackIosNew, stringResource(R.string.close_player), tint = Color.White) }
            Text(song.title, Modifier.weight(1f), color = Color.White, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            SleepTimerIndicator(state.sleepMinutes, endAtMillis = state.sleepEndAtMs, showLabel = false)
        }
        RoundButton(onSpeed, stringResource(R.string.speed)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Speed, null, tint = Color.White, modifier = Modifier.size(20.dp))
                Text(speedLabel(state.speed), color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (onOptions != null) RoundButton(onOptions, stringResource(R.string.more_options)) {
            Icon(Icons.Default.MoreVert, null, tint = Color.White)
        }
    }
}

/**
 * Bottom controls. Portrait: action pill, timeline, transport row. Landscape: timeline,
 * then actions | transport | rotate on one row.
 */
@Composable internal fun VideoBottomControls(song: Song, state: PlaybackState, progress: Pair<Long, Long>,
    playback: PlaybackViewModel, landscape: Boolean, controls: VideoControlSettings,
    onAudioOnly: () -> Unit, onQueue: (() -> Unit)?, onScaleToggle: () -> Unit,
    modifier: Modifier = Modifier) {
    val rotate = rememberRotateAction()
    val actions = controls.visibleIds
    Column(modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.displayCutout)
        .padding(horizontal = 16.dp, vertical = 10.dp)) {
        if (!landscape) {
            VideoControlRow(actions, Modifier.background(Glass, RoundedCornerShape(28.dp)).padding(vertical = 4.dp),
                onAudioOnly = onAudioOnly, onQueue = onQueue, onScaleToggle = onScaleToggle, onRotate = rotate,
                playback = playback, state = state)
            Spacer(Modifier.height(10.dp))
            VideoTimeline(song, progress, playback)
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically) { Transport(state, playback) }
        } else {
            VideoTimeline(song, progress, playback)
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                val sideActions = actions.filter { it != VideoControlId.Rotate }
                VideoControlRow(sideActions, Modifier.weight(1f), onAudioOnly = onAudioOnly, onQueue = onQueue,
                    onScaleToggle = onScaleToggle, arrangement = Arrangement.Start, playback = playback, state = state)
                Transport(state, playback)
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    if (VideoControlId.Rotate in actions) RoundButton(rotate, stringResource(R.string.video_rotate)) {
                        Icon(Icons.Default.ScreenRotation, null, tint = Color.White)
                    }
                }
            }
        }
    }
}

@Composable private fun Transport(state: PlaybackState, playback: PlaybackViewModel) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        RoundButton(playback::previous, stringResource(R.string.previous)) {
            Icon(Icons.Default.SkipPrevious, null, tint = Color.White, modifier = Modifier.size(28.dp))
        }
        FilledIconButton(onClick = playback::toggle, modifier = Modifier.size(60.dp),
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = StonePalette.Yellow,
                contentColor = StonePalette.Background)) {
            Icon(if (state.playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                stringResource(if (state.playing) R.string.pause else R.string.play), modifier = Modifier.size(34.dp))
        }
        RoundButton(playback::next, stringResource(R.string.next)) {
            Icon(Icons.Default.SkipNext, null, tint = Color.White, modifier = Modifier.size(28.dp))
        }
    }
}

@Composable private fun RoundButton(onClick: () -> Unit, label: String, content: @Composable () -> Unit) {
    FilledIconButton(onClick = onClick, modifier = Modifier.size(52.dp),
        colors = IconButtonDefaults.filledIconButtonColors(containerColor = Glass)) {
        Box(Modifier.semantics { contentDescription = label }, contentAlignment = Alignment.Center) { content() }
    }
}

private fun speedLabel(speed: Float) = if (speed % 1f == 0f) "${speed.toInt()}.0x" else "${speed}x"

/** Toggles portrait/landscape; the activity returns to sensor rotation when the player closes. */
@Composable private fun rememberRotateAction(): () -> Unit {
    val activity = LocalContext.current.findActivity()
    DisposableEffect(activity) {
        onDispose { activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }
    return {
        activity?.let {
            val landscape = it.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
            it.requestedOrientation = if (landscape) ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
