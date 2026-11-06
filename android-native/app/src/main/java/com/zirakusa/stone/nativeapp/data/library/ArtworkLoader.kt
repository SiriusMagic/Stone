package com.zirakusa.stone.nativeapp.data.library

import android.content.Context
import android.content.ComponentCallbacks2
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/** Only visible artwork is loaded. Memory is bounded; no full-library artwork scan. */
object ArtworkLoader {
    data class VideoFrame(val bitmap: Bitmap?, val width: Int, val height: Int)
    private val cache = object : LruCache<String, Bitmap>(4 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }
    private val videoCache = object : LruCache<String, VideoFrame>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: VideoFrame) = value.bitmap?.allocationByteCount ?: 1
    }
    private val missing = LruCache<String, Boolean>(256)
    private val slots = Semaphore(2)
    @Volatile private var configured = false

    /** Keeps visible artwork bounded and lets Android reclaim it under memory pressure. */
    fun configure(context: Context) {
        if (configured) return
        synchronized(this) {
            if (configured) return
            val memoryClassMb = context.getSystemService(android.app.ActivityManager::class.java)
                ?.memoryClass ?: 128
            val totalCacheMb = when {
                memoryClassMb <= 64 -> 4
                memoryClassMb <= 128 -> 8
                else -> 12
            }
            cache.resize(totalCacheMb * 1024 * 1024 / 2)
            videoCache.resize(totalCacheMb * 1024 * 1024 / 2)
            context.applicationContext.registerComponentCallbacks(object : ComponentCallbacks2 {
                override fun onConfigurationChanged(newConfig: Configuration) = Unit
                override fun onLowMemory() = trimMemory()
                override fun onTrimMemory(level: Int) {
                    if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) trimMemory()
                }
            })
            configured = true
        }
    }

    private fun trimMemory() {
        cache.evictAll()
        videoCache.evictAll()
        missing.evictAll()
    }

    suspend fun load(context: Context, uri: String, modified: Long): Bitmap? = withContext(Dispatchers.IO) {
        val key = "$uri:$modified"
        cache.get(key)?.let { return@withContext it }
        if (missing.get(key) == true) return@withContext null
        slots.withPermit {
            currentCoroutineContext().ensureActive()
            cache.get(key)?.let { return@withPermit it }
            val retriever = MediaMetadataRetriever()
            val bitmap = try {
                val source = Uri.parse(uri)
                if (source.scheme == "file") decodeArtworkFile(source.path)
                else {
                    retriever.setDataSource(context, source)
                    retriever.embeddedPicture?.let(::decodeArtworkBytes)
                }
            } catch (_: RuntimeException) { null }
            finally { retriever.release() }
            currentCoroutineContext().ensureActive()
            if (bitmap != null) cache.put(key, bitmap) else missing.put(key, true)
            bitmap
        }
    }

    private fun decodeArtworkBytes(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val options = BitmapFactory.Options().apply { inSampleSize = 1 }
        while (maxOf(bounds.outWidth, bounds.outHeight) / options.inSampleSize > 512) {
            options.inSampleSize *= 2
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    private fun decodeArtworkFile(path: String?): Bitmap? {
        if (path.isNullOrBlank()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val options = BitmapFactory.Options().apply { inSampleSize = 1 }
        while (maxOf(bounds.outWidth, bounds.outHeight) / options.inSampleSize > 512) {
            options.inSampleSize *= 2
        }
        return BitmapFactory.decodeFile(path, options)
    }

    suspend fun loadVideoFrame(context: Context, uri: String, modified: Long): VideoFrame =
        loadVideoFrameAt(context, uri, modified, 0L)

    /** Loads a bounded thumbnail at a video position for the seek preview strip. */
    suspend fun loadVideoFrameAt(context: Context, uri: String, modified: Long,
        positionMs: Long, maxDimension: Int = 640): VideoFrame =
        withContext(Dispatchers.IO) {
            val safePosition = positionMs.coerceAtLeast(0L)
            val safeDimension = maxDimension.coerceIn(96, 640)
            val key = "$uri:$modified:$safePosition:$safeDimension"
            videoCache.get(key)?.let { return@withContext it }
            slots.withPermit {
                currentCoroutineContext().ensureActive()
                videoCache.get(key)?.let { return@withPermit it }
                val retriever = MediaMetadataRetriever()
                val frame = try {
                    retriever.setDataSource(context, Uri.parse(uri))
                    val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                        ?.toIntOrNull() ?: 0
                    val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                        ?.toIntOrNull() ?: 0
                    val bitmap = retriever.getFrameAtTime(safePosition * 1_000L,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        ?.let { source ->
                            val longest = maxOf(source.width, source.height)
                            if (longest <= safeDimension) source else {
                                val scale = safeDimension.toFloat() / longest
                                Bitmap.createScaledBitmap(source, (source.width * scale).toInt(),
                                    (source.height * scale).toInt(), true)
                            }
                        }
                    VideoFrame(bitmap, width, height)
                } catch (_: RuntimeException) { VideoFrame(null, 0, 0) }
                finally { retriever.release() }
                currentCoroutineContext().ensureActive()
                videoCache.put(key, frame)
                frame
            }
        }
}
