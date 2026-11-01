package com.zirakusa.stone.nativeapp.feature.player

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
    LaunchedEffect(owner, state) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            do {
                progress = playback.progress()
                if (!state.playing) break
                delay(1000)
            } while (isActive)
        }
    }
    return progress
}

@Composable fun PlayerProgress(progress: Pair<Long, Long>, playback: PlaybackViewModel) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val label = stringResource(R.string.seek)
    Slider(value = dragging ?: progress.first.toFloat().coerceAtMost(progress.second.toFloat()),
        onValueChange = { dragging = it }, valueRange = 0f..progress.second.toFloat().coerceAtLeast(1f),
        enabled = progress.second > 0,
        onValueChangeFinished = {
            dragging?.let { playback.seek(it.toLong()) }
            dragging = null
        }, modifier = Modifier.semantics { contentDescription = label })
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(duration(progress.first)); Text(duration(progress.second))
    }
}

fun duration(ms: Long): String = "%d:%02d".format(ms / 60000, (ms / 1000) % 60)
