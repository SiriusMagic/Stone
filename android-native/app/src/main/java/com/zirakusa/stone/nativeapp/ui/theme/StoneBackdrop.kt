package com.zirakusa.stone.nativeapp.ui.theme

import android.graphics.Bitmap
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.random.Random

/**
 * Theme backdrop: base colour, a soft accent glow and a fine film grain. The grain is a
 * 96 px tile generated once per process and repeated by the GPU, so it costs no work per
 * frame. AMOLED skips both layers to keep pixels fully off.
 */
fun Modifier.stoneBackdrop(): Modifier = drawWithCache {
    val base = StonePalette.Background
    val textured = StonePalette.Textured
    val glow = Brush.radialGradient(
        listOf(StonePalette.Glow.copy(alpha = .06f), Color.Transparent),
        center = Offset(size.width * .15f, 0f), radius = maxOf(size.width, size.height) * .75f)
    val grain = ShaderBrush(ImageShader(GrainTile.bitmap, TileMode.Repeated, TileMode.Repeated))
    onDrawBehind {
        drawRect(base)
        if (textured) {
            drawRect(glow)
            drawRect(grain, alpha = .03f)
        }
    }
}

private object GrainTile {
    val bitmap: ImageBitmap by lazy {
        val side = 96
        val random = Random(7)
        val pixels = IntArray(side * side) {
            val value = random.nextInt(256)
            (0xFF shl 24) or (value shl 16) or (value shl 8) or value
        }
        Bitmap.createBitmap(pixels, side, side, Bitmap.Config.ARGB_8888).asImageBitmap()
    }
}
