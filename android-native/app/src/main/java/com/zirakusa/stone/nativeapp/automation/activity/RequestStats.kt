package com.zirakusa.stone.nativeapp.automation.activity

import com.zirakusa.stone.nativeapp.data.persistence.StoneSettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Requests per minute for Ajustes › Modo MCP › Analítica: all agents together and each token on
 * its own, split into successful, denied and failed, for the last 24 hours. Calls rejected for a
 * missing or wrong token are counted apart (they belong to no token). Saved at most once a minute.
 */
object RequestStats {
    const val KEEP_MINUTES = 24 * 60
    const val OK = 0; const val DENIED = 1; const val FAILED = 2; const val REJECTED = 3
    private const val KEY = "api.stats"
    private const val ALL = "*"

    /** minute (epoch / 60 000) → counts [ok, denied, failed, rejected], per token id and for ALL. */
    private val minutes = HashMap<String, HashMap<Long, IntArray>>()
    private val mutableRevision = MutableStateFlow(0)
    /** Changes on every recorded request, so screens redraw without polling. */
    val revision = mutableRevision.asStateFlow()
    private var store: StoneSettingsStore? = null
    private var lastSaved = 0L

    fun attach(store: StoneSettingsStore) {
        this.store = store
        synchronized(minutes) { minutes.clear(); minutes.putAll(parse(store.look(KEY, "{}"))) }
    }

    fun record(tokenId: String, kind: Int, now: Long = System.currentTimeMillis()) {
        val minute = now / 60_000
        synchronized(minutes) {
            listOfNotNull(ALL, tokenId.takeIf { it.isNotBlank() }).forEach { key ->
                minutes.getOrPut(key) { HashMap() }.getOrPut(minute) { IntArray(4) }[kind]++
            }
            minutes.values.forEach { series -> series.keys.removeAll { it <= minute - KEEP_MINUTES } }
        }
        mutableRevision.value++
        if (now - lastSaved >= 60_000) { lastSaved = now; store?.let { runCatching { it.setLook(KEY, serialize()) } } }
    }

    /**
     * [buckets] totals of [bucketMinutes] each, oldest first, ending with the current minute.
     * Each entry is [ok, denied, failed, rejected]; [tokenId] null = every agent.
     */
    fun series(tokenId: String?, buckets: Int, bucketMinutes: Int, now: Long = System.currentTimeMillis()): List<IntArray> {
        val last = now / 60_000
        val first = last - buckets.toLong() * bucketMinutes + 1
        val result = List(buckets) { IntArray(4) }
        synchronized(minutes) {
            minutes[tokenId ?: ALL]?.forEach { (minute, counts) ->
                if (minute in first..last) {
                    val target = result[((minute - first) / bucketMinutes).toInt()]
                    for (i in 0 until 4) target[i] += counts[i]
                }
            }
        }
        return result
    }

    fun totals(tokenId: String?, minutesBack: Int, now: Long = System.currentTimeMillis()): IntArray =
        series(tokenId, 1, minutesBack, now).single()

    fun forget(tokenId: String) { synchronized(minutes) { minutes.remove(tokenId) }; mutableRevision.value++ }
    internal fun clearForTests() { synchronized(minutes) { minutes.clear() }; store = null }

    private fun serialize(): String = synchronized(minutes) {
        JSONObject().apply {
            minutes.forEach { (key, series) ->
                put(key, JSONObject().apply { series.forEach { (minute, c) -> put(minute.toString(), JSONArray(c.toList())) } })
            }
        }.toString()
    }

    private fun parse(raw: String): Map<String, HashMap<Long, IntArray>> = runCatching {
        val root = JSONObject(raw)
        root.keys().asSequence().associateWith { key ->
            val series = root.getJSONObject(key)
            HashMap<Long, IntArray>().apply {
                series.keys().forEach { minute ->
                    val c = series.getJSONArray(minute)
                    minute.toLongOrNull()?.let { put(it, IntArray(4) { i -> c.optInt(i) }) }
                }
            }
        }
    }.getOrDefault(emptyMap())
}
