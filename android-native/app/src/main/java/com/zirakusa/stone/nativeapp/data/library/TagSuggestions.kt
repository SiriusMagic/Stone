package com.zirakusa.stone.nativeapp.data.library

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** A possible set of tags for a song, with how sure Stone is that it is the same song (0-1). */
data class TagCandidate(val title: String, val artist: String, val album: String, val genre: String,
    val year: String, val coverUrl: String, val durationSeconds: Double, val confidence: Double)

/**
 * "Completar etiquetas": looks the song up in Apple's public iTunes Search catalogue (no key) and
 * returns title, artist, album, genre, year and a 600 px cover, best match first.
 */
object TagSuggestions {
    fun find(title: String, artist: String, album: String, durationMs: Long, limit: Int = 8): List<TagCandidate> {
        val query = LyricsMatcher.query(title, artist, album, durationMs)
        val term = URLEncoder.encode("${query.title} ${query.artist}".trim(), "UTF-8")
        val connection = URL("https://itunes.apple.com/search?media=music&entity=song&limit=15&term=$term")
            .openConnection() as HttpURLConnection
        val body = try {
            connection.connectTimeout = 10_000; connection.readTimeout = 15_000
            connection.setRequestProperty("User-Agent", "Stone music player")
            if (connection.responseCode != 200) error("iTunes answered HTTP ${connection.responseCode}")
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally { connection.disconnect() }
        val results = JSONObject(body).optJSONArray("results") ?: return emptyList()
        return (0 until results.length()).map { results.getJSONObject(it) }.map { item ->
            val seconds = item.optLong("trackTimeMillis") / 1000.0
            val name = item.optString("trackName"); val by = item.optString("artistName")
            TagCandidate(name, by, item.optString("collectionName"), item.optString("primaryGenreName"),
                item.optString("releaseDate").take(4), item.optString("artworkUrl100").replace("100x100bb", "600x600bb"),
                seconds, LyricsMatcher.score(query, name, by, seconds))
        }.filter { it.title.isNotBlank() }.distinctBy { "${it.title}|${it.artist}|${it.album}" }
            .sortedByDescending { it.confidence }.take(limit)
    }
}
