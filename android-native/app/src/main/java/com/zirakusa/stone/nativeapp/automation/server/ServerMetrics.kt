package com.zirakusa.stone.nativeapp.automation.server

import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import com.zirakusa.stone.nativeapp.automation.actions.StoneAutomation

/**
 * What the MCP server and the relay link actually do, since Stone started, to measure before
 * optimising: requests per route, HTTP status, JSON-RPC method and tool, latency and time spent
 * waiting in the queue, plus relay events (reconnections, timeouts, late or refused work).
 *
 * Never stores secrets: no query strings (they may carry ?token=), no headers, no arguments;
 * routes are normalised and tools are counted by name only.
 */
object ServerMetrics {
    /** Latency buckets (ms), shared by total time and queue wait. */
    private val bounds = longArrayOf(100, 500, 2_000, 10_000, 30_000, 75_000)
    private val labels = listOf("<100ms", "<500ms", "<2s", "<10s", "<30s", "<75s", ">=75s")

    @Volatile var since = System.currentTimeMillis(); private set
    private val routes = ConcurrentHashMap<String, AtomicLong>()
    private val statuses = ConcurrentHashMap<String, AtomicLong>()
    private val methods = ConcurrentHashMap<String, AtomicLong>()
    private val tools = ConcurrentHashMap<String, AtomicLong>()
    private val events = ConcurrentHashMap<String, AtomicLong>()
    private val latency = ConcurrentHashMap<String, AtomicLong>()
    private val queueWait = ConcurrentHashMap<String, AtomicLong>()
    private val totalMs = AtomicLong(); private val maxMs = AtomicLong(); private val timed = AtomicLong()
    @Volatile var lastRelayError = ""; private set

    private fun ConcurrentHashMap<String, AtomicLong>.inc(key: String) = getOrPut(key) { AtomicLong() }.incrementAndGet()
    private fun bucket(ms: Long) = labels[bounds.indexOfFirst { ms < it }.let { if (it < 0) labels.lastIndex else it }]

    /** One HTTP request handled by the local server ([viaRelay] = forwarded from the internet link). */
    fun request(method: String, path: String, status: Int, elapsedMs: Long, viaRelay: Boolean, queuedMs: Long?) {
        routes.inc("$method ${route(path)}")
        statuses.inc(status.toString())
        latency.inc(bucket(elapsedMs))
        totalMs.addAndGet(elapsedMs); timed.incrementAndGet(); maxMs.accumulateAndGet(elapsedMs, ::maxOf)
        events.inc(if (viaRelay) "requestsViaRelay" else "requestsLocal")
        queuedMs?.let { queueWait.inc(bucket(it)) }
    }

    /** One JSON-RPC message ([tool] for tools/call). */
    fun rpc(method: String, tool: String? = null) {
        methods.inc(method.takeIf { it.length <= 40 && it.all { c -> c.isLetterOrDigit() || c in "/_." } } ?: "other")
        tool?.let { name -> tools.inc(StoneAutomation.find(name)?.toolName ?: "unknown") }
    }

    /** Named event: relayConnects, relayReconnectAttempts, relayPermanentStops, relayTimeouts, lateCompletions… */
    fun event(name: String) { events.inc(name) }

    fun relayError(description: String) { lastRelayError = description.take(120); event("relayErrors") }

    /** Paths keep only their shape: /call/<tool> and unknown paths are grouped. */
    private fun route(path: String): String = when {
        path.startsWith("/call/") -> "/call/*"
        path in setOf("/mcp", "/", "/sse", "/messages", "/tools", "/health") -> path
        else -> "other"
    }

    fun count(name: String): Long = events[name]?.get() ?: 0

    fun snapshot(): JSONObject {
        fun map(source: Map<String, AtomicLong>, top: Int = 50) = JSONObject().apply {
            source.entries.sortedByDescending { it.value.get() }.take(top).forEach { put(it.key, it.value.get()) }
        }
        fun buckets(source: Map<String, AtomicLong>) = JSONObject().apply { labels.forEach { put(it, source[it]?.get() ?: 0) } }
        val count = timed.get()
        return JSONObject().put("sinceMs", since).put("uptimeSeconds", (System.currentTimeMillis() - since) / 1000)
            .put("routes", map(routes)).put("status", map(statuses)).put("rpcMethods", map(methods)).put("tools", map(tools, 30))
            .put("latency", buckets(latency)).put("queueWait", buckets(queueWait))
            .put("averageMs", if (count == 0L) 0 else totalMs.get() / count).put("maxMs", maxMs.get())
            .put("events", map(events)).put("lastRelayError", lastRelayError)
            .put("notes", JSONArray(listOf(
                "Counts start when Stone starts. Requests answered by Cloudflare alone (OPTIONS, 503 while the phone is offline, 504 timeouts) are not seen here; relayTimeouts counts the 504s the relay reported back.",
                "No tokens, query strings or arguments are recorded.")))
    }

    fun reset() {
        listOf(routes, statuses, methods, tools, events, latency, queueWait).forEach { it.clear() }
        totalMs.set(0); maxMs.set(0); timed.set(0); lastRelayError = ""; since = System.currentTimeMillis()
    }
}
