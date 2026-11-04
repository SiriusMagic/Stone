package com.zirakusa.stone.nativeapp.data.library

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri

data class AudioTechnicalInfo(val name: String, val format: String, val size: String,
    val bytes: String, val duration: String, val bitrate: String, val sampleRate: String,
    val channels: String, val mime: String, val modified: String, val location: String)

object AudioTechnicalReader {
    fun read(context: Context, value: String): AudioTechnicalInfo {
        val uri = Uri.parse(value)
        val name = uri.lastPathSegment?.substringAfterLast('/') ?: "No disponible"
        val ext = name.substringAfterLast('.', "").uppercase().ifBlank { "No disponible" }
        val size = runCatching { context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L }.getOrDefault(-1L)
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val text = { key: Int -> retriever.extractMetadata(key) ?: "No disponible" }
            val bytes = if (size >= 0) size.toString() else "No disponible"
            val friendly = if (size < 0) "No disponible" else when {
                size >= 1_073_741_824 -> "%.2f GB".format(size / 1_073_741_824.0)
                size >= 1_048_576 -> "%.2f MB".format(size / 1_048_576.0)
                else -> "%.1f KB".format(size / 1024.0)
            }
            AudioTechnicalInfo(name, ext, friendly, bytes,
                text(MediaMetadataRetriever.METADATA_KEY_DURATION).let { if (it == "No disponible") it else "%d:%02d".format((it.toLong()/60000), (it.toLong()/1000)%60) },
                text(MediaMetadataRetriever.METADATA_KEY_BITRATE).let { if (it == "No disponible") it else "${it.toLong()/1000} kbps" },
                text(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE).let { if (it == "No disponible") it else "$it Hz" },
                "No disponible", context.contentResolver.getType(uri) ?: "No disponible",
                "No disponible", value)
        } catch (_: Exception) {
            AudioTechnicalInfo(name, ext, "No disponible", "No disponible", "No disponible", "No disponible", "No disponible", "No disponible", "No disponible", "No disponible", value)
        } finally { retriever.release() }
    }
}
