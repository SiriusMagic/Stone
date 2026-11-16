@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.zirakusa.stone.nativeapp.feature.player

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.graphics.get
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChange
import com.zirakusa.stone.nativeapp.R
import com.zirakusa.stone.nativeapp.playback.PlaybackState
import com.zirakusa.stone.nativeapp.playback.PlaybackViewModel
import com.zirakusa.stone.nativeapp.playback.StonePlaybackService
import com.zirakusa.stone.nativeapp.ui.theme.StonePalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.roundToInt

internal enum class VideoAdjustmentSide { Brightness, Volume }

internal data class VideoAdjustment(val side: VideoAdjustmentSide, val value: Float,
    val maxPercent: Int)

/** Handles vertical brightness/volume adjustments and horizontal track changes. */
@Composable internal fun VideoGestureOverlay(state: PlaybackState, playback: PlaybackViewModel,
    modifier: Modifier, onTap: () -> Unit, onDoubleTap: (leftSide: Boolean) -> Unit = {},
    onPrevious: () -> Unit, onNext: () -> Unit) {
    val context = LocalContext.current
    var brightness by remember { mutableFloatStateOf(currentWindowBrightness(context)) }
    val maxVolumePercent = state.audioMode.maxVolumePercent
    var volume by remember { mutableFloatStateOf(
        state.volumePercent.toFloat().coerceIn(0f, maxVolumePercent.toFloat())
    ) }
    var adjustment by remember { mutableStateOf<VideoAdjustment?>(null) }
    // While a swipe is active the gesture owns its fractional value. Syncing from the
    // service mid-swipe snapped every small step back to the previous Android level
    // (STREAM_MUSIC has ~15 steps), so slow swipes never changed the volume.
    LaunchedEffect(state.volumePercent, maxVolumePercent, adjustment == null) {
        if (adjustment == null) volume = state.volumePercent.toFloat().coerceIn(0f, maxVolumePercent.toFloat())
    }

    LaunchedEffect(adjustment) {
        if (adjustment != null) {
            delay(850)
            adjustment = null
        }
    }
    var fastHold by remember { mutableStateOf(false) }
    var speedBeforeHold by remember { mutableFloatStateOf(1f) }
    val currentHold = rememberUpdatedState { start: Boolean ->
        if (start) { speedBeforeHold = state.speed; playback.setSpeed(2f) } else playback.setSpeed(speedBeforeHold)
        fastHold = start
    }
    val currentTap = rememberUpdatedState(onTap)
    val currentDoubleTap = rememberUpdatedState(onDoubleTap)
    val currentAdjustment = rememberUpdatedState(newValue = { leftSide: Boolean, delta: Float ->
        if (leftSide) {
            brightness = (brightness + delta).coerceIn(.02f, 1f)
            setWindowBrightness(context, brightness)
            adjustment = VideoAdjustment(VideoAdjustmentSide.Brightness, brightness, 100)
        } else {
            volume = (volume + delta * maxVolumePercent).coerceIn(0f, maxVolumePercent.toFloat())
            StonePlaybackService.setVolumePercent(volume.roundToInt(), showOverlay = false)
            adjustment = VideoAdjustment(VideoAdjustmentSide.Volume,
                volume / maxVolumePercent.toFloat(), maxVolumePercent)
        }
    })
    Box(modifier.pointerInput(Unit) {
        detectVideoGestures(
            onTap = { currentTap.value() },
            onDoubleTap = { currentDoubleTap.value(it) },
            onVerticalChange = { leftSide, delta -> currentAdjustment.value(leftSide, delta) },
            onHorizontalSwipe = { deltaX ->
                if (deltaX < 0f) onNext() else onPrevious()
            },
            onHold = { start -> currentHold.value(start) }
        )
    }) {
        adjustment?.let { VideoAdjustmentIndicator(it, Modifier.fillMaxSize()) }
        if (fastHold) Row(Modifier.align(Alignment.TopCenter).padding(top = 90.dp)
            .background(Color(0xB3000000), RoundedCornerShape(10.dp)).padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.FastForward, null, tint = Color.White, modifier = Modifier.size(20.dp))
            Text(" 2.0X ", color = Color(0xFF4CD2A8), fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.hold_speed_banner), color = Color.White)
        }
    }
}

@Composable internal fun VideoAdjustmentIndicator(adjustment: VideoAdjustment, modifier: Modifier) {
    val left = adjustment.side == VideoAdjustmentSide.Brightness
    Box(
        modifier = modifier.padding(horizontal = 18.dp),
        contentAlignment = if (left) Alignment.CenterStart else Alignment.CenterEnd
    ) {
        Column(
            modifier = Modifier.width(52.dp).height(176.dp)
                .background(Color(0xCC17151B), RoundedCornerShape(26.dp))
                .border(1.dp, StonePalette.Yellow.copy(alpha = .55f), RoundedCornerShape(26.dp))
                .padding(vertical = 14.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                if (left) Icons.Default.WbSunny else Icons.Default.VolumeUp,
                contentDescription = if (left) "Brillo" else "Volumen",
                tint = StonePalette.Yellow,
                modifier = Modifier.size(20.dp)
            )
            Box(Modifier.weight(1f).width(7.dp).clip(RoundedCornerShape(4.dp))
                .background(Color(0x663A353D))) {
                Box(Modifier.fillMaxWidth().fillMaxHeight(adjustment.value.coerceIn(0f, 1f))
                    .align(Alignment.BottomCenter).background(StonePalette.Yellow))
            }
            val percentage = adjustment.value * adjustment.maxPercent
            Text("${percentage.roundToInt()}%", color = Color.White,
                fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}

internal suspend fun PointerInputScope.detectVideoGestures(onTap: () -> Unit,
    onDoubleTap: (leftSide: Boolean) -> Unit,
    onVerticalChange: (leftSide: Boolean, delta: Float) -> Unit,
    onHorizontalSwipe: (Float) -> Unit, onHold: (Boolean) -> Unit = {}) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var totalX = 0f
        var totalY = 0f
        var moved = false
        var horizontalTriggered = false
        var holding = false
        while (true) {
            // Holding still past the long-press timeout plays at 2x until the finger lifts.
            val waitMs = viewConfiguration.longPressTimeoutMillis -
                (android.os.SystemClock.uptimeMillis() - down.uptimeMillis)
            val event = if (!moved && !holding) withTimeoutOrNull(waitMs.coerceAtLeast(1)) { awaitPointerEvent() }
                else awaitPointerEvent()
            if (event == null) { holding = true; onHold(true); continue }
            val change = event.changes.firstOrNull() ?: break
            if (!change.pressed) break
            val delta = change.positionChange()
            totalX += delta.x
            totalY += delta.y
            if (holding) { change.consume(); continue }
            if (!moved && maxOf(abs(totalX), abs(totalY)) > viewConfiguration.touchSlop) moved = true
            if (moved) {
                if (abs(totalY) >= abs(totalX)) {
                    val normalized = (-delta.y / size.height.toFloat().coerceAtLeast(1f))
                        .coerceIn(-.12f, .12f)
                    onVerticalChange(down.position.x < size.width / 2f, normalized)
                } else if (!horizontalTriggered &&
                    abs(totalX) >= maxOf(viewConfiguration.touchSlop * 2.5f, density * 64f)) {
                    horizontalTriggered = true
                    onHorizontalSwipe(totalX)
                }
                change.consume()
            }
        }
        if (holding) { onHold(false); return@awaitEachGesture }
        if (!moved) {
            // Waiting briefly for a second pointer-down lets a double tap remain a seek gesture
            // without making a single tap impossible.
            val secondDown = withTimeoutOrNull(android.view.ViewConfiguration.getDoubleTapTimeout().toLong()) {
                awaitFirstDown(requireUnconsumed = false)
            }
            if (secondDown == null) {
                onTap()
            } else {
                var secondMoved = false
                val secondPosition = secondDown.position
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull() ?: break
                    if (!change.pressed) break
                    if ((change.position - secondPosition).getDistance() > viewConfiguration.touchSlop) {
                        secondMoved = true
                    }
                    change.consume()
                }
                if (!secondMoved) {
                    onDoubleTap(down.position.x < size.width / 2f)
                }
            }
        }
    }
}

internal fun currentWindowBrightness(context: Context): Float {
    val value = context.findActivity()?.window?.attributes?.screenBrightness ?: -1f
    return if (value in .02f..1f) value else .5f
}

internal fun setWindowBrightness(context: Context, value: Float) {
    context.findActivity()?.let { activity ->
        val attributes = activity.window.attributes
        attributes.screenBrightness = value.coerceIn(.02f, 1f)
        activity.window.attributes = attributes
    }
}
