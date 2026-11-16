package com.zirakusa.stone.nativeapp.automation

import android.content.Context
import com.zirakusa.stone.core.Song
import com.zirakusa.stone.nativeapp.data.library.AudioTagEditor
import com.zirakusa.stone.nativeapp.data.library.RateLimitedException
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

    /**
     * The song with the tags its file has right now. The library index can lag behind an edit
     * (Android re-reads files later), so searches use this and need no rescan in between.
     */
    fun fresh(song: Song): Song = if (song.isVideo) song else runCatching {
        val tags = AudioTagEditor(context()).read(song.uri)
        song.copy(title = tags.title.ifBlank { song.title }, artist = tags.artist.ifBlank { song.artist },
            album = tags.album.ifBlank { song.album }, genre = tags.genre.ifBlank { song.genre })
    }.getOrDefault(song)

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
     * One item: the edit (or its preview) as a JSON result with ok=true, or ok=false with an error
     * code. A rate limit is not an item failure: it is thrown so the caller can wait and resume.
     */
    fun runItem(item: BatchItem, dryRun: Boolean, locked: Set<String>, preview: ((Song, JSONObject) -> JSONObject)?,
        work: (BatchItem, String) -> JSONObject, batch: String): JSONObject = try {
        val song = item.song ?: throw ActionException("No song or video with id '${item.songId}'.", "NOT_FOUND")
        if (!dryRun && song.id in locked && !song.isVideo) throw ActionException(
            "Android did not allow Stone to write '${song.title}'.", "ANDROID_CONSENT", MetadataEdits.ANDROID_HINT)
        (if (dryRun && preview != null) preview(song, item.change) else work(item, batch)).put("ok", true)
    } catch (limited: RateLimitedException) {
        throw limited
    } catch (failure: Exception) {
        JSONObject().put("id", item.songId).put("ok", false).put("error", ActionException.of(failure).json())
    }

    /**
     * Runs [work] for every item on a background thread with progress and one summary. Failed
     * items do not stop the batch; each result says why with an error code. If a web service
     * starts limiting requests the batch stops there and lists the songs still to do.
     */
    suspend fun run(tool: String, items: List<BatchItem>, dryRun: Boolean, locked: Set<String> = emptySet(),
        preview: (Song, JSONObject) -> JSONObject, work: (BatchItem, String) -> JSONObject): JSONObject {
        val batch = UUID.randomUUID().toString().take(8)
        val started = System.currentTimeMillis()
        var limited: RateLimitedException? = null
        val results = withContext(Dispatchers.IO) {
            val done = mutableListOf<JSONObject>()
            for ((index, item) in items.withIndex()) {
                if (!dryRun) StoneAutomation.reportProgress(BatchProgress(tool, index, items.size))
                try { done += runItem(item, dryRun, locked, preview, work, batch) }
                catch (stop: RateLimitedException) { limited = stop; break }
            }
            done
        }
        StoneAutomation.reportProgress(null)
        if (!dryRun && results.any { it.getBoolean("ok") }) host().refreshLibrary()
        return summary(results, dryRun, batch, started).apply {
            limited?.let { stop ->
                put("stoppedBy", ActionException.of(stop).json())
                put("retryAfterSeconds", stop.retryAfterSeconds)
                put("remainingSongIds", JSONArray(items.drop(results.size).map { it.songId }))
            }
        }
    }

    /**
     * Small batches answer directly; bigger ones (or background=true) become a [StoneJobs] job so
     * the relay never times out.
     */
    suspend fun runOrStart(tool: String, items: List<BatchItem>, args: JSONObject, locked: Set<String> = emptySet(),
        preview: (Song, JSONObject) -> JSONObject, work: (BatchItem, String) -> JSONObject): JSONObject {
        val dryRun = args.optBoolean("dryRun")
        val background = args.optBoolean("background", items.size > StoneJobs.SYNC_LIMIT)
        return if (background && !dryRun) StoneJobs.start(tool, items, locked, currentAuth().tokenId, work)
        else run(tool, items, dryRun, locked, preview, work)
    }

    val backgroundParam = ActionParam("background", "boolean",
        "Run as a background job and get a jobId (poll stone_job_status). Default: automatic above ${StoneJobs.SYNC_LIMIT} items.")

    fun summary(results: List<JSONObject>, dryRun: Boolean, batch: String, started: Long): JSONObject {
        val ok = results.count { it.getBoolean("ok") }
        return JSONObject().put("dryRun", dryRun).put("total", results.size)
            .put(if (dryRun) "wouldChange" else "updated", ok).put("failed", results.size - ok)
            .put("durationMs", System.currentTimeMillis() - started)
            .apply { if (!dryRun && ok > 0) put("batchId", batch).put("undo", "metadata_undo with batchId=$batch reverts this") }
            .put("results", JSONArray(results))
    }
}
