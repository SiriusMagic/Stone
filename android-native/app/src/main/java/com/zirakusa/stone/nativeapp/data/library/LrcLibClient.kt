package com.zirakusa.stone.nativeapp.data.library

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Lyrics found online: synced LRC when available, else plain text. */
data class FoundLyrics(val text: String, val synced: Boolean, val source: String)

/** Free public lyrics database lrclib.net (no key). Exact match first, then a search. */
object LrcLibClient {
    private const val BASE = "https://lrclib.net/api"

    fun find(title: String, artist: String, album: String, durationMs: Long): FoundLyrics? {
        val exact = buildList {
            add("track_name" to title); add("artist_name" to artist)
            if (album.isNotBlank()) add("album_name" to album)
            if (durationMs > 0) add("duration" to (durationMs / 1000).toString())
        }
        get("/get", exact)?.let { JSONObject(it) }?.let(::pick)?.let { return it }
        val results = get("/search", listOf("track_name" to title, "artist_name" to artist))?.let { JSONArray(it) } ?: return null
        return (0 until results.length()).asSequence().mapNotNull { pick(results.getJSONObject(it)) }.firstOrNull()
    }

    private fun pick(item: JSONObject): FoundLyrics? {
        if (item.optBoolean("instrumental")) return null
        val synced = item.optString("syncedLyrics").takeIf { it.isNotBlank() && it != "null" }
        val plain = item.optString("plainLyrics").takeIf { it.isNotBlank() && it != "null" }
        return synced?.let { FoundLyrics(it, true, "lrclib.net") } ?: plain?.let { FoundLyrics(it, false, "lrclib.net") }
    }

    private fun get(path: String, query: List<Pair<String, String>>): String? {
        val params = query.filter { it.second.isNotBlank() }
            .joinToString("&") { (key, value) -> "$key=${URLEncoder.encode(value, "UTF-8")}" }
        val connection = URL("$BASE$path?$params").openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("User-Agent", "Stone music player")
            when (connection.responseCode) {
                200 -> connection.inputStream.bufferedReader().use { it.readText() }
                404 -> null
                else -> error("lrclib.net answered HTTP ${connection.responseCode}")
            }
        } finally { connection.disconnect() }
    }
}
