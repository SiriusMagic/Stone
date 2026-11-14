package com.zirakusa.stone.nativeapp.data.library

import android.content.Context
import com.zirakusa.stone.core.Song
import com.zirakusa.stone.nativeapp.data.persistence.StoneDatabase
import org.json.JSONArray
import org.json.JSONObject

data class StonePlaylist(val id: String, val name: String, val songIds: List<String>)

class LibraryUserStore(context: Context) {
    private val database = StoneDatabase.get(context)
    fun playlists(): List<StonePlaylist> = runCatching {
        val values = JSONArray(database.getString("library.playlists") ?: "[]")
        List(values.length()) { index -> values.getJSONObject(index).let { item ->
            val ids = item.getJSONArray("songIds")
            StonePlaylist(item.getString("id"), item.getString("name"), List(ids.length()) { ids.getString(it) })
        } }
    }.getOrDefault(emptyList())
    fun createPlaylist(name: String): StonePlaylist = StonePlaylist("custom-${System.currentTimeMillis()}",
        name.trim(), emptyList()).also { savePlaylists(listOf(it) + playlists()) }
    fun add(playlistId: String, songId: String) = savePlaylists(playlists().map { if (it.id == playlistId)
        it.copy(songIds = (it.songIds + songId).distinct()) else it })
    fun remove(playlistId: String, songId: String) = savePlaylists(playlists().map { if (it.id == playlistId)
        it.copy(songIds = it.songIds - songId) else it })
    fun recordPlay(songId: String) {
        val recent = recentIds().toMutableList().apply { remove(songId); add(0, songId) }.take(100)
        database.putString("library.recent", JSONArray(recent).toString())
        database.putString("library.count:$songId", (playCount(songId) + 1).toString())
    }
    fun recentIds(): List<String> = runCatching { JSONArray(database.getString("library.recent") ?: "[]").let {
        array -> List(array.length()) { array.getString(it) } } }.getOrDefault(emptyList())
    fun playCount(id: String) = database.getString("library.count:$id")?.toIntOrNull() ?: 0
    /** One database read, with no zero-count entries retained in the UI state. */
    fun playCounts(songs: List<Song>): Map<String, Int> {
        if (songs.isEmpty()) return emptyMap()
        val visibleIds = songs.mapTo(HashSet(songs.size)) { it.id }
        return buildMap {
            database.forEachValueStartingWith("library.count:") { key, value ->
                val id = key.removePrefix("library.count:")
                val count = value.toIntOrNull() ?: 0
                if (id in visibleIds && count > 0) put(id, count)
            }
        }
    }
    fun favoriteIds(): Set<String> = runCatching { JSONArray(database.getString("favorites") ?: "[]").let {
        array -> List(array.length()) { array.getString(it) }.toSet() } }.getOrDefault(emptySet())

    /** Covers are keyed separately so built-in automatic playlists can also be customized. */
    fun playlistCovers(): Map<String, String> = buildMap {
        database.forEachValueStartingWith("library.playlist.cover:") { key, value ->
            if (value.isNotBlank()) put(key.removePrefix("library.playlist.cover:"), value)
        }
    }

    fun setPlaylistCover(playlistId: String, uri: String?) {
        val key = "library.playlist.cover:$playlistId"
        if (uri.isNullOrBlank()) database.remove(key) else database.putString(key, uri)
    }

    /** Rebinds user data from source URIs to the private files created by a .musp import. */
    fun remapSongIds(mapping: Map<String, String>) {
        if (mapping.isEmpty()) return
        val favorites = favoriteIds().map { mapping[it] ?: it }.distinct()
        database.putString("favorites", JSONArray(favorites).toString())
        val recent = recentIds().map { mapping[it] ?: it }.distinct()
        database.putString("library.recent", JSONArray(recent).toString())
        savePlaylists(playlists().map { playlist ->
            playlist.copy(songIds = playlist.songIds.map { mapping[it] ?: it }.distinct())
        })
        database.keys("library.count:").forEach { key ->
            val oldId = key.removePrefix("library.count:")
            mapping[oldId]?.let { newId ->
                database.getString(key)?.let { database.putString("library.count:$newId", it) }
                database.remove(key)
            }
        }
    }
    private fun savePlaylists(playlists: List<StonePlaylist>) {
        val array = JSONArray(); playlists.forEach { array.put(JSONObject().put("id", it.id)
            .put("name", it.name).put("songIds", JSONArray(it.songIds))) }
        database.putString("library.playlists", array.toString())
    }
}
