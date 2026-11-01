package com.zirakusa.stone.nativeapp.feature.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zirakusa.stone.nativeapp.R
import com.zirakusa.stone.nativeapp.playback.PlaybackState
import com.zirakusa.stone.nativeapp.playback.PlaybackViewModel

@Composable fun EnergySaverScreen(state: PlaybackState, playback: PlaybackViewModel, close: () -> Unit) {
    BackHandler(onBack = close)
    Column(Modifier.fillMaxSize().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = close) { Icon(Icons.Default.Close, stringResource(R.string.dismiss)) }
            Text(stringResource(R.string.energy_saver), fontWeight = FontWeight.Bold)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.BatterySaver, null, Modifier.size(80.dp))
            Text(state.title, fontSize = 25.sp, fontWeight = FontWeight.Black)
            Text(state.artist)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            IconButton(playback::previous) { Icon(Icons.Default.SkipPrevious, stringResource(R.string.previous), Modifier.size(48.dp)) }
            FilledIconButton(playback::toggle, Modifier.size(72.dp)) { Icon(if (state.playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                stringResource(if (state.playing) R.string.pause else R.string.play), Modifier.size(48.dp)) }
            IconButton(playback::next) { Icon(Icons.Default.SkipNext, stringResource(R.string.next), Modifier.size(48.dp)) }
        }
        Text(stringResource(R.string.energy_saver_hint), fontSize = 13.sp)
    }
}
