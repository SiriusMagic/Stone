package com.zirakusa.stone.nativeapp.feature.player

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.zirakusa.stone.nativeapp.R
import com.zirakusa.stone.nativeapp.playback.PlaybackState
import com.zirakusa.stone.nativeapp.playback.PlaybackViewModel

@Composable fun PlaybackControls(state: PlaybackState, playback: PlaybackViewModel) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        IconToggleButton(state.shuffle, { playback.shuffle() }) {
            Icon(Icons.Default.Shuffle, stringResource(R.string.shuffle))
        }
        IconButton(onClick = playback::previous) { Icon(Icons.Default.SkipPrevious, stringResource(R.string.previous)) }
        FilledIconButton(onClick = playback::toggle, modifier = Modifier.size(56.dp)) {
            Icon(if (state.playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                stringResource(if (state.playing) R.string.pause else R.string.play))
        }
        IconButton(onClick = playback::next) { Icon(Icons.Default.SkipNext, stringResource(R.string.next)) }
        IconToggleButton(state.repeat != Player.REPEAT_MODE_OFF, { playback.repeat() }) {
            Icon(if (state.repeat == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                stringResource(R.string.repeat))
        }
    }
}
