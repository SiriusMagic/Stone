package com.zirakusa.stone.nativeapp.data.library.user

import android.content.Context
import android.provider.DocumentsContract
import androidx.core.net.toUri
import java.io.File

/**
 * Removes the physical media file. SAF documents are deleted through the folder grant the
 * user already gave Stone; app-private and file paths are deleted directly. Returns true
 * when the file no longer exists afterwards (an already-missing file counts as deleted).
 */
object SongFileDeleter {
    fun delete(context: Context, uri: String): Boolean {
        val target = uri.toUri()
        return when (target.scheme) {
            "file", null -> target.path?.let(::File)?.let { !it.exists() || it.delete() } ?: false
            "content" -> runCatching {
                if (DocumentsContract.isDocumentUri(context, target)) {
                    DocumentsContract.deleteDocument(context.contentResolver, target)
                } else context.contentResolver.delete(target, null, null) > 0
            }.recover { error ->
                // A missing document means the work is already done.
                if (error is java.io.FileNotFoundException) true else throw error
            }.getOrDefault(false)
            else -> false
        }
    }
}
