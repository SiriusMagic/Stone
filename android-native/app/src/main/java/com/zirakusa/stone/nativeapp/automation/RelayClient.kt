package com.zirakusa.stone.nativeapp.automation

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
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

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
    private val onState: (State) -> Unit,
) {
    enum class State { CONNECTING, ONLINE, OFFLINE }

    private val http = OkHttpClient.Builder().pingInterval(25, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS).build()
    private val workers = Executors.newFixedThreadPool(4)
    private val retry = Executors.newSingleThreadScheduledExecutor()
    @Volatile private var socket: WebSocket? = null
    @Volatile private var running = false
    private var backoffSeconds = 1L

    val publicBase: String get() = "$relayUrl/d/$device"

    fun start() {
        if (running) return
        running = true
        connect()
    }

    fun stop() {
        running = false
        socket?.close(1000, "off")
        socket = null
        onState(State.OFFLINE)
    }

    private fun connect() {
        if (!running) return
        onState(State.CONNECTING)
        val wsUrl = relayUrl.replaceFirst("https://", "wss://").replaceFirst("http://", "ws://") + "/connect?device=$device"
        val request = Request.Builder().url(wsUrl).header("X-Stone-Secret", secret).build()
        socket = http.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                backoffSeconds = 1
                onState(State.ONLINE)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val message = runCatching { JSONObject(text) }.getOrNull() ?: return
                if (message.optString("t") == "req") workers.execute { webSocket.send(answer(message).toString()) }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = retryLater(webSocket)
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w("StoneRelay", "relay connection failed: ${t.message}")
                retryLater(webSocket)
            }
        })
    }

    private fun retryLater(closed: WebSocket) {
        if (socket !== closed) return
        socket = null
        if (!running) return
        onState(State.CONNECTING)
        retry.schedule(::connect, backoffSeconds, TimeUnit.SECONDS)
        backoffSeconds = (backoffSeconds * 2).coerceAtMost(30)
    }

    /**
     * Replays one forwarded request against the local MCP server and packs its answer.
     * Uses a plain socket: Android's cleartext policy blocks HttpURLConnection to 127.0.0.1.
     */
    private fun answer(message: JSONObject): JSONObject {
        val reply = JSONObject().put("t", "res").put("id", message.optString("id"))
        return try {
            val method = message.optString("method", "POST").let { if (it == "OPTIONS") "POST" else it }
            val body = message.optString("body").toByteArray(Charsets.UTF_8)
            val head = StringBuilder("$method ${message.optString("path", "/")}${message.optString("query")} HTTP/1.1\r\n")
                .append("Host: 127.0.0.1\r\nConnection: close\r\nContent-Length: ${body.size}\r\n")
            message.optJSONObject("headers")?.let { headers ->
                headers.keys().forEach { head.append(it).append(": ").append(headers.getString(it).replace(Regex("[\r\n]"), " ")).append("\r\n") }
            }
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

    private companion object {
        val PASSED_BACK = listOf("Content-Type", "Mcp-Session-Id", "X-Stone-Server", "WWW-Authenticate")
    }
}
