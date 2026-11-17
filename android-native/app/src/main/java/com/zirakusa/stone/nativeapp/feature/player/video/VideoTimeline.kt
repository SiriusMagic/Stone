@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.zirakusa.stone.nativeapp.feature.player.video

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.get
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.pointer.pointerInput
import com.zirakusa.stone.core.Song
import com.zirakusa.stone.nativeapp.playback.control.PlaybackViewModel
import com.zirakusa.stone.nativeapp.data.library.artwork.ArtworkLoader
import com.zirakusa.stone.nativeapp.ui.theme.StonePalette
import kotlin.math.abs
import com.zirakusa.stone.nativeapp.feature.player.PlayerProgress

@Composable internal fun VideoTimeline(song: Song, progress: Pair<Long, Long>, playback: PlaybackViewModel) {
    var framesVisible by remember(song.id, progress.second) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        if (framesVisible) {
            VideoFrameStrip(song, progress, playback, Modifier.fillMaxWidth().padding(bottom = 4.dp))
        }
        PlayerProgress(progress, playback, onVerticalDrag = { dragAmount ->
            when {
                dragAmount < -8f -> framesVisible = true
                dragAmount > 8f -> framesVisible = false
            }
        })
    }
}

internal fun coverBackgroundColor(bitmap: android.graphics.Bitmap): Color {
    var red = 0L; var green = 0L; var blue = 0L; var count = 0L
    val stepX = (bitmap.width / 24).coerceAtLeast(1)
    val stepY = (bitmap.height / 24).coerceAtLeast(1)
    for (y in 0 until bitmap.height step stepY) for (x in 0 until bitmap.width step stepX) {
        val pixel = bitmap[x, y]
        red += android.graphics.Color.red(pixel); green += android.graphics.Color.green(pixel)
        blue += android.graphics.Color.blue(pixel); count++
    }
    val darken = .52f
    return Color((red / count / 255f * darken).toFloat().coerceIn(.035f, .25f),
        (green / count / 255f * darken).toFloat().coerceIn(.035f, .25f),
        (blue / count / 255f * darken).toFloat().coerceIn(.035f, .25f))
}

/**
 * A bounded, YouTube-like preview strip. Frames are loaded lazily and only while the video
 * controls are visible; dragging the strip seeks the same player instead of creating a second
 * player or decoder.
 */
@Composable internal fun VideoFrameStrip(song: Song, progress: Pair<Long, Long>,
    playback: PlaybackViewModel, modifier: Modifier) {
    val durationMs = progress.second
    if (durationMs <= 0L) return
    val context = LocalContext.current
    val framePositions = remember(song.id, song.modifiedMs, durationMs) {
        List(8) { index -> durationMs * index / 7L }
    }
    var frames by remember(song.id, song.modifiedMs, durationMs) {
        mutableStateOf(List(framePositions.size) { ArtworkLoader.VideoFrame(null, 0, 0) })
    }
    LaunchedEffect(song.id, song.modifiedMs, durationMs) {
        // Decode the bounded thumbnails sequentially, but publish one state update after
        // the strip is complete. This avoids eight Compose recompositions while a preview
        // is loading and keeps the existing two-decoder memory limit in ArtworkLoader.
        val loaded = framePositions.map { positionMs ->
            ArtworkLoader.loadVideoFrameAt(
                context.applicationContext, song.uri, song.modifiedMs, positionMs, maxDimension = 180
            )
        }
        frames = loaded
    }
    val currentFraction = (progress.first.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    Box(
        modifier.height(54.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, StonePalette.Yellow.copy(alpha = .65f), RoundedCornerShape(8.dp))
            .pointerInput(song.id, durationMs) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var moved = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (!change.pressed) break
                        val horizontal = change.position.x - down.position.x
                        if (!moved && abs(horizontal) > viewConfiguration.touchSlop) moved = true
                        if (moved) {
                            val fraction = (change.position.x / size.width.toFloat().coerceAtLeast(1f))
                                .coerceIn(0f, 1f)
                            // The thumbnail strip is deliberately a light-weight seek affordance;
                            // the actual seek is still performed by the shared PlaybackViewModel.
                            // The gesture is consumed only after it becomes a drag.
                            change.consume()
                            playback.seek((durationMs * fraction).toLong().coerceIn(0L, durationMs))
                        }
                    }
                }
            }
    ) {
        Row(Modifier.fillMaxSize()) {
            frames.forEachIndexed { index, frame ->
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    frame.bitmap?.let { bitmap ->
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } ?: Box(Modifier.fillMaxSize().background(Color(0x4417171B)))
                    val start = index.toFloat() / framePositions.size
                    val end = (index + 1).toFloat() / framePositions.size
                    if (currentFraction in start..end ||
                        (index == framePositions.lastIndex && currentFraction == 1f)) {
                        Box(Modifier.fillMaxHeight().width(2.dp)
                            .background(StonePalette.Yellow).align(Alignment.CenterStart))
                    }
                    Box(Modifier.fillMaxHeight().width(.5.dp)
                        .background(Color(0x553A353D)).align(Alignment.CenterEnd))
                }
            }
        }
    }
}
