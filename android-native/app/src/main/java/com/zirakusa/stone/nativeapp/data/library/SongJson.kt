package com.zirakusa.stone.nativeapp.data.library

import com.zirakusa.stone.core.LyricLine
import com.zirakusa.stone.core.Song
import com.zirakusa.stone.core.LyricsParser
import org.json.JSONArray
import org.json.JSONObject

/** One stable representation shared by the cache, backup and portable Stone package. */
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
        put("lyrics", JSONArray(song.lyrics))
        put("syncedLyrics", JSONArray().apply {
            song.syncedLyrics.forEach { put(JSONObject().put("timeMs", it.timeMs).put("text", it.text)) }
        })
    }

    fun fromJson(item: JSONObject): Song = Song(
        item.getString("uri"), item.optString("title", ""), item.optString("artist", ""),
        item.optString("album", ""), item.optString("folder", ""), item.optLong("duration", 0),
        item.optLong("size", 0), item.optLong("modified", 0),
        item.optJSONArray("lyrics")?.let { values -> List(values.length()) { values.getString(it) } }
            ?: emptyList(),
        item.optJSONArray("syncedLyrics")?.let { values -> List(values.length()) {
            values.getJSONObject(it).let { line -> LyricLine(line.optLong("timeMs"), line.optString("text")) }
        } } ?: emptyList(), item.optBoolean("isVideo", false), item.optString("artworkUri", ""))

    fun lrc(song: Song): String = if (song.syncedLyrics.isNotEmpty()) {
        LyricsParser.toLrc(song.syncedLyrics)
    } else song.lyrics.joinToString("\n")
}
