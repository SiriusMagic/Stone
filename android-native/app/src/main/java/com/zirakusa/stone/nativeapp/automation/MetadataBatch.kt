package com.zirakusa.stone.nativeapp.automation

import android.content.Context
import com.zirakusa.stone.core.Song
import com.zirakusa.stone.nativeapp.playback.StonePlaybackService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** One item of a batch: the target (null when the id is unknown) and its change set. */
internal class BatchItem(val songId: String, val song: Song?, val change: JSONObject)

/** Shared plumbing of the metadata tools: lookups, JSON lists, dry runs, progress and summaries. */
internal object MetadataBatch {
    fun host() = StoneAutomation.requireHost()
    fun context(): Context = StonePlaybackService.instance?.applicationContext
        ?: throw ActionException("Playback service not running; open Stone.", "STONE_CLOSED")
    fun song(id: String): Song = host().songs.firstOrNull { it.id == id }
        ?: throw ActionException("No song or video with id '$id'.", "NOT_FOUND", "Use library_search to get valid ids.")

    val dryRunParam = ActionParam("dryRun", "boolean", "true = only show what would change; nothing is written.")

    /** Parses a JSON array of objects that each have a songId and only [allowed] fields. */
    fun items(raw: String, allowed: Set<String>, max: Int = 500): List<BatchItem> {
        val list = runCatching { JSONArray(raw) }.getOrNull()
            ?: throw ActionException("'changes' must be a JSON array like [{\"songId\":\"…\",\"title\":\"…\"}]", "INVALID_PARAM")
        if (list.length() !in 1..max) throw ActionException("'changes' must contain between 1 and $max items.", "INVALID_PARAM")
        val songs = host().songs.associateBy { it.id }
        return (0 until list.length()).map { index ->
            val change = list.optJSONObject(index) ?: throw ActionException("Item $index is not an object.", "INVALID_PARAM")
            val id = change.optString("songId").ifBlank { throw ActionException("Item $index has no songId.", "INVALID_PARAM") }
            change.keys().forEach { key ->
                if (key != "songId" && key !in allowed) throw ActionException("Unknown field '$key' in item $index.", "INVALID_PARAM",
                    "Allowed: ${allowed.joinToString()}")
            }
            BatchItem(id, songs[id], change)
        }
    }

    fun ids(raw: String): List<Song> = raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }.distinct().map(::song)

    /**
     * Runs [work] for every item on a background thread with progress and one summary. Failed
     * items do not stop the batch; each result says why with an error code.
     */
    suspend fun run(tool: String, items: List<BatchItem>, dryRun: Boolean, locked: Set<String> = emptySet(),
        preview: (Song, JSONObject) -> JSONObject, work: (BatchItem, String) -> JSONObject): JSONObject {
        val batch = UUID.randomUUID().toString().take(8)
        val started = System.currentTimeMillis()
        val results = withContext(Dispatchers.IO) {
            items.mapIndexed { index, item ->
                if (!dryRun) StoneAutomation.reportProgress(BatchProgress(tool, index, items.size))
                try {
                    val song = item.song ?: throw ActionException("No song or video with id '${item.songId}'.", "NOT_FOUND")
                    if (!dryRun && song.id in locked && !song.isVideo) throw ActionException(
                        "Android did not allow Stone to write '${song.title}'.", "ANDROID_CONSENT", MetadataEdits.ANDROID_HINT)
                    (if (dryRun) preview(song, item.change) else work(item, batch)).put("ok", true)
                } catch (failure: Exception) {
                    val error = failure as? ActionException ?: ActionException(failure.message ?: "failed")
                    JSONObject().put("id", item.songId).put("ok", false).put("error", error.json())
                }
            }
        }
        StoneAutomation.reportProgress(null)
        if (!dryRun && results.any { it.getBoolean("ok") }) host().refreshLibrary()
        return summary(results, dryRun, batch, started)
    }

    fun summary(results: List<JSONObject>, dryRun: Boolean, batch: String, started: Long): JSONObject {
        val ok = results.count { it.getBoolean("ok") }
        return JSONObject().put("dryRun", dryRun).put("total", results.size)
            .put(if (dryRun) "wouldChange" else "updated", ok).put("failed", results.size - ok)
            .put("durationMs", System.currentTimeMillis() - started)
            .apply { if (!dryRun && ok > 0) put("batchId", batch).put("undo", "metadata_undo with batchId=$batch reverts this") }
            .put("results", JSONArray(results))
    }
}
