package com.zirakusa.stone.nativeapp.feature.player

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.zirakusa.stone.nativeapp.R
import com.zirakusa.stone.nativeapp.playback.PlaybackState
import com.zirakusa.stone.nativeapp.ui.theme.StonePalette

data class CompactPlayerActions(
    val toggle: () -> Unit, val previous: () -> Unit, val next: () -> Unit,
    val shuffle: () -> Unit, val repeat: () -> Unit, val favorite: () -> Unit,
    val seek: (Long) -> Unit, val options: () -> Unit, val lyrics: () -> Unit,
    val queue: () -> Unit, val open: (() -> Unit)? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun CompactPlayerTimeline(progress: Pair<Long, Long>, seek: (Long) -> Unit) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val end = progress.second.coerceAtLeast(0)
    val label = stringResource(R.string.seek)
    Column {
        Slider(value = dragging ?: progress.first.coerceIn(0, end).toFloat(),
            onValueChange = { dragging = it }, valueRange = 0f..end.coerceAtLeast(1).toFloat(),
            enabled = end > 0, onValueChangeFinished = {
                dragging?.let { seek(it.toLong().coerceIn(0, end)) }; dragging = null
            }, modifier = Modifier.fillMaxWidth().height(40.dp).semantics { contentDescription = label },
            thumb = { Box(Modifier.size(12.dp).background(StonePalette.Yellow, CircleShape)) },
            track = { SliderDefaults.Track(it, modifier = Modifier.height(4.dp),
                thumbTrackGapSize = 0.dp, drawStopIndicator = null) })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(duration(progress.first.coerceAtLeast(0)), style = MaterialTheme.typography.labelSmall)
            Text(duration(end), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable fun CompactPlayerControls(state: PlaybackState, actions: CompactPlayerActions, wide: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically) {
            if (wide) ShuffleControl(state, actions)
            IconButton(onClick = actions.previous, enabled = state.ready) {
                Icon(Icons.Default.SkipPrevious, stringResource(R.string.previous))
            }
            FilledIconButton(onClick = actions.toggle, enabled = state.ready, modifier = Modifier.size(56.dp)) {
                Icon(if (state.playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                    stringResource(if (state.playing) R.string.pause else R.string.play), Modifier.size(32.dp))
            }
            IconButton(onClick = actions.next, enabled = state.ready) {
                Icon(Icons.Default.SkipNext, stringResource(R.string.next))
            }
            if (wide) RepeatControl(state, actions)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            if (!wide) ShuffleControl(state, actions)
            IconButton(onClick = actions.lyrics) { Icon(Icons.Default.ChatBubbleOutline, stringResource(R.string.lyrics)) }
            IconButton(onClick = actions.queue) { Icon(Icons.Default.QueueMusic, stringResource(R.string.queue)) }
            if (!wide) RepeatControl(state, actions)
        }
    }
}

@Composable private fun ShuffleControl(state: PlaybackState, actions: CompactPlayerActions) {
    IconToggleButton(checked = state.shuffle, onCheckedChange = { actions.shuffle() }, enabled = state.ready) {
        Icon(Icons.Default.Shuffle, stringResource(R.string.shuffle),
            tint = if (state.shuffle) StonePalette.Yellow else StonePalette.Secondary)
    }
}

@Composable private fun RepeatControl(state: PlaybackState, actions: CompactPlayerActions) {
    IconButton(onClick = actions.repeat, enabled = state.ready) {
        Icon(if (state.repeat == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
            stringResource(R.string.repeat), tint = if (state.repeat != Player.REPEAT_MODE_OFF) StonePalette.Yellow else StonePalette.Secondary)
    }
}
