package com.zirakusa.stone.nativeapp.data.library

import android.content.Context
import android.util.AtomicFile
import com.zirakusa.stone.core.Song
import com.zirakusa.stone.core.LyricLine
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/** Disposable index only; user playlists and preferences must never live here. */
class LibraryCache(context: Context) {
    private val cacheDirectory = File(context.filesDir, "library-indices").apply { mkdirs() }
    private val legacyFile = AtomicFile(File(context.filesDir, "library-index.json"))

    private fun file(tree: String): AtomicFile {
        val key = MessageDigest.getInstance("SHA-256").digest(tree.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return AtomicFile(File(cacheDirectory, "$key.json"))
    }

    fun read(tree: String): List<Song> = read(file(tree), tree)
        .ifEmpty { read(legacyFile, tree) }

    private fun read(file: AtomicFile, tree: String): List<Song> = runCatching {
        val root = JSONObject(file.openRead().bufferedReader().use { it.readText() })
        if (root.optString("tree") != tree) return emptyList()
        val songs = root.getJSONArray("songs")
        List(songs.length()) { index ->
            val item = songs.getJSONObject(index)
            Song(item.getString("uri"), item.getString("title"), item.getString("artist"),
                item.getString("album"), item.getString("folder"), item.getLong("duration"),
                item.getLong("size"), item.getLong("modified"),
                item.optJSONArray("lyrics")?.let { values -> List(values.length()) { values.getString(it) } }
                    ?: emptyList(),
                item.optJSONArray("syncedLyrics")?.let { values -> List(values.length()) {
                    values.getJSONObject(it).let { line -> LyricLine(line.getLong("timeMs"), line.getString("text")) }
                } } ?: emptyList(), item.optBoolean("isVideo", false))
                .copy(artworkUri = item.optString("artworkUri", ""))
        }
    }.getOrDefault(emptyList())

    fun write(tree: String, songs: List<Song>) {
        val entries = JSONArray()
        songs.forEach { song ->
            entries.put(JSONObject().put("uri", song.uri).put("title", song.title)
                .put("artist", song.artist).put("album", song.album).put("folder", song.folder)
                .put("duration", song.durationMs).put("size", song.size).put("modified", song.modifiedMs)
                .put("isVideo", song.isVideo)
                .put("artworkUri", song.artworkUri)
                .put("lyrics", JSONArray(song.lyrics)).put("syncedLyrics", JSONArray().apply {
                    song.syncedLyrics.forEach { line -> put(JSONObject().put("timeMs", line.timeMs).put("text", line.text)) }
                }))
        }
        val bytes = JSONObject().put("tree", tree).put("songs", entries).toString().toByteArray()
        val file = file(tree)
        val stream = file.startWrite()
        try { stream.write(bytes); file.finishWrite(stream) }
        catch (error: Exception) { file.failWrite(stream); throw error }
    }
}
