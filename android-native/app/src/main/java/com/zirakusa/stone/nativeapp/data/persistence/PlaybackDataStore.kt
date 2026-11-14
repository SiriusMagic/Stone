package com.zirakusa.stone.nativeapp.data.persistence

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class SavedQueueItem(
    val id: String,
    val uri: String,
    val title: String,
    val artist: String,
    val album: String,
    /** Optional extracted cover used to rebuild system media artwork after restart. */
    val artworkUri: String = "",
    val modifiedMs: Long = 0L,
)
data class SavedPlayback(val items: List<SavedQueueItem>, val index: Int, val position: Long,
    val shuffle: Boolean, val repeat: Int)

class PlaybackDataStore(context: Context) {
    private val database = StoneDatabase.get(context)
    fun favorites(): MutableSet<String> = runCatching { JSONArray(database.getString("favorites") ?: "[]").let {
        array -> MutableList(array.length()) { index -> array.getString(index) }.toMutableSet() } }
        .getOrDefault(mutableSetOf())
    fun isFavorite(id: String) = id in favorites()
    fun toggleFavorite(id: String): Boolean {
        val values = favorites(); val enabled = if (id in values) { values.remove(id); false } else { values.add(id); true }
        database.putString("favorites", JSONArray(values).toString()); return enabled
    }
    fun string(key: String, fallback: String) = database.getString("tool.$key") ?: fallback
    fun float(key: String, fallback: Float) = string(key, fallback.toString()).toFloatOrNull() ?: fallback
    fun int(key: String, fallback: Int) = string(key, fallback.toString()).toIntOrNull() ?: fallback
    fun boolean(key: String, fallback: Boolean) = string(key, fallback.toString()).toBooleanStrictOrNull() ?: fallback
    fun put(key: String, value: Any) = database.putString("tool.$key", value.toString())

    fun saveQueue(value: SavedPlayback) {
        val items = JSONArray(); value.items.forEach { items.put(JSONObject().put("id", it.id).put("uri", it.uri)
            .put("title", it.title).put("artist", it.artist).put("album", it.album)
            .put("artworkUri", it.artworkUri).put("modifiedMs", it.modifiedMs)) }
        database.putString("playback.queue", JSONObject().put("items", items).put("index", value.index)
            .put("position", value.position).put("shuffle", value.shuffle).put("repeat", value.repeat).toString())
    }
    fun queue(): SavedPlayback? = runCatching {
        val root = JSONObject(database.getString("playback.queue") ?: return null)
        val items = root.getJSONArray("items")
        SavedPlayback(List(items.length()) { index -> items.getJSONObject(index).let { item -> SavedQueueItem(
            item.getString("id"), item.getString("uri"), item.optString("title"), item.optString("artist"),
            item.optString("album"), item.optString("artworkUri"), item.optLong("modifiedMs", 0L)) } },
            root.optInt("index", 0), root.optLong("position", 0), root.optBoolean("shuffle"), root.optInt("repeat"))
    }.getOrNull()
}
