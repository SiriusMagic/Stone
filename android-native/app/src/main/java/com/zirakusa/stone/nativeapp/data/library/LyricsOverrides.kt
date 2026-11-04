package com.zirakusa.stone.nativeapp.data.library

import android.content.Context
import com.zirakusa.stone.nativeapp.data.persistence.StoneDatabase
import org.json.JSONObject

/** User-entered lyrics are durable data and intentionally live outside the disposable library cache. */
class LyricsOverrides(context: Context) {
    private val database = StoneDatabase.get(context)
    fun get(songId: String): String? = runCatching { JSONObject(database.getString("lyrics") ?: "{}").optString(songId)
        .takeIf(String::isNotEmpty) }.getOrNull()
    fun put(songId: String, value: String) {
        val lyrics = runCatching { JSONObject(database.getString("lyrics") ?: "{}") }.getOrDefault(JSONObject())
        database.putString("lyrics", lyrics.put(songId, value).toString())
    }
    fun delete(songId: String) {
        val lyrics = runCatching { JSONObject(database.getString("lyrics") ?: "{}") }.getOrDefault(JSONObject())
        database.putString("lyrics", lyrics.apply { remove(songId) }.toString())
    }

    fun offset(songId: String): Long = runCatching {
        JSONObject(database.getString("lyrics-offsets") ?: "{}").optLong(songId, 0L)
    }.getOrDefault(0L)

    fun putOffset(songId: String, value: Long) {
        val offsets = runCatching { JSONObject(database.getString("lyrics-offsets") ?: "{}") }
            .getOrDefault(JSONObject())
        database.putString("lyrics-offsets", offsets.put(songId, value.coerceIn(-120_000L, 120_000L)).toString())
    }

    fun deleteOffset(songId: String) {
        val offsets = runCatching { JSONObject(database.getString("lyrics-offsets") ?: "{}") }
            .getOrDefault(JSONObject())
        database.putString("lyrics-offsets", offsets.apply { remove(songId) }.toString())
    }
}
