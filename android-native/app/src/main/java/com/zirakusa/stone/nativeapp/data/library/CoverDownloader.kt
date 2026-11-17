package com.zirakusa.stone.nativeapp.data.library

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.graphics.scale
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads a cover image from the internet (https only) and returns it as a JPEG of at most
 * 1600 px, ready to embed in a file. Anything that is not a decodable image is rejected.
 */
object CoverDownloader {
    private const val MAX_BYTES = 15 * 1024 * 1024
    private const val MAX_SIDE = 1600

    fun download(address: String): ByteArray {
        val url = runCatching { URL(address.trim()) }.getOrNull()
            // https only: plain http could only reach services on this phone (see network_security_config).
            ?.takeIf { it.protocol == "https" }
            ?: error("The cover must be an https image URL")
        var connection = url.openConnection() as HttpURLConnection
        var redirects = 0
        while (true) {
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("User-Agent", "Stone")
            connection.setRequestProperty("Accept", "image/*")
            val code = connection.responseCode
            if (code !in 300..399) break
            val next = connection.getHeaderField("Location") ?: break
            connection.disconnect()
            val target = URL(connection.url, next)
            if (target.protocol != "https") error("The cover URL redirects to a non-https address")
            if (++redirects > 5) error("The cover URL redirects too often")
            connection = target.openConnection() as HttpURLConnection
        }
        try {
            if (connection.responseCode !in 200..299) error("The cover URL answered HTTP ${connection.responseCode}")
            val bytes = connection.inputStream.use { input ->
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    out.write(buffer, 0, read)
                    if (out.size() > MAX_BYTES) error("The cover image is larger than 15 MB")
                }
                out.toByteArray()
            }
            return toJpeg(bytes)
        } finally { connection.disconnect() }
    }

    fun decode(bytes: ByteArray): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "The URL is not an image (JPEG, PNG or WebP)" }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_SIDE * 2) sample *= 2
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: error("The cover image could not be decoded")
        val scale = MAX_SIDE.toFloat() / maxOf(bitmap.width, bitmap.height)
        if (scale >= 1f) return bitmap
        return bitmap.scale((bitmap.width * scale).toInt().coerceAtLeast(1), (bitmap.height * scale).toInt().coerceAtLeast(1))
            .also { if (it !== bitmap) bitmap.recycle() }
    }

    private fun toJpeg(bytes: ByteArray): ByteArray {
        val bitmap = decode(bytes)
        return try {
            ByteArrayOutputStream().also { check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)) }.toByteArray()
        } finally { bitmap.recycle() }
    }
}
