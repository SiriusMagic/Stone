package com.zirakusa.stone.nativeapp.data.library.user

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
        filesFor(playlistId).forEach { it.delete() }
    }

    private fun save(playlistId: String, bitmap: Bitmap): String {
        // A new name per cover: the artwork cache is keyed by URI, so reusing the same file
        // would keep showing the previous image.
        val previous = filesFor(playlistId)
        val target = File(root, "${digest(playlistId)}-${System.currentTimeMillis()}.jpg")
        val temporary = File(root, ".${target.name}.tmp")
        try {
            FileOutputStream(temporary).use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, 88, output)) {
                    "No se pudo guardar la portada"
                }
                output.fd.sync()
            }
            check(temporary.renameTo(target)) { "No se pudo guardar la portada" }
            previous.forEach { it.delete() }
            return target.toUri().toString()
        } finally {
            temporary.delete()
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    private fun digest(playlistId: String) = MessageDigest.getInstance("SHA-256")
        .digest(playlistId.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    /** Every stored cover of the playlist (also the single-file name used before 1.6.7). */
    private fun filesFor(playlistId: String): List<File> {
        val prefix = digest(playlistId)
        return root.listFiles { file -> file.name.startsWith(prefix) }?.toList().orEmpty()
    }

    private fun calculateSample(width: Int, height: Int): Int {
        var sample = 1
        while (maxOf(width, height) / sample > 1600) sample *= 2
        return sample
    }
}
