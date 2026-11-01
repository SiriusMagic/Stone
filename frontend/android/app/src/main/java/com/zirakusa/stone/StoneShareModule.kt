package com.zirakusa.stone

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import java.io.File

class StoneShareModule(private val context: ReactApplicationContext) : ReactContextBaseJavaModule(context) {
  override fun getName() = "StoneShare"

  @ReactMethod
  fun shareAudio(value: String, title: String, promise: Promise) {
    try {
      val source = Uri.parse(value)
      val uri = when (source.scheme) {
        "content" -> source
        "file" -> shareableFile(source, title)
        else -> error("La canción debe estar descargada para compartir el archivo")
      }
      val mime = context.contentResolver.getType(uri) ?: audioMime(value)
      val send = Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newUri(context.contentResolver, title, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }
      context.startActivity(Intent.createChooser(send, "Compartir canción").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
      promise.resolve(true)
    } catch (error: Exception) {
      promise.reject("SHARE_AUDIO_FAILED", error.message ?: "No se pudo compartir la canción", error)
    }
  }

  private fun shareableFile(uri: Uri, title: String): Uri {
    val source = File(uri.path ?: error("Ruta de archivo inválida"))
    val extension = source.extension.ifBlank { "mp3" }
    val safeName = title.replace(Regex("[^A-Za-z0-9 _-]"), "").take(60).ifBlank { "Stone audio" }
    val folder = File(context.cacheDir, "shared-audio").apply { mkdirs() }
    val target = File(folder, "$safeName.$extension")
    source.copyTo(target, overwrite = true)
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", target)
  }

  private fun audioMime(value: String) = when (value.substringAfterLast('.', "").lowercase()) {
    "m4a", "mp4" -> "audio/mp4"
    "wav" -> "audio/wav"
    "flac" -> "audio/flac"
    "ogg", "opus" -> "audio/ogg"
    else -> "audio/mpeg"
  }
}
