package com.zirakusa.stone

import android.media.MediaMetadataRetriever
import android.net.Uri
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.WritableMap
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset
import java.security.MessageDigest

class StoneMediaMetadataModule(private val context: ReactApplicationContext) :
  ReactContextBaseJavaModule(context) {

  override fun getName() = "StoneMediaMetadata"

  @ReactMethod
  fun readMetadata(uri: String, promise: Promise) {
    try {
      promise.resolve(metadataMap(uri))
    } catch (_: Exception) {
      promise.resolve(null)
    }
  }

  @ReactMethod
  fun writeMetadata(uri: String, values: com.facebook.react.bridge.ReadableMap, promise: Promise) {
    try {
      val source = readBytes(uri)
      val updated = rewriteId3(source, mapOf(
        "TIT2" to values.stringOrEmpty("title"),
        "TPE1" to values.stringOrEmpty("artist"),
        "TALB" to values.stringOrEmpty("album"),
        "TCON" to values.stringOrEmpty("genre"),
        "TYER" to values.stringOrEmpty("year"),
        "TRCK" to values.stringOrEmpty("trackNumber"),
      ))
      writeBytes(uri, updated)
      promise.resolve(metadataMap(uri))
    } catch (error: Exception) {
      promise.reject("METADATA_WRITE_FAILED", "No se pudieron guardar las etiquetas del MP3.", error)
    }
  }

  private fun metadataMap(uri: String): WritableMap {
    val retriever = MediaMetadataRetriever()
    try {
      setSource(retriever, uri)
      return Arguments.createMap().apply {
      putString("title", valueOf(retriever, MediaMetadataRetriever.METADATA_KEY_TITLE))
      putString("artist", valueOf(retriever, MediaMetadataRetriever.METADATA_KEY_ARTIST)
        ?: valueOf(retriever, MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST))
      putString("album", valueOf(retriever, MediaMetadataRetriever.METADATA_KEY_ALBUM))
      putString("genre", valueOf(retriever, MediaMetadataRetriever.METADATA_KEY_GENRE))
      putString("year", valueOf(retriever, MediaMetadataRetriever.METADATA_KEY_YEAR))
      putString("trackNumber", valueOf(retriever, MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER))
      putInt("durationMs", valueOf(retriever, MediaMetadataRetriever.METADATA_KEY_DURATION)?.toIntOrNull() ?: 0)
      putString("artworkUri", writeArtwork(uri, retriever.embeddedPicture))
      putArray("lyrics", Arguments.fromList(readId3Lyrics(uri)))
      }
    } finally {
      retriever.release()
    }
  }

  private fun valueOf(retriever: MediaMetadataRetriever, key: Int) =
    retriever.extractMetadata(key)?.takeIf { it.isNotBlank() }

  private fun com.facebook.react.bridge.ReadableMap.stringOrEmpty(key: String) =
    if (hasKey(key) && !isNull(key)) getString(key).orEmpty() else ""

  private fun readBytes(value: String): ByteArray {
    val uri = Uri.parse(value)
    val stream = if (uri.scheme == "file") File(uri.path ?: error("Ruta invalida")).inputStream()
    else context.contentResolver.openInputStream(uri)
    return stream?.use { it.readBytes() } ?: error("No se pudo abrir el archivo")
  }

  private fun writeBytes(value: String, bytes: ByteArray) {
    val uri = Uri.parse(value)
    val stream = if (uri.scheme == "file") FileOutputStream(File(uri.path ?: error("Ruta invalida")))
    else context.contentResolver.openOutputStream(uri, "wt")
    stream?.use { it.write(bytes) } ?: error("El proveedor no permite escribir este archivo")
  }

  private fun rewriteId3(source: ByteArray, changes: Map<String, String>): ByteArray {
    val hasTag = source.size >= 10 && String(source, 0, 3, Charsets.ISO_8859_1) == "ID3"
    val version = if (hasTag && source[3].toInt() in 3..4) source[3].toInt() else 3
    val tagEnd = if (hasTag) (10 + syncSafe(source, 6)).coerceAtMost(source.size) else 0
    val existingBody = if (hasTag) source.copyOfRange(10, tagEnd) else ByteArray(0)
    val body = ByteArrayOutputStream().apply {
      write(preserveFrames(existingBody, version))
      changes.forEach { (frame, value) -> writeTextFrame(this, version, frame, value) }
    }.toByteArray()
    require(body.size <= 0x0fffffff) { "Etiquetas demasiado grandes" }
    return ByteArrayOutputStream(10 + body.size + source.size - tagEnd).apply {
      write("ID3".toByteArray(Charsets.ISO_8859_1))
      write(byteArrayOf(version.toByte(), 0, 0))
      write(syncSafeBytes(body.size))
      write(body)
      write(source, tagEnd, source.size - tagEnd)
    }.toByteArray()
  }

  private fun preserveFrames(body: ByteArray, version: Int): ByteArray {
    if (version !in 3..4) return ByteArray(0)
    val replaced = setOf("TIT2", "TPE1", "TALB", "TCON", "TYER", "TDRC", "TRCK")
    val kept = ByteArrayOutputStream()
    var offset = 0
    while (offset + 10 <= body.size) {
      val id = String(body, offset, 4, Charsets.ISO_8859_1)
      if (id.all { it == '\u0000' }) break
      val size = if (version == 4) syncSafe(body, offset + 4)
      else ByteBuffer.wrap(body, offset + 4, 4).order(ByteOrder.BIG_ENDIAN).int
      val end = offset + 10 + size
      if (size < 0 || end > body.size) break
      if (id !in replaced) kept.write(body, offset, end - offset)
      offset = end
    }
    return kept.toByteArray()
  }

  private fun writeTextFrame(out: ByteArrayOutputStream, version: Int, id: String, value: String) {
    if (value.isBlank()) return
    val text = if (version == 4) value.toByteArray(Charsets.UTF_8)
    else value.toByteArray(Charsets.UTF_16)
    val payload = byteArrayOf(if (version == 4) 3 else 1) + text
    out.write(id.toByteArray(Charsets.ISO_8859_1))
    out.write(if (version == 4) syncSafeBytes(payload.size) else intBytes(payload.size))
    out.write(byteArrayOf(0, 0))
    out.write(payload)
  }

  private fun intBytes(value: Int) = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(value).array()

  private fun syncSafeBytes(value: Int) = byteArrayOf(
    ((value shr 21) and 0x7f).toByte(),
    ((value shr 14) and 0x7f).toByte(),
    ((value shr 7) and 0x7f).toByte(),
    (value and 0x7f).toByte(),
  )

  private fun setSource(retriever: MediaMetadataRetriever, value: String) {
    val uri = Uri.parse(value)
    if (uri.scheme == "content") {
      context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { descriptor ->
        if (descriptor.length > 0) {
          retriever.setDataSource(descriptor.fileDescriptor, descriptor.startOffset, descriptor.length)
        } else {
          retriever.setDataSource(descriptor.fileDescriptor)
        }
      } ?: error("Cannot open media URI")
      return
    }
    if (uri.scheme == "file") retriever.setDataSource(uri.path) else retriever.setDataSource(context, uri)
  }

  private fun writeArtwork(uri: String, bytes: ByteArray?): String? {
    if (bytes == null || bytes.isEmpty()) return null
    val folder = File(context.filesDir, "stone-artwork").apply { mkdirs() }
    val name = hash(uri) + ".jpg"
    val file = File(folder, name)
    if (!file.exists()) FileOutputStream(file).use { it.write(bytes) }
    return Uri.fromFile(file).toString()
  }

  private fun readId3Lyrics(value: String): List<String> {
    val uri = Uri.parse(value)
    val stream = runCatching { context.contentResolver.openInputStream(uri) }.getOrNull() ?: return emptyList()
    return stream.use { input ->
      val header = ByteArray(10)
      if (input.read(header) != 10 || String(header, 0, 3) != "ID3") return@use emptyList()
      val version = header[3].toInt()
      val tagSize = syncSafe(header, 6).coerceAtMost(16 * 1024 * 1024)
      val tag = ByteArrayOutputStream(tagSize)
      val buffer = ByteArray(8192)
      var remaining = tagSize
      while (remaining > 0) {
        val count = input.read(buffer, 0, minOf(buffer.size, remaining))
        if (count <= 0) break
        tag.write(buffer, 0, count)
        remaining -= count
      }
      parseLyricsFrame(tag.toByteArray(), version)
    }
  }

  private fun parseLyricsFrame(tag: ByteArray, version: Int): List<String> {
    var offset = 0
    while (offset + 10 <= tag.size) {
      val id = String(tag, offset, 4, Charsets.ISO_8859_1)
      if (id.all { it == '\u0000' }) break
      val size = if (version >= 4) syncSafe(tag, offset + 4)
      else ByteBuffer.wrap(tag, offset + 4, 4).order(ByteOrder.BIG_ENDIAN).int
      if (size <= 0 || offset + 10 + size > tag.size) break
      if (id == "USLT") return decodeUnsyncedLyrics(tag.copyOfRange(offset + 10, offset + 10 + size))
      offset += 10 + size
    }
    return emptyList()
  }

  private fun decodeUnsyncedLyrics(frame: ByteArray): List<String> {
    if (frame.size < 5) return emptyList()
    val encoding = frame[0].toInt() and 0xff
    val charset = when (encoding) {
      1 -> Charsets.UTF_16
      2 -> Charsets.UTF_16BE
      3 -> Charsets.UTF_8
      else -> Charsets.ISO_8859_1
    }
    val separatorSize = if (encoding == 1 || encoding == 2) 2 else 1
    var start = 4
    while (start + separatorSize <= frame.size) {
      val separator = (0 until separatorSize).all { frame[start + it].toInt() == 0 }
      start += separatorSize
      if (separator) break
    }
    return String(frame, start.coerceAtMost(frame.size), frame.size - start.coerceAtMost(frame.size), charset)
      .replace("\u0000", "")
      .lines()
      .map { it.trim() }
      .filter { it.isNotEmpty() }
  }

  private fun syncSafe(bytes: ByteArray, offset: Int): Int =
    ((bytes[offset].toInt() and 0x7f) shl 21) or
      ((bytes[offset + 1].toInt() and 0x7f) shl 14) or
      ((bytes[offset + 2].toInt() and 0x7f) shl 7) or
      (bytes[offset + 3].toInt() and 0x7f)

  private fun hash(value: String): String =
    MessageDigest.getInstance("SHA-256")
      .digest(value.toByteArray())
      .joinToString("") { "%02x".format(it) }
}
