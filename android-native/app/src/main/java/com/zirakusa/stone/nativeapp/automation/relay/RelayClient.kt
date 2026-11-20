package com.zirakusa.stone.nativeapp.automation.relay

import android.net.ConnectivityManager
import android.net.Network
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import com.zirakusa.stone.nativeapp.automation.server.ServerMetrics

/**
 * Keeps Stone reachable at a fixed public link through the Stone relay (see ../relay).
 * The phone dials out to the relay over a WebSocket, so it works on any Wi-Fi or mobile data
 * without a computer or router setup. Each request the relay forwards is replayed against
 * Stone's own MCP server on 127.0.0.1, which still checks the token.
 */
class RelayClient(
    private val relayUrl: String,
    private val device: String,
    private val secret: String,
    private val localPort: () -> Int,
    private val connectivity: ConnectivityManager? = null,
    private val onState: (State) -> Unit,
) {
    enum class State { CONNECTING, ONLINE, OFFLINE }

    private val http = OkHttpClient.Builder().pingInterval(25, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS).build()
    // Several agents (one per token) can call at once; a slow call must not hold up the others.
    // The waiting line is bounded: when it is full the request is refused at once ("busy"), instead of
    // queueing work the relay would give up on after 75 s.
    private val workers = ThreadPoolExecutor(WORKERS, WORKERS, 0, TimeUnit.MILLISECONDS, ArrayBlockingQueue(MAX_QUEUED))
    private val retry = Executors.newSingleThreadScheduledExecutor()
    @Volatile private var socket: WebSocket? = null
    @Volatile private var running = false
    /** Set when the relay gave an answer that will not change: no more automatic attempts. */
    @Volatile private var stopped = false
    private var attempt = 0
    private var pending: ScheduledFuture<*>? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    fun start() {
        if (running) return
        running = true; stopped = false; attempt = 0
        watchNetwork()
        connect()
    }

    fun stop() {
        running = false
        pending?.cancel(false)
        unwatchNetwork()
        socket?.close(1000, "off")
        socket = null
        onState(State.OFFLINE)
    }

    private fun connect() {
        if (!running || stopped) return
        onState(State.CONNECTING)
        ServerMetrics.event("relayConnectAttempts")
        // Encrypted only: the device secret travels in this request. Plain ws:// is allowed only to a
        // relay on this same machine ("wrangler dev" in tests), never across a network.
        if (!isAllowedRelay(relayUrl)) { stopped = true; onState(State.OFFLINE); return }
        val wsUrl = relayUrl.replaceFirst("https://", "wss://").replaceFirst("http://", "ws://") + "/connect?device=$device"
        val request = Request.Builder().url(wsUrl).header("X-Stone-Secret", secret).build()
        socket = http.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                attempt = 0
                ServerMetrics.event("relayConnects")
                onState(State.ONLINE)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val message = runCatching { JSONObject(text) }.getOrNull() ?: return
                when (message.optString("t")) {
                    "req" -> {
                        val receivedAt = System.currentTimeMillis()
                        try {
                            workers.execute { webSocket.send(answer(message, receivedAt).toString()) }
                        } catch (_: RejectedExecutionException) {
                            ServerMetrics.event("relayQueueFull")
                            webSocket.send(busy(message.optString("id")).toString())
                        }
                    }
                    // The relay answered 504 for this request: whatever is still waiting for it must not run.
                    "timeout" -> { ServerMetrics.event("relayTimeouts"); RelayGuard.relayTimedOut(message.optString("id")) }
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = retryLater(webSocket, null, reason)
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w("StoneRelay", "relay connection failed: ${t.message}")
                retryLater(webSocket, response?.code, null, t.message)
            }
        })
    }

    /**
     * Dials again after a growing wait, or stops for good when the relay's answer will not change
     * (see [ReconnectPolicy]): each attempt is a billable request to Cloudflare.
     */
    private fun retryLater(closed: WebSocket, httpStatus: Int?, closeReason: String?, error: String? = null) {
        if (socket !== closed) return
        socket = null
        if (!running) return
        if (ReconnectPolicy.isPermanent(httpStatus, closeReason)) {
            stopped = true
            ServerMetrics.event("relayPermanentStops")
            ServerMetrics.relayError(httpStatus?.let { "HTTP $it" } ?: closeReason.orEmpty())
            onState(State.OFFLINE)
            return
        }
        ServerMetrics.relayError(httpStatus?.let { "HTTP $it" } ?: error ?: closeReason ?: "closed")
        onState(State.CONNECTING)
        attempt++
        pending = retry.schedule(::connect, ReconnectPolicy.delaySeconds(attempt), TimeUnit.SECONDS)
    }

    /** When the phone gets a network back, try at once instead of waiting out the backoff. */
    private fun watchNetwork() {
        val manager = connectivity ?: return
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                if (!running || stopped || socket != null) return
                pending?.cancel(false)
                attempt = 0
                pending = retry.schedule(::connect, 1, TimeUnit.SECONDS)
            }
        }
        runCatching { manager.registerDefaultNetworkCallback(callback); networkCallback = callback }
    }

    private fun unwatchNetwork() {
        val callback = networkCallback ?: return
        runCatching { connectivity?.unregisterNetworkCallback(callback) }
        networkCallback = null
    }

    private fun busy(id: String) = JSONObject().put("t", "res").put("id", id).put("status", 503)
        .put("headers", JSONObject().put("Content-Type", "application/json").put("Retry-After", "10"))
        .put("body", JSONObject().put("jsonrpc", "2.0").put("id", JSONObject.NULL).put("error", JSONObject().put("code", -32000)
            .put("message", "Stone is busy with other agents' requests. Nothing was run; send it again in a few seconds.")).toString())

    /**
     * Replays one forwarded request against the local MCP server and packs its answer.
     * Uses a plain socket: Android's cleartext policy blocks HttpURLConnection to 127.0.0.1.
     */
    private fun answer(message: JSONObject, receivedAt: Long): JSONObject {
        val reply = JSONObject().put("t", "res").put("id", message.optString("id"))
        // Still queued when the relay already answered 504: drop it without running it.
        if (RelayGuard.hasTimedOut(message.optString("id"))) {
            ServerMetrics.event("staleRequestsNotRun")
            return busy(message.optString("id"))
        }
        return try {
            val method = message.optString("method", "POST").let { if (it == "OPTIONS") "POST" else it }
            val body = message.optString("body").toByteArray(Charsets.UTF_8)
            val head = StringBuilder("$method ${message.optString("path", "/")}${message.optString("query")} HTTP/1.1\r\n")
                .append("Host: 127.0.0.1\r\nConnection: close\r\nContent-Length: ${body.size}\r\n")
            message.optJSONObject("headers")?.let { headers ->
                // X-Stone-* is reserved for what this client adds below; anything arriving with that name is dropped.
                headers.keys().asSequence().filterNot { it.startsWith("x-stone-", ignoreCase = true) }.forEach {
                    head.append(it).append(": ").append(headers.getString(it).replace(Regex("[\r\n]"), " ")).append("\r\n")
                }
            }
            // Trusted by the server only with the per-run key: real client address (from Cloudflare), arrival time, relay id.
            head.append("X-Stone-Relay-Key: ").append(RelayGuard.key).append("\r\n")
                .append("X-Stone-Relay-Received: ").append(receivedAt).append("\r\n")
                .append("X-Stone-Relay-Id: ").append(message.optString("id").filter { it.isLetterOrDigit() }.take(20)).append("\r\n")
            RelayGuard.cleanAddress(message.optString("client"))?.let { head.append("X-Stone-Relay-Client: ").append(it).append("\r\n") }
            val raw = Socket().use { local ->
                local.connect(InetSocketAddress(InetAddress.getLoopbackAddress(), localPort()), 3_000)
                local.soTimeout = 70_000
                local.getOutputStream().apply { write(head.append("\r\n").toString().toByteArray(Charsets.UTF_8)); write(body); flush() }
                local.getInputStream().readBytes().toString(Charsets.UTF_8)
            }
            val lines = raw.substringBefore("\r\n\r\n").split("\r\n")
            val status = lines.first().split(" ").getOrNull(1)?.toIntOrNull() ?: 502
            val headers = JSONObject()
            lines.drop(1).forEach { line ->
                val name = line.substringBefore(":").trim()
                PASSED_BACK.firstOrNull { it.equals(name, ignoreCase = true) }?.let { headers.put(it, line.substringAfter(":").trim()) }
            }
            reply.put("status", status).put("headers", headers).put("body", raw.substringAfter("\r\n\r\n", ""))
        } catch (failure: Exception) {
            reply.put("status", 502).put("headers", JSONObject().put("Content-Type", "application/json"))
                .put("body", JSONObject().put("jsonrpc", "2.0").put("id", JSONObject.NULL).put("error",
                    JSONObject().put("code", -32000).put("message", "Stone: local server not reachable (${failure.message})")).toString())
        }
    }

    internal companion object {
        fun isAllowedRelay(url: String): Boolean = url.startsWith("https://") ||
            Regex("^http://(127\\.0\\.0\\.1|localhost)(:\\d+)?(/.*)?$").matches(url)

        val PASSED_BACK = listOf("Content-Type", "Mcp-Session-Id", "X-Stone-Server", "WWW-Authenticate", "Retry-After")
        const val WORKERS = 8
        const val MAX_QUEUED = 64
    }
}
