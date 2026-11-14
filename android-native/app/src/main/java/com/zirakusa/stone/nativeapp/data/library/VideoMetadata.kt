package com.zirakusa.stone.nativeapp.data.library

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.core.net.toUri
import com.zirakusa.stone.core.Song
import com.zirakusa.stone.nativeapp.data.persistence.StoneDatabase
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Locale

enum class VideoContainer(val extension: String) {
    MP4("mp4"), M4V("m4v"), MOV("mov"), MKV("mkv"), WEBM("webm"), OTHER("");

    companion object {
        fun fromUri(value: String): VideoContainer = when (
            value.substringBefore('?').substringAfterLast('.', "").lowercase(Locale.ROOT)
        ) {
            "mp4" -> MP4
            "m4v" -> M4V
            "mov" -> MOV
            "mkv" -> MKV
            "webm" -> WEBM
            else -> OTHER
        }
    }
}

data class VideoMetadataDraft(
    val title: String = "",
    val description: String = "",
    val author: String = "",
    val date: String = "",
    val genre: String = "",
    val thumbnailUri: String = "",
    /** True only when the user explicitly selected or removed the thumbnail. */
    val thumbnailChanged: Boolean = false,
)

data class StoredVideoMetadata(
    val draft: VideoMetadataDraft,
    val nativeFileWritten: Boolean = false,
)

class VideoMetadataStore(context: Context) {
    private val database = StoneDatabase.get(context)

    fun read(uri: String): StoredVideoMetadata? = runCatching {
        val value = database.getString(key(uri)) ?: return null
        val json = JSONObject(value)
        StoredVideoMetadata(
            draft = VideoMetadataDraft(
                title = json.optString("title"),
                description = json.optString("description"),
                author = json.optString("author"),
                date = json.optString("date"),
                genre = json.optString("genre"),
                thumbnailUri = json.optString("thumbnailUri"),
            ),
            nativeFileWritten = json.optBoolean("nativeFileWritten"),
        )
    }.getOrNull()

    fun save(uri: String, draft: VideoMetadataDraft, nativeFileWritten: Boolean) {
        database.putString(key(uri), JSONObject()
            .put("title", draft.title.trim())
            .put("description", draft.description.trim())
            .put("author", draft.author.trim())
            .put("date", draft.date.trim())
            .put("genre", draft.genre.trim())
            .put("thumbnailUri", draft.thumbnailUri)
            .put("nativeFileWritten", nativeFileWritten)
            .put("updatedAt", System.currentTimeMillis())
            .toString())
    }

    /** Applies only app-owned overrides; it never changes the media file. */
    fun applyToSong(song: Song): Song {
        if (!song.isVideo) return song
        val stored = read(song.uri)?.draft ?: return song
        return song.copy(
            title = stored.title.ifBlank { song.title },
            artist = stored.author.ifBlank { song.artist },
            artworkUri = stored.thumbnailUri.ifBlank { song.artworkUri },
        )
    }

    private fun key(uri: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(uri.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return "video.metadata.$digest"
    }
}

data class VideoMetadataDocument(
    val draft: VideoMetadataDraft,
    val container: VideoContainer,
    val nativeRead: Boolean,
    val nativeWriteAvailable: Boolean,
    val nativeFileWritten: Boolean,
)

data class VideoMetadataSaveResult(
    val nativeFileWritten: Boolean,
    val internalMetadataSaved: Boolean = true,
    val nativeFailureMessage: String? = null,
)

/** Keeps custom video artwork in the app's private storage, not as a transient gallery URI. */
class VideoArtworkStore(context: Context) {
    private val root = File(context.filesDir, "video-covers").also { it.mkdirs() }

    fun saveFromUri(context: Context, videoUri: String, imageUri: Uri): String {
        val source = context.contentResolver.openInputStream(imageUri)
            ?: error("No se pudo abrir la imagen seleccionada")
        val bitmap = source.use { input ->
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(imageUri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            require(bounds.outWidth > 0 && bounds.outHeight > 0) { "La imagen no es válida" }
            val sample = calculateSample(bounds.outWidth, bounds.outHeight)
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            BitmapFactory.decodeStream(input, null, options)
        } ?: error("No se pudo decodificar la imagen")
        return saveBitmap(videoUri, bitmap)
    }

    fun saveBitmap(videoUri: String, bitmap: Bitmap): String {
        val target = fileFor(videoUri)
        val temporary = File(target.parentFile, ".${target.name}.tmp")
        try {
            FileOutputStream(temporary).use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, 88, output)) {
                    "No se pudo guardar la miniatura"
                }
                output.fd.sync()
            }
            if (!temporary.renameTo(target)) {
                target.delete()
                check(temporary.renameTo(target)) { "No se pudo reemplazar la miniatura" }
            }
            return target.toUri().toString()
        } finally {
            temporary.delete()
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    private fun fileFor(uri: String): File {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(uri.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return File(root, "$digest.jpg")
    }

    private fun calculateSample(width: Int, height: Int): Int {
        var sample = 1
        while (maxOf(width, height) / sample > 1600) sample *= 2
        return sample
    }
}

/**
 * Metadata facade used by the editor. Android exposes reliable metadata reading for all
 * common containers, but it does not expose a general metadata writer. MP4/M4V files are
 * handled by the small, no-reencode writer when the provider is a writable local file;
 * everything else is kept safely in Stone's database.
 */
class VideoMetadataEditor(private val context: Context) {
    private val store = VideoMetadataStore(context)
    private val artwork = VideoArtworkStore(context)

    fun read(value: String): VideoMetadataDocument {
        val stored = store.read(value)
        val native = readNative(value)
        val fallbackTitle = value.substringBefore('?').substringAfterLast('/').substringBeforeLast('.')
        val draft = stored?.draft ?: VideoMetadataDraft()
        return VideoMetadataDocument(
            draft = draft.copy(
                title = draft.title.ifBlank { native.title.ifBlank { fallbackTitle } },
                author = draft.author.ifBlank { native.author },
                date = draft.date.ifBlank { native.date },
                genre = draft.genre.ifBlank { native.genre },
            ),
            container = VideoContainer.fromUri(value),
            nativeRead = native.read,
            nativeWriteAvailable = Mp4MetadataWriter.canWrite(context, value),
            nativeFileWritten = stored?.nativeFileWritten == true,
        )
    }

    fun save(value: String, draft: VideoMetadataDraft): VideoMetadataSaveResult {
        val nativeAvailable = Mp4MetadataWriter.canWrite(context, value)
        var nativeWritten = false
        var nativeFailure: String? = null
        if (nativeAvailable) {
            try {
                val cover = if (draft.thumbnailChanged && draft.thumbnailUri.isNotBlank()) {
                    context.contentResolver.openInputStream(draft.thumbnailUri.toUri())?.use { it.readBytes() }
                } else null
                Mp4MetadataWriter.write(context, value, draft, cover,
                    removeCover = draft.thumbnailChanged && draft.thumbnailUri.isBlank())
                nativeWritten = true
            } catch (error: Exception) {
                nativeFailure = error.message ?: "No se pudo escribir el archivo"
            }
        }
        // Always keep a copy in the app database. This also preserves description and
        // thumbnail choices for MKV/WebM/providers that cannot be edited in place.
        store.save(value, draft, nativeWritten)
        return VideoMetadataSaveResult(nativeWritten, nativeFailureMessage = nativeFailure)
    }

    fun saveImageFromGallery(value: String, imageUri: Uri): String =
        artwork.saveFromUri(context, value, imageUri)

    fun saveVideoFrame(value: String, bitmap: Bitmap): String = artwork.saveBitmap(value, bitmap)

    private data class NativeFields(
        val read: Boolean = false,
        val title: String = "",
        val author: String = "",
        val date: String = "",
        val genre: String = "",
    )

    private fun readNative(value: String): NativeFields {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, value.toUri())
            fun text(key: Int): String = retriever.extractMetadata(key).orEmpty()
            NativeFields(
                read = true,
                title = text(MediaMetadataRetriever.METADATA_KEY_TITLE),
                author = text(MediaMetadataRetriever.METADATA_KEY_AUTHOR).ifBlank {
                    text(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                },
                date = text(MediaMetadataRetriever.METADATA_KEY_DATE),
                genre = text(MediaMetadataRetriever.METADATA_KEY_GENRE),
            )
        } catch (_: RuntimeException) {
            NativeFields()
        } finally {
            retriever.release()
        }
    }
}
