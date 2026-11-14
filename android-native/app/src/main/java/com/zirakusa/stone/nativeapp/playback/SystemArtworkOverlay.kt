package com.zirakusa.stone.nativeapp.playback

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.LruCache
import com.zirakusa.stone.nativeapp.R
import com.zirakusa.stone.nativeapp.data.library.ArtworkLoader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

/**
 * Builds the artwork sent to Android's media controls without changing the
 * artwork embedded in, or next to, the user's media file.
 *
 * The app UI continues to use [ArtworkLoader] directly. This cache only keeps
 * the small encoded copies used by MediaSession, so a system notification or
 * lock-screen control does not cause the same composition to be repeated.
 */
object SystemArtworkOverlay {
    private const val LOGO_VERSION = 1
    private const val LOGO_FRACTION = 0.18f
    private const val MARGIN_FRACTION = 0.04f
    private const val CACHE_BYTES = 2 * 1024 * 1024

    private val cache = object : LruCache<String, ByteArray>(CACHE_BYTES) {
        override fun sizeOf(key: String, value: ByteArray): Int = value.size
    }
    private val missing = LruCache<String, Boolean>(128)
    @Volatile private var logo: Bitmap? = null

    /** Returns a PNG containing the original artwork and Stone's official logo. */
    suspend fun load(context: Context, source: String, modified: Long): ByteArray? {
        if (source.isBlank()) return null
        val key = "$LOGO_VERSION:$source:$modified"
        cache.get(key)?.let { return it }
        if (missing.get(key) == true) return null

        val appContext = context.applicationContext
        ArtworkLoader.configure(appContext)
        val original = ArtworkLoader.load(appContext, source, modified) ?: run {
            missing.put(key, true)
            return null
        }

        val encoded = try {
            withContext(Dispatchers.Default) {
                encode(compose(original, logo(appContext)))
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: RuntimeException) {
            null
        }
        if (encoded == null) missing.put(key, true) else cache.put(key, encoded)
        return encoded
    }

    private fun logo(context: Context): Bitmap? {
        logo?.let { return it }
        synchronized(this) {
            logo?.let { return it }
            logo = BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher)
            return logo
        }
    }

    private fun compose(original: Bitmap, watermark: Bitmap?): Bitmap? {
        if (watermark == null || watermark.isRecycled || original.isRecycled) return null
        val result = original.copy(Bitmap.Config.ARGB_8888, true) ?: return null
        val canvas = Canvas(result)
        val shortestSide = minOf(result.width, result.height).toFloat()
        val boxSize = (shortestSide * LOGO_FRACTION).roundToInt().coerceAtLeast(1)
        val scale = minOf(boxSize.toFloat() / watermark.width,
            boxSize.toFloat() / watermark.height)
        val logoWidth = (watermark.width * scale).roundToInt().coerceAtLeast(1)
        val logoHeight = (watermark.height * scale).roundToInt().coerceAtLeast(1)
        val margin = (shortestSide * MARGIN_FRACTION).roundToInt().coerceAtLeast(1)
        val left = margin.toFloat()
        val top = (result.height - margin - logoHeight).coerceAtLeast(0).toFloat()
        val destination = RectF(left, top, left + logoWidth, top + logoHeight)
        canvas.drawBitmap(watermark, null, destination, Paint(Paint.ANTI_ALIAS_FLAG or
            Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG))
        return result
    }

    private fun encode(bitmap: Bitmap?): ByteArray? {
        bitmap ?: return null
        return try {
            ByteArrayOutputStream().use { output ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) null
                else output.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
    }

    internal fun composeForTest(original: Bitmap, watermark: Bitmap): Bitmap? =
        compose(original, watermark)

    internal fun clearForTest() {
        cache.evictAll()
        missing.evictAll()
        logo = null
    }
}
