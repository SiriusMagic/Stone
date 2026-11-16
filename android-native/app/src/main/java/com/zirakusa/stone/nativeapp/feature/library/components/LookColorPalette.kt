package com.zirakusa.stone.nativeapp.feature.library.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

/**
 * Touch palette: hue left to right, pale at the top and deep at the bottom. Tapping or
 * dragging picks the colour under the finger; a ring marks the current one.
 */
@Composable internal fun LookColorPalette(current: Color, onPick: (Color) -> Unit) {
    val hues = (0..6).map { Color.hsv(it * 60f % 360f, 1f, 1f) }
    fun colorAt(offset: Offset, size: IntSize): Color {
        val x = (offset.x / size.width).coerceIn(0f, 1f)
        val y = (offset.y / size.height).coerceIn(0f, 1f)
        // Top half: white → pure hue; bottom half: pure hue → dark (never fully black).
        val saturation = if (y < .5f) .15f + y / .5f * .85f else 1f
        val value = if (y < .5f) 1f else 1f - (y - .5f) / .5f * .75f
        return Color.hsv(x * 359.9f, saturation, value)
    }
    Canvas(Modifier.fillMaxWidth().padding(top = 10.dp).height(150.dp).clip(RoundedCornerShape(16.dp))
        .border(1.dp, Color.White.copy(alpha = .15f), RoundedCornerShape(16.dp))
        .pointerInput(Unit) { detectTapGestures { onPick(colorAt(it, size)) } }
        .pointerInput(Unit) { detectDragGestures { change, _ -> onPick(colorAt(change.position, size)) } }) {
        drawRect(Brush.horizontalGradient(hues))
        drawRect(Brush.verticalGradient(0f to Color.White.copy(alpha = .85f), .5f to Color.Transparent,
            1f to Color.Black.copy(alpha = .75f)))
        val hsv = FloatArray(3).also { android.graphics.Color.colorToHSV(current.toArgb(), it) }
        val y = if (hsv[2] < .999f) .5f + (1f - hsv[2]) / .75f * .5f else ((hsv[1] - .15f) / .85f * .5f).coerceIn(0f, .5f)
        val center = Offset(hsv[0] / 360f * size.width, y.coerceIn(0f, 1f) * size.height)
        drawCircle(Color.White, 11.dp.toPx(), center, style = Stroke(3.dp.toPx()))
        drawCircle(Color.Black.copy(alpha = .5f), 13.dp.toPx(), center, style = Stroke(1.dp.toPx()))
    }
}
