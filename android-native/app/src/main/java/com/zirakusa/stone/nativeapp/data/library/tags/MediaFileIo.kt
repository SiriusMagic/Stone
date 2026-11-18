package com.zirakusa.stone.nativeapp.data.library.tags

import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.os.Process
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.net.toUri
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/**
 * Shared file access for the tag editors. Every edit changes the ORIGINAL file and then asks
 * Android to re-index it, so other players show the new title/artist/cover too.
 */
object MediaFileIo {
    /** MediaStore files (found by "scan whole device") need the user's consent on Android 11+. */
    fun needsWriteConsent(context: Context, value: String): Boolean {
        val uri = value.toUri()
        if (Build.VERSION.SDK_INT < 30 || uri.authority != MediaStore.AUTHORITY) return false
        // "Acceso a todos los archivos" lets Stone edit any media file without a per-file dialog.
        if (hasAllFilesAccess()) return false
        return context.checkUriPermission(uri, Process.myPid(), Process.myUid(),
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION) != PackageManager.PERMISSION_GRANTED
    }

    /** Android 11+ "All files access" granted in system settings. */
    fun hasAllFilesAccess(): Boolean = Build.VERSION.SDK_INT >= 30 && runCatching { Environment.isExternalStorageManager() }.getOrDefault(false)

    fun writeConsentRequest(context: Context, values: List<String>): IntentSender? =
        if (Build.VERSION.SDK_INT >= 30) MediaStore.createWriteRequest(context.contentResolver,
            values.map { it.toUri() }).intentSender else null

    /** File extension, also for MediaStore URIs that have none in their path. */
    fun extension(context: Context, value: String): String {
        val uri = value.toUri()
        val fromPath = Uri.decode(value).substringBefore('?').substringAfterLast('/').substringAfterLast('.', "")
        if (fromPath.isNotEmpty() && uri.authority != MediaStore.AUTHORITY) return fromPath.lowercase()
        val name = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        }.getOrNull().orEmpty()
        return name.substringAfterLast('.', fromPath).lowercase()
    }

    fun open(context: Context, uri: Uri): FileInputStream =
        if (uri.scheme == "file") FileInputStream(File(uri.path!!))
        else ParcelFileDescriptor.AutoCloseInputStream(context.contentResolver.openFileDescriptor(uri, "r")
            ?: error("No se pudo abrir el archivo"))

    /** Positional writer; never in append mode so headers can be replaced in place. */
    fun openWritable(context: Context, uri: Uri): FileChannel =
        if (uri.scheme == "file") RandomAccessFile(File(uri.path!!), "rw").channel
        else ParcelFileDescriptor.AutoCloseOutputStream(context.contentResolver.openFileDescriptor(uri, "rw")
            ?: error("El proveedor no permite modificar este archivo")).channel

    fun copy(source: FileChannel, offset: Long, length: Long, target: FileChannel) {
        var done = 0L
        while (done < length) {
            val moved = source.transferTo(offset + done, length - done, target)
            check(moved > 0) { "Copia interrumpida" }
            done += moved
        }
    }

    fun readHead(context: Context, uri: Uri, length: Int): ByteArray = open(context, uri).use { input ->
        val size = minOf(length.toLong(), input.channel.size()).toInt()
        val buffer = ByteBuffer.allocate(size)
        while (buffer.hasRemaining() && input.channel.read(buffer, buffer.position().toLong()) > 0) Unit
        buffer.array().copyOf(buffer.position())
    }

    fun size(context: Context, uri: Uri): Long = open(context, uri).use { it.channel.size() }

    /**
     * Replaces the first [oldHeadSize] bytes with [newHead]. Same size: written in place.
     * Different size: the file is rebuilt in a temporary copy (media bytes streamed, never
     * decoded) and then copied over the original.
     */
    fun replaceHead(context: Context, uri: Uri, oldHeadSize: Long, newHead: ByteArray) {
        if (newHead.size.toLong() == oldHeadSize) {
            openWritable(context, uri).use { output ->
                val buffer = ByteBuffer.wrap(newHead)
                while (buffer.hasRemaining()) output.write(buffer, buffer.position().toLong())
                output.force(true)
            }
            return
        }
        val temporary = File.createTempFile(".stone-tags-", ".tmp", context.cacheDir)
        try {
            val expected = open(context, uri).use { input ->
                val rest = input.channel.size() - oldHeadSize
                require(rest >= 0) { "Archivo incompleto" }
                FileOutputStream(temporary).use { output ->
                    output.write(newHead)
                    copy(input.channel, oldHeadSize, rest, output.channel)
                    output.fd.sync()
                }
                newHead.size + rest
            }
            check(temporary.length() == expected) { "La copia temporal está incompleta" }
            openWritable(context, uri).use { output ->
                output.position(0)
                FileInputStream(temporary).use { copy(it.channel, 0, expected, output) }
                output.truncate(expected)
                output.force(true)
            }
        } finally {
            temporary.delete()
        }
    }

    /** Human readable location (/storage/...) for the editor's "Ruta del archivo". */
    fun displayPath(context: Context, value: String): String {
        val uri = value.toUri()
        if (uri.authority == MediaStore.AUTHORITY) runCatching {
            @Suppress("DEPRECATION")
            context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)?.use {
                if (it.moveToFirst()) return it.getString(0) ?: value
            }
        }
        return filePath(uri) ?: Uri.decode(value)
    }

    private fun filePath(uri: Uri): String? = when {
        uri.scheme == "file" -> uri.path
        uri.authority == "com.android.externalstorage.documents" -> runCatching {
            val id = DocumentsContract.getDocumentId(uri)
            val volume = id.substringBefore(':')
            @Suppress("DEPRECATION")
            val root = if (volume.equals("primary", true)) Environment.getExternalStorageDirectory().path
                else "/storage/$volume"
            "$root/${id.substringAfter(':', "")}"
        }.getOrNull()
        else -> null
    }

    /** Ask Android to re-read the file so MediaStore (and every other player) sees the edit. */
    fun rescan(context: Context, value: String) {
        val uri = value.toUri()
        val path = when {
            uri.scheme == "file" -> uri.path
            uri.authority == "com.android.externalstorage.documents" -> runCatching {
                val id = DocumentsContract.getDocumentId(uri)
                val volume = id.substringBefore(':')
                val relative = id.substringAfter(':', "")
                @Suppress("DEPRECATION")
                val root = if (volume.equals("primary", true)) Environment.getExternalStorageDirectory().path
                    else "/storage/$volume"
                "$root/$relative"
            }.getOrNull()
            else -> null
        }
        if (path != null) MediaScannerConnection.scanFile(context.applicationContext, arrayOf(path), null, null)
        else if (uri.authority == MediaStore.AUTHORITY) runCatching {
            // Writes through a MediaStore URI are re-indexed when the descriptor closes;
            // the notification refreshes apps that already listed it.
            context.contentResolver.notifyChange(uri, null)
        }
    }
}
