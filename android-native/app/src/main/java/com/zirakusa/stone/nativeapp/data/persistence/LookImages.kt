package com.zirakusa.stone.nativeapp.data.persistence

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.graphics.scale
import androidx.core.net.toUri
import java.io.File
import java.io.FileOutputStream

/** Private copies of images chosen in Personalizado (background, avatar, shortcut icon). */
object LookImages {
    fun save(context: Context, source: Uri, name: String, maxSide: Int): String {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0) { "Imagen no válida" }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxSide * 2) sample *= 2
        val decoded = context.contentResolver.openInputStream(source)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: error("Imagen no válida")
        val scale = minOf(1f, maxSide.toFloat() / maxOf(decoded.width, decoded.height))
        val bitmap = if (scale < 1f) decoded.scale((decoded.width * scale).toInt(), (decoded.height * scale).toInt()) else decoded
        val folder = File(context.filesDir, "look").apply { mkdirs() }
        folder.listFiles { file -> file.name.startsWith("$name-") }?.forEach { it.delete() }
        // A new file name per choice so cached bitmaps never show the previous image.
        val target = File(folder, "$name-${System.currentTimeMillis()}.jpg")
        FileOutputStream(target).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        return target.toUri().toString()
    }

    fun load(value: String?, maxSide: Int): Bitmap? = runCatching {
        val path = value?.toUri()?.path ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxSide * 2) sample *= 2
        BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
    }.getOrNull()
}
