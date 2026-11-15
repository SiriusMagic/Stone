package com.zirakusa.stone.nativeapp.feature.library.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors

/**
 * Captures what the user is looking at when they ask to report a problem, so they don't
 * have to take a screenshot and attach it from the gallery. PixelCopy reads the window
 * surface (including video) without any permission. The file stays in the app cache.
 */
object ReportScreenshot {
    private val io = Executors.newSingleThreadExecutor()

    fun capture(context: Context, done: (Uri?) -> Unit) {
        val activity = context.findActivity() ?: return done(null)
        val decor = activity.window.decorView
        if (decor.width <= 0 || decor.height <= 0) return done(null)
        val bitmap = runCatching { createBitmap(decor.width, decor.height) }
            .getOrNull() ?: return done(null)
        val main = Handler(Looper.getMainLooper())
        runCatching {
            PixelCopy.request(activity.window, bitmap, { result ->
                if (result != PixelCopy.SUCCESS) { bitmap.recycle(); done(null); return@request }
                io.execute {
                    val uri = runCatching { save(context.applicationContext, bitmap) }.getOrNull()
                    bitmap.recycle()
                    main.post { done(uri) }
                }
            }, main)
        }.onFailure { bitmap.recycle(); done(null) }
    }

    private fun save(context: Context, bitmap: Bitmap): Uri {
        val folder = File(context.cacheDir, "reports").apply { mkdirs() }
        folder.listFiles()?.sortedByDescending { it.lastModified() }?.drop(5)?.forEach { it.delete() }
        val file = File(folder, "stone-${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
