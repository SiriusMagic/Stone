package com.zirakusa.stone.nativeapp.data.library.lyrics

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import com.zirakusa.stone.core.Song
import com.zirakusa.stone.nativeapp.data.persistence.StoneDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.coroutines.coroutineContext

/**
 * Which songs have lyrics, for the "LETRAS" badge, the "Con letras" list and sorting. Combines
 * lyrics saved in Stone (an empty one means deleted), lyrics read while scanning folders, and the
 * ID3 lyrics of files found by the device scan, read once in the background and cached.
 */
object LyricsIndex {
    /** Song id → whether the lyrics saved in Stone have text. */
    var saved by mutableStateOf(emptyMap<String, Boolean>())
        private set
    /** Ids whose file carries lyrics (from the background read). */
    var embedded by mutableStateOf(emptySet<String>())
        private set
    private var loaded = false
    private const val CACHE = "lyrics.embedded.v1"

    fun has(song: Song): Boolean = !song.isVideo && (saved[song.id] ?: (song.id in embedded ||
        song.lyrics.any(String::isNotBlank) || song.syncedLyrics.any { it.text.isNotBlank() }))

    /** For queue entries, which only carry the id. */
    fun has(id: String): Boolean = saved[id] ?: (id in embedded)

    fun ensureLoaded(context: Context) {
        if (loaded) return
        loaded = true
        saved = runCatching {
            val json = JSONObject(StoneDatabase.get(context).getString("lyrics") ?: "{}")
            json.keys().asSequence().associateWith { json.optString(it).isNotBlank() }
        }.getOrDefault(emptyMap())
    }

    /** Called by [LyricsOverrides] whenever Stone saves or deletes lyrics. */
    fun onSaved(id: String, text: String?) {
        saved = if (text == null) saved - id else saved + (id to text.isNotBlank())
    }

    /** Reads the ID3 lyrics of audio files that are not cached yet; cheap after the first run. */
    suspend fun scanEmbedded(context: Context, songs: List<Song>) = withContext(Dispatchers.IO) {
        val database = StoneDatabase.get(context)
        val cache = runCatching { JSONObject(database.getString(CACHE) ?: "{}") }.getOrDefault(JSONObject())
        val reader = EmbeddedLyricsReader(context)
        val audio = songs.filter { !it.isVideo }
        var changed = false
        var sinceSave = 0
        audio.forEach { song ->
            coroutineContext.ensureActive()
            val stamp = "${song.modifiedMs}:${song.size}"
            if (cache.optJSONObject(song.id)?.optString("stamp") == stamp) return@forEach
            val found = song.lyrics.any(String::isNotBlank) || song.syncedLyrics.isNotEmpty() ||
                runCatching { reader.read(song.uri.toUri()).isNotBlank() }.getOrDefault(false)
            cache.put(song.id, JSONObject().put("stamp", stamp).put("lyrics", found))
            changed = true
            if (++sinceSave >= 50) { database.putString(CACHE, cache.toString()); sinceSave = 0; publish(cache, audio) }
        }
        if (changed) database.putString(CACHE, cache.toString())
        publish(cache, audio)
    }

    private fun publish(cache: JSONObject, audio: List<Song>) {
        embedded = audio.filter { cache.optJSONObject(it.id)?.optBoolean("lyrics") == true }.mapTo(HashSet()) { it.id }
    }
}
