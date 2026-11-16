package com.zirakusa.stone.nativeapp.data.library

import com.zirakusa.stone.core.LyricLine
import com.zirakusa.stone.core.LyricWord
import com.zirakusa.stone.core.Song
import com.zirakusa.stone.core.LyricsParser
import org.json.JSONArray
import org.json.JSONObject

/** Stable representation shared by the library cache and legacy app-private media index. */
object SongJson {
    fun toJson(song: Song): JSONObject = JSONObject().apply {
        put("uri", song.uri)
        put("title", song.title)
        put("artist", song.artist)
        put("album", song.album)
        put("folder", song.folder)
        put("duration", song.durationMs)
        put("size", song.size)
        put("modified", song.modifiedMs)
        put("isVideo", song.isVideo)
        put("artworkUri", song.artworkUri)
        put("genre", song.genre)
        put("lyrics", JSONArray(song.lyrics))
        put("syncedLyrics", JSONArray().apply {
            song.syncedLyrics.forEach { put(lineJson(it)) }
        })
    }

    /** One timed line; word timings (enhanced LRC) only when present, so old caches stay readable. */
    fun lineJson(line: LyricLine): JSONObject = JSONObject().put("timeMs", line.timeMs).put("text", line.text).apply {
        if (line.words.isNotEmpty()) put("words", JSONArray().apply {
            line.words.forEach { put(JSONObject().put("timeMs", it.timeMs).put("text", it.text)) }
        })
    }

    fun line(item: JSONObject): LyricLine = LyricLine(item.optLong("timeMs"), item.optString("text"),
        item.optJSONArray("words")?.let { words -> List(words.length()) {
            words.getJSONObject(it).let { word -> LyricWord(word.optLong("timeMs"), word.optString("text")) }
        } } ?: emptyList())

    fun fromJson(item: JSONObject): Song = Song(
        item.getString("uri"), item.optString("title", ""), item.optString("artist", ""),
        item.optString("album", ""), item.optString("folder", ""), item.optLong("duration", 0),
        item.optLong("size", 0), item.optLong("modified", 0),
        item.optJSONArray("lyrics")?.let { values -> List(values.length()) { values.getString(it) } }
            ?: emptyList(),
        item.optJSONArray("syncedLyrics")?.let { values -> List(values.length()) {
            line(values.getJSONObject(it))
        } } ?: emptyList(), item.optBoolean("isVideo", false), item.optString("artworkUri", ""),
        item.optString("genre", ""))

    fun lrc(song: Song): String = if (song.syncedLyrics.isNotEmpty()) {
        LyricsParser.toLrc(song.syncedLyrics)
    } else song.lyrics.joinToString("\n")
}
