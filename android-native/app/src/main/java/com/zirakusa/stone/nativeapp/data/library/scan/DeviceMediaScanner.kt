package com.zirakusa.stone.nativeapp.data.library.scan

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.zirakusa.stone.core.Song
import kotlinx.coroutines.flow.MutableSharedFlow

/** Runtime permission needed to read every song and video indexed by Android. */
object DeviceMediaAccess {
    fun permissions(): Array<String> = if (Build.VERSION.SDK_INT >= 33) {
        arrayOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.READ_MEDIA_VIDEO)
    } else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)

    private fun has(context: Context, permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun audioGranted(context: Context) = has(context, permissions().first())
    fun videoGranted(context: Context) = has(context, permissions().last())

    /** Emitted after the permission or the setting changes, so the library rescans at once. */
    val rescanRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
}

/**
 * Reads Android's media index (MediaStore) like any music player does: fast, no folder
 * walking and no file opening. Ringtones, alarms and notification sounds are skipped.
 */
class DeviceMediaScanner(private val context: Context) {
    fun scan(): List<Song> = buildList {
        if (DeviceMediaAccess.audioGranted(context)) addAll(audio())
        if (DeviceMediaAccess.videoGranted(context)) addAll(video())
    }

    private fun audio(): List<Song> {
        val selection = "(${MediaStore.Audio.Media.IS_MUSIC} != 0 OR ${MediaStore.Audio.Media.DURATION} >= 30000)" +
            " AND ${MediaStore.Audio.Media.IS_RINGTONE} = 0 AND ${MediaStore.Audio.Media.IS_ALARM} = 0" +
            " AND ${MediaStore.Audio.Media.IS_NOTIFICATION} = 0"
        return query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, selection, video = false,
            arrayOf(MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM) + genreColumn())
    }

    /** MediaStore has a genre column for audio since Android 11. */
    private fun genreColumn(): Array<String> = if (Build.VERSION.SDK_INT >= 30) arrayOf(MediaStore.Audio.Media.GENRE) else emptyArray()

    private fun video(): List<Song> =
        query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, "${MediaStore.Video.Media.DURATION} > 0", video = true,
            arrayOf(MediaStore.Video.Media.ARTIST, MediaStore.Video.Media.ALBUM))

    private fun query(collection: Uri, selection: String, video: Boolean, extra: Array<String>): List<Song> {
        val projection = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.TITLE,
            MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.DURATION, MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_MODIFIED, folderColumn()) + extra
        val songs = mutableListOf<Song>()
        runCatching {
            context.contentResolver.query(collection, projection, selection, null, null)?.use { cursor ->
                fun text(index: Int) = cursor.getString(index)?.takeUnless { it == "<unknown>" }.orEmpty()
                while (cursor.moveToNext()) {
                    val uri = ContentUris.withAppendedId(collection, cursor.getLong(0))
                    val name = text(2).substringBeforeLast('.')
                    val folder = text(6).let { if (folderColumn() == FOLDER_LEGACY) it.substringBeforeLast('/').substringAfterLast('/') else it }
                    songs += Song(uri.toString(), text(1).ifBlank { name }, text(7), text(8),
                        folder, cursor.getLong(3), cursor.getLong(4), cursor.getLong(5) * 1000L, isVideo = video,
                        genre = if (cursor.columnCount > 9) text(9) else "")
                }
            }
        }
        return songs
    }

    @Suppress("DEPRECATION")
    private fun folderColumn() = if (Build.VERSION.SDK_INT >= 29) MediaStore.MediaColumns.BUCKET_DISPLAY_NAME
        else FOLDER_LEGACY

    private companion object {
        @Suppress("DEPRECATION") val FOLDER_LEGACY = MediaStore.MediaColumns.DATA
    }
}
