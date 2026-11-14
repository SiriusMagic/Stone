package com.zirakusa.stone.nativeapp.data.library

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.net.toUri
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/** Copies selected playlist artwork into app-private storage so it remains available later. */
class PlaylistCoverStore(context: Context) {
    private val root = File(context.filesDir, "playlist-covers").also { it.mkdirs() }

    fun copyFrom(context: Context, playlistId: String, source: Uri): String {
        val bitmap = context.contentResolver.openInputStream(source)?.use { input ->
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(input, null, bounds)
            require(bounds.outWidth > 0 && bounds.outHeight > 0) { "La imagen no es válida" }
            val sample = calculateSample(bounds.outWidth, bounds.outHeight)
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            context.contentResolver.openInputStream(source)?.use { fresh ->
                BitmapFactory.decodeStream(fresh, null, options)
            }
        } ?: error("No se pudo abrir o decodificar la portada")
        return save(playlistId, bitmap)
    }

    fun clear(playlistId: String) {
        fileFor(playlistId).delete()
    }

    private fun save(playlistId: String, bitmap: Bitmap): String {
        val target = fileFor(playlistId)
        val temporary = File(target.parentFile, ".${target.name}.tmp")
        try {
            FileOutputStream(temporary).use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, 88, output)) {
                    "No se pudo guardar la portada"
                }
                output.fd.sync()
            }
            if (!temporary.renameTo(target)) {
                target.delete()
                check(temporary.renameTo(target)) { "No se pudo reemplazar la portada" }
            }
            return target.toUri().toString()
        } finally {
            temporary.delete()
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    private fun fileFor(playlistId: String): File {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(playlistId.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return File(root, "$digest.jpg")
    }

    private fun calculateSample(width: Int, height: Int): Int {
        var sample = 1
        while (maxOf(width, height) / sample > 1600) sample *= 2
        return sample
    }
}
