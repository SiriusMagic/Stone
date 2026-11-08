package com.zirakusa.stone.nativeapp.feature.player

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.zirakusa.stone.nativeapp.R
import com.zirakusa.stone.nativeapp.playback.PlaybackState
import com.zirakusa.stone.nativeapp.playback.PlaybackViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable fun rememberPlayerProgress(state: PlaybackState, playback: PlaybackViewModel): Pair<Long, Long> {
    var progress by remember { mutableStateOf(0L to 0L) }
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner, state.playing, state.currentId) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                progress = playback.progress()
                if (!state.playing) break
                // A quarter-second cadence follows timed lyrics smoothly while
                // keeping a single lightweight UI coroutine.
                delay(250)
            }
        }
    }
    return progress
}

@Composable fun PlayerProgress(progress: Pair<Long, Long>, playback: PlaybackViewModel,
    onVerticalDrag: ((Float) -> Unit)? = null) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val label = stringResource(R.string.seek)
    val verticalGesture = if (onVerticalDrag == null) Modifier else Modifier.pointerInput(onVerticalDrag) {
        detectVerticalDragGestures { change, dragAmount ->
            change.consume()
            onVerticalDrag(dragAmount)
        }
    }
    Slider(value = dragging ?: progress.first.toFloat().coerceAtMost(progress.second.toFloat()),
        onValueChange = { dragging = it }, valueRange = 0f..progress.second.toFloat().coerceAtLeast(1f),
        enabled = progress.second > 0,
        onValueChangeFinished = {
            dragging?.let { playback.seek(it.toLong()) }
            dragging = null
        }, modifier = verticalGesture.semantics { contentDescription = label })
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(duration(progress.first)); Text(duration(progress.second))
    }
}

fun duration(ms: Long): String = "%d:%02d".format(ms / 60000, (ms / 1000) % 60)
