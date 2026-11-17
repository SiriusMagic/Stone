package com.zirakusa.stone.nativeapp.feature.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.media3.common.Player
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zirakusa.stone.core.Song
import com.zirakusa.stone.nativeapp.R
import com.zirakusa.stone.nativeapp.playback.control.PlaybackState
import com.zirakusa.stone.nativeapp.data.library.artwork.ArtworkLoader
import com.zirakusa.stone.nativeapp.ui.components.*
import com.zirakusa.stone.nativeapp.ui.theme.StonePalette
import com.zirakusa.stone.nativeapp.ui.theme.StoneLook
import kotlinx.coroutines.delay
import androidx.compose.runtime.produceState
import androidx.compose.material3.CircularProgressIndicator
import kotlin.math.abs

@Composable fun MiniPlayer(state: PlaybackState, open: () -> Unit, toggle: () -> Unit,
    modifier: Modifier = Modifier,
    song: Song? = null, previous: () -> Unit = {}, next: () -> Unit = {},
    onPlayMode: (() -> Unit)? = null, progress: () -> Float = { 0f }, onSwipeUp: () -> Unit = {}) {
    if (state.queue.isEmpty()) return
    val context = LocalContext.current.applicationContext
    var videoFrame by remember(song?.uri, song?.modifiedMs) { mutableStateOf<ArtworkLoader.VideoFrame?>(null) }
    // The transient "solo audio" action must not make a video look like a video again
    // merely because its file extension is still .mp4/.mkv.
    val hasVideoSource = song?.isVideo == true
    LaunchedEffect(song?.uri, song?.modifiedMs, hasVideoSource) {
        videoFrame = if (hasVideoSource) ArtworkLoader.loadVideoFrame(context, song!!.uri, song.modifiedMs) else null
    }
    Surface(modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp)
        .pointerInput(state.currentId) { detectMiniPlayerSwipe(previous, next, onSwipeUp) }
        .clickable(onClick = open),
        shape = RoundedCornerShape(24.dp), color = StonePalette.MiniBackground,
        border = BorderStroke(1.dp, StonePalette.MiniBorder), shadowElevation = 10.dp) {
        Row(Modifier.fillMaxWidth().heightIn(min = 76.dp).padding(10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            val roundCover = song?.isVideo != true && StoneLook.artworkShape in setOf("round", "round_spin")
            Box(Modifier.size(56.dp).clip(if (roundCover) CircleShape else RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                if (hasVideoSource && videoFrame?.bitmap != null) {
                    Image(videoFrame!!.bitmap!!.asImageBitmap(), null, Modifier.fillMaxSize(),
                        contentScale = if (videoFrame!!.height > videoFrame!!.width) ContentScale.Fit else ContentScale.Crop)
                } else if (song != null && StoneLook.artworkShape in setOf("round", "round_spin"))
                    PlayerArtwork(song, StoneLook.artworkShape, state.playing, Modifier.fillMaxSize())
                else StoneArtwork(song?.artworkSource, song?.modifiedMs ?: 0, Modifier.fillMaxSize())
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(state.title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, letterSpacing = 0.sp)
                Text(stringResource(if (state.playing) R.string.now_playing else R.string.tap_to_play),
                    Modifier.padding(top = 4.dp), color = StonePalette.MiniSubtitle, fontSize = 12.sp,
                    maxLines = 1, letterSpacing = 0.sp)
            }
            // Play mode: repeat all → repeat one → shuffle; the icon shows the current one.
            if (onPlayMode != null) IconButton(onClick = onPlayMode) {
                val (icon, label) = when {
                    state.shuffle -> Icons.Default.Shuffle to R.string.play_mode_shuffle
                    state.repeat == Player.REPEAT_MODE_ONE -> Icons.Default.RepeatOne to R.string.play_mode_one
                    else -> Icons.Default.Repeat to R.string.play_mode_all
                }
                Icon(icon, stringResource(label), tint = Color.White)
            }
            // Play/pause inside a ring that fills as the song advances.
            val fraction by produceState(progress(), state.playing, state.currentId) {
                value = progress()
                while (state.playing) { delay(500); value = progress() }
            }
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator({ fraction.coerceIn(0f, 1f) }, Modifier.size(50.dp), color = StonePalette.Yellow,
                    strokeWidth = 2.5.dp, trackColor = StonePalette.Yellow.copy(alpha = .25f), gapSize = 0.dp)
                StoneIconButton(if (state.playing) StoneGlyph.Pause else StoneGlyph.Play,
                    stringResource(if (state.playing) R.string.pause else R.string.play), toggle, Modifier.clip(CircleShape))
            }
        }
    }
}

private suspend fun PointerInputScope.detectMiniPlayerSwipe(onPrevious: () -> Unit,
    onNext: () -> Unit, onUp: () -> Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        var totalX = 0f
        var totalY = 0f
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull() ?: break
            if (!change.pressed || change.isConsumed) break
            val delta = change.positionChange()
            totalX += delta.x
            totalY += delta.y
            if (maxOf(abs(totalX), abs(totalY)) > viewConfiguration.touchSlop) {
                // Upwards opens "Recomendaciones para ti"; downwards is left to the screen.
                if (abs(totalY) > abs(totalX)) {
                    if (totalY > 0f) break
                    if (-totalY >= maxOf(viewConfiguration.touchSlop * 2.5f, density * 48f)) { change.consume(); onUp(); break }
                    continue
                }
                if (abs(totalX) >= maxOf(viewConfiguration.touchSlop * 2.5f, density * 48f)) {
                    change.consume()
                    if (totalX < 0f) onNext() else onPrevious()
                    break
                }
            }
        }
    }
}
