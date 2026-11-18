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
fun Modifier.stoneBackdrop(image: ImageBitmap? = null): Modifier = drawWithCache {
    val base = StonePalette.Background
    val textured = StonePalette.Textured
    val glow = Brush.radialGradient(
        listOf(StonePalette.Glow.copy(alpha = .06f), Color.Transparent),
        center = Offset(size.width * .15f, 0f), radius = maxOf(size.width, size.height) * .75f)
    val grain = ShaderBrush(ImageShader(GrainTile.bitmap, TileMode.Repeated, TileMode.Repeated))
    onDrawBehind {
        drawRect(base)
        if (image != null) {
            // Center-crop the user's image, then veil it so text stays readable.
            val scale = maxOf(size.width / image.width, size.height / image.height)
            val srcW = (size.width / scale).toInt().coerceAtMost(image.width)
            val srcH = (size.height / scale).toInt().coerceAtMost(image.height)
            drawImage(image, srcOffset = androidx.compose.ui.unit.IntOffset((image.width - srcW) / 2, (image.height - srcH) / 2),
                srcSize = androidx.compose.ui.unit.IntSize(srcW, srcH),
                dstSize = androidx.compose.ui.unit.IntSize(size.width.toInt(), size.height.toInt()))
            val veil = 1f - StoneLook.backgroundOpacity
            if (StoneLook.backgroundMode == "focus") {
                // Focus: the centre shows the image at the chosen opacity, the edges fade to the theme colour.
                drawRect(Brush.radialGradient(listOf(base.copy(alpha = veil), base.copy(alpha = (veil + .55f).coerceAtMost(.97f))),
                    center = Offset(size.width / 2, size.height * .42f), radius = maxOf(size.width, size.height) * .55f))
            } else drawRect(base.copy(alpha = veil))
            return@onDrawBehind
        }
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
