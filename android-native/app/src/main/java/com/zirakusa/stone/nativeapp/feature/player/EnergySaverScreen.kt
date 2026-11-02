package com.zirakusa.stone.nativeapp.feature.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zirakusa.stone.nativeapp.R
import com.zirakusa.stone.nativeapp.playback.PlaybackState
import com.zirakusa.stone.nativeapp.playback.PlaybackViewModel
import com.zirakusa.stone.core.Song
import com.zirakusa.stone.nativeapp.ui.components.StoneArtwork

@Composable fun EnergySaverScreen(state: PlaybackState, song: Song?, playback: PlaybackViewModel, close: () -> Unit) {
    BackHandler(onBack = close)
    var dragDistance by androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    Box(Modifier.fillMaxSize().background(Color.Black).pointerInput(Unit) {
        detectHorizontalDragGestures(
            onDragEnd = { if (kotlin.math.abs(dragDistance) > 180f) close(); dragDistance = 0f },
            onDragCancel = { dragDistance = 0f },
            onHorizontalDrag = { change, amount -> change.consume(); dragDistance += amount }
        )
    }) {
    Column(Modifier.fillMaxSize().graphicsLayer { translationX = dragDistance }.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween) {
        Spacer(Modifier.height(24.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (song != null) StoneArtwork(song.uri, song.modifiedMs,
                Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(28.dp)))
            else Icon(Icons.Default.BatterySaver, null, Modifier.size(80.dp))
            Text(state.title, fontSize = 25.sp, fontWeight = FontWeight.Black, color = Color.White)
            Text(state.artist, color = Color(0xFFBDBDBD))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            IconButton(playback::previous) { Icon(Icons.Default.SkipPrevious, stringResource(R.string.previous), Modifier.size(48.dp)) }
            FilledIconButton(playback::toggle, Modifier.size(72.dp)) { Icon(if (state.playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                stringResource(if (state.playing) R.string.pause else R.string.play), Modifier.size(48.dp)) }
            IconButton(playback::next) { Icon(Icons.Default.SkipNext, stringResource(R.string.next), Modifier.size(48.dp)) }
        }
        Text(stringResource(R.string.energy_saver_hint), fontSize = 13.sp, color = Color(0xFFBDBDBD))
    }
    // Keeps the cover and controls readable while reducing visual brightness.
    Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = .28f)))
    }
}
