package com.zirakusa.stone.nativeapp.automation.actions

import com.zirakusa.stone.nativeapp.data.library.web.RateLimitedException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import com.zirakusa.stone.nativeapp.automation.server.McpServer

/**
 * Background batches for agents. A long batch through the relay used to time out; now the tool
 * answers at once with a jobId and the work goes on here, one song at a time, with a result per
 * song. When lrclib.net or iTunes answer 429 the job waits (longer each time) and resumes alone;
 * after too many waits it stops as "paused" and stone_job_resume continues where it was.
 */
internal object StoneJobs {
    /** Batches above this size run as a job automatically (5-10 songs is what services tolerate). */
    const val SYNC_LIMIT = 10
    private const val MAX_WAITS = 6
    private const val MAX_JOBS = 20
    /** Shortest wait after a 429 (tests make it tiny). */
    @Volatile internal var minWaitMs = 5_000L

    /** [owner] is the token id that started the job ("" = Stone itself): only it can read, resume or cancel it. */
    class Entry(val id: String, val tool: String, val items: List<BatchItem>, val locked: Set<String>,
        val work: (BatchItem, String) -> JSONObject, val owner: String = "") {
        val batch = UUID.randomUUID().toString().take(8)
        val results = mutableListOf<JSONObject>()
        val startedAt = System.currentTimeMillis()
        @Volatile var status = "running"
        @Volatile var resumeAt = 0L
        @Volatile var waits = 0
        var runner: Job? = null
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = LinkedHashMap<String, Entry>()

    fun start(tool: String, items: List<BatchItem>, locked: Set<String> = emptySet(), owner: String = "",
        work: (BatchItem, String) -> JSONObject): JSONObject {
        val entry = Entry(UUID.randomUUID().toString().take(8), tool, items, locked, work, owner)
        synchronized(jobs) {
            jobs[entry.id] = entry
            while (jobs.size > MAX_JOBS) jobs.remove(jobs.keys.first { jobs[it]?.status != "running" } ?: break)
        }
        launch(entry)
        return status(entry.id, 0, owner).put("message", "Running in the background. Poll stone_job_status jobId=${entry.id} " +
            "every 10-20 s; each song gets its own result.")
    }

    private fun launch(entry: Entry) {
        entry.status = "running"
        entry.runner = scope.launch {
            while (isActive) {
                val index = synchronized(entry.results) { entry.results.size }
                if (index >= entry.items.size) break
                // A job never outlives its token: revoked, paused or expired stops it before the next song.
                if (entry.owner.isNotEmpty() && !McpServer.isActive(entry.owner)) { entry.status = "cancelled"; break }
                val item = entry.items[index]
                StoneAutomation.reportProgress(BatchProgress(entry.tool, index, entry.items.size))
                val result = try {
                    MetadataBatch.runItem(item, false, entry.locked, null, entry.work, entry.batch)
                } catch (limited: RateLimitedException) {
                    if (++entry.waits > MAX_WAITS) { entry.status = "paused"; break }
                    // Progressive wait: what the service asked, doubled on every new block, at most 5 min.
                    val wait = (limited.retryAfterSeconds * 1000L shl (entry.waits - 1)).coerceIn(minWaitMs, 300_000)
                    entry.status = "waiting"; entry.resumeAt = System.currentTimeMillis() + wait
                    delay(wait); entry.status = "running"
                    continue
                }
                synchronized(entry.results) { entry.results += result }
            }
            StoneAutomation.reportProgress(null)
            if (entry.status == "running") entry.status = "done"
            if (entry.results.any { it.optBoolean("ok") }) runCatching {
                withContext(Dispatchers.Main) { MetadataBatch.host().refreshLibrary() }
            }
        }
    }

    fun cancel(id: String, owner: String = ""): JSONObject {
        val entry = find(id, owner)
        entry.runner?.cancel(); entry.status = "cancelled"
        return status(id, 0, owner)
    }

    fun resume(id: String, owner: String = ""): JSONObject {
        val entry = find(id, owner)
        if (entry.status == "running" || entry.status == "waiting") return status(id, 0, owner)
        entry.waits = 0; launch(entry)
        return status(id, 0, owner)
    }

    /** Another token's job is reported exactly like a missing one, so ids reveal nothing. */
    private fun find(id: String, owner: String) = synchronized(jobs) { jobs[id] }?.takeIf { owner.isEmpty() || it.owner == owner }
        ?: throw ActionException("No job with id '$id'.", "NOT_FOUND", "stone_job_status without jobId lists the jobs.")

    /** Progress, per-song results from [since] on, and what is left. */
    fun status(id: String, since: Int, owner: String = ""): JSONObject {
        val entry = find(id, owner)
        val results = synchronized(entry.results) { entry.results.toList() }
        val ok = results.count { it.optBoolean("ok") }
        return JSONObject().put("jobId", entry.id).put("tool", entry.tool).put("status", entry.status)
            .put("done", results.size).put("total", entry.items.size)
            .put("percent", if (entry.items.isEmpty()) 100 else results.size * 100 / entry.items.size)
            .put("ok", ok).put("failed", results.size - ok).put("elapsedSeconds", (System.currentTimeMillis() - entry.startedAt) / 1000)
            .apply {
                if (entry.status == "waiting") put("resumesInSeconds", ((entry.resumeAt - System.currentTimeMillis()) / 1000).coerceAtLeast(0))
                if (entry.status == "paused") put("hint", "The service kept limiting requests. Call stone_job_resume jobId=${entry.id} in a few minutes.")
                if (ok > 0) put("batchId", entry.batch).put("undo", "metadata_undo batchId=${entry.batch}")
                if (entry.status != "done") put("remainingSongIds", JSONArray(entry.items.drop(results.size).map { it.songId }))
            }
            .put("results", JSONArray(results.drop(since.coerceAtLeast(0))))
    }

    fun list(owner: String = ""): JSONObject = JSONObject().put("jobs", JSONArray(synchronized(jobs) {
        jobs.values.filter { owner.isEmpty() || it.owner == owner }.map { it.id }
    }.reversed().map { id -> status(id, Int.MAX_VALUE, owner).apply { remove("results"); remove("remainingSongIds") } }))
}
