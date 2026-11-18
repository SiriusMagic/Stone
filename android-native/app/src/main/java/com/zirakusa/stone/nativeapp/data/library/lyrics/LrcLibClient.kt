package com.zirakusa.stone.nativeapp.data.library.lyrics

import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import com.zirakusa.stone.nativeapp.data.library.web.WebPacer

/** Lyrics found online: synced LRC when available, else plain text. */
data class FoundLyrics(val text: String, val synced: Boolean, val source: String)

/** One lrclib.net result scored against the song ([confidence] 0..1, see [LyricsMatcher]). */
data class LyricsCandidate(val id: Long, val title: String, val artist: String, val album: String,
    val durationSeconds: Double, val syncedLyrics: String?, val plainLyrics: String?, val confidence: Double) {
    val synced: Boolean get() = !syncedLyrics.isNullOrBlank()
    val text: String get() = syncedLyrics?.takeIf { it.isNotBlank() } ?: plainLyrics.orEmpty()
    fun found() = FoundLyrics(text, synced, "lrclib.net")
}

/** Free public lyrics database lrclib.net (no key): exact lookup plus searches, best match first. */
object LrcLibClient {
    private const val BASE = "https://lrclib.net/api"

    /** Best match when it is at least [minConfidence], else null. */
    fun find(title: String, artist: String, album: String, durationMs: Long,
        minConfidence: Double = LyricsMatcher.CONFIDENT): FoundLyrics? =
        candidates(LyricsMatcher.query(title, artist, album, durationMs)).firstOrNull { it.confidence >= minConfidence }?.found()

    /** Every result with lyrics for [query], highest confidence first (synced wins a tie). */
    fun candidates(query: LyricsQuery, limit: Int = 8): List<LyricsCandidate> {
        val raw = buildList {
            if (query.artist.isNotEmpty()) get("/get", listOf("track_name" to query.title, "artist_name" to query.artist,
                "album_name" to query.album, "duration" to if (query.durationMs > 0) (query.durationMs / 1000).toString() else ""))
                ?.let { add(JSONObject(it)) }
            if (query.artist.isNotEmpty()) addAll(array(get("/search", listOf("track_name" to query.title, "artist_name" to query.artist))))
            if (size < 3) addAll(array(get("/search", listOf("q" to "${query.title} ${query.artist}".trim()))))
        }
        return raw.mapNotNull { parse(it, query) }.distinctBy { it.id }
            .sortedWith(compareByDescending<LyricsCandidate> { it.confidence }.thenByDescending { it.synced })
            .take(limit)
    }

    /** One record by its lrclib id (when an agent picked a candidate). */
    fun byId(id: Long): LyricsCandidate? = get("/get/$id", emptyList())?.let { parse(JSONObject(it), null) }

    private fun parse(item: JSONObject, query: LyricsQuery?): LyricsCandidate? {
        if (item.optBoolean("instrumental")) return null
        fun text(key: String) = item.optString(key).takeIf { it.isNotBlank() && it != "null" }
        val synced = text("syncedLyrics"); val plain = text("plainLyrics")
        if (synced == null && plain == null) return null
        val title = item.optString("trackName"); val artist = item.optString("artistName")
        val duration = item.optDouble("duration", 0.0)
        return LyricsCandidate(item.optLong("id"), title, artist, item.optString("albumName"), duration, synced, plain,
            query?.let { LyricsMatcher.score(it, title, artist, duration) } ?: 1.0)
    }

    private fun array(text: String?): List<JSONObject> = text?.let { JSONArray(it) }
        ?.let { list -> (0 until list.length()).map { list.getJSONObject(it) } }.orEmpty()

    private fun get(path: String, query: List<Pair<String, String>>): String? {
        val params = query.filter { it.second.isNotBlank() }
            .joinToString("&") { (key, value) -> "$key=${URLEncoder.encode(value, "UTF-8")}" }
        // Paced and shared: many songs in a row (or two agents at once) never burst into a 429.
        return WebPacer.lrclib.call { WebPacer.get("$BASE$path" + if (params.isEmpty()) "" else "?$params") }
    }
}
