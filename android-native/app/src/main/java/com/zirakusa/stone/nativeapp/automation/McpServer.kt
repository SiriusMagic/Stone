package com.zirakusa.stone.nativeapp.automation

import android.util.Log
import com.zirakusa.stone.nativeapp.BuildConfig
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Model Context Protocol server (Streamable HTTP, JSON responses) exposing [StoneAutomation]
 * actions as tools. Deliberately small: one POST endpoint, JSON-RPC 2.0, no sessions.
 *
 * Security: every request needs `Authorization: Bearer <token>`; requests carrying a browser
 * `Origin` header are refused (DNS-rebinding/CSRF); it listens on loopback unless the user
 * enables local-network access.
 */
object McpServer {
    const val PORT = 8765
    const val PATH = "/mcp"
    private const val TAG = "StoneMcp"
    private const val MAX_BODY = 1 shl 20
    private val protocolVersions = listOf("2025-06-18", "2025-03-26", "2024-11-05")

    @Volatile private var socket: ServerSocket? = null
    @Volatile private var token: String = ""
    private var workers: ExecutorService? = null

    val running: Boolean get() = socket?.isClosed == false

    @Synchronized fun start(token: String, localNetwork: Boolean): Boolean {
        stop()
        this.token = token
        return runCatching {
            val address = if (localNetwork) InetAddress.getByName("0.0.0.0") else InetAddress.getLoopbackAddress()
            val server = ServerSocket(PORT, 16, address)
            socket = server
            val pool = Executors.newFixedThreadPool(4).also { workers = it }
            Thread({
                while (!server.isClosed) {
                    val client = runCatching { server.accept() }.getOrNull() ?: break
                    pool.execute { runCatching { client.use(::serve) }.onFailure { Log.w(TAG, "request failed", it) } }
                }
            }, "stone-mcp").apply { isDaemon = true }.start()
            true
        }.onFailure { Log.w(TAG, "could not start", it) }.getOrDefault(false)
    }

    @Synchronized fun stop() {
        runCatching { socket?.close() }
        socket = null
        workers?.shutdownNow()
        workers = null
    }

    private fun serve(client: Socket) {
        client.soTimeout = 15_000
        val input = BufferedInputStream(client.getInputStream())
        val requestLine = readLine(input) ?: return
        val parts = requestLine.split(' ')
        if (parts.size < 2) return respond(client, 400, error(null, -32700, "Bad request"))
        val headers = HashMap<String, String>()
        while (true) {
            val line = readLine(input) ?: break
            if (line.isEmpty()) break
            val colon = line.indexOf(':')
            if (colon > 0) headers[line.substring(0, colon).trim().lowercase()] = line.substring(colon + 1).trim()
        }
        val path = parts[1].substringBefore('?')
        if (headers.containsKey("origin")) return respond(client, 403, error(null, -32000, "Browser origins are not allowed"))
        if (!authorized(headers["authorization"])) return respond(client, 401, error(null, -32001, "Missing or wrong token"),
            "WWW-Authenticate: Bearer")
        if (path != PATH) return respond(client, 404, error(null, -32601, "Use POST $PATH"))
        if (parts[0] != "POST") return respond(client, 405, error(null, -32601, "Only POST is supported"), "Allow: POST")
        val length = headers["content-length"]?.toIntOrNull() ?: 0
        if (length !in 1..MAX_BODY) return respond(client, 400, error(null, -32700, "Body required (max 1 MB)"))
        val body = ByteArray(length)
        var read = 0
        while (read < length) {
            val count = input.read(body, read, length - read)
            if (count < 0) break
            read += count
        }
        val text = String(body, 0, read, Charsets.UTF_8).trim()
        val reply: Any? = try {
            if (text.startsWith("[")) {
                val batch = JSONArray(text)
                JSONArray((0 until batch.length()).mapNotNull { handle(batch.getJSONObject(it)) }).takeIf { it.length() > 0 }
            } else handle(JSONObject(text))
        } catch (parse: org.json.JSONException) {
            error(null, -32700, "Invalid JSON")
        }
        if (reply == null) respond(client, 202, null) else respond(client, 200, reply)
    }

    /** Returns the JSON-RPC response, or null for notifications. */
    private fun handle(message: JSONObject): JSONObject? {
        val id = message.opt("id")
        val method = message.optString("method")
        if (id == null || id == JSONObject.NULL) return null
        val params = message.optJSONObject("params") ?: JSONObject()
        return try {
            val result: JSONObject = when (method) {
                "initialize" -> JSONObject()
                    .put("protocolVersion", params.optString("protocolVersion").takeIf { it in protocolVersions } ?: protocolVersions.first())
                    .put("capabilities", JSONObject().put("tools", JSONObject().put("listChanged", false)))
                    .put("serverInfo", JSONObject().put("name", "stone").put("title", "Stone").put("version", BuildConfig.VERSION_NAME))
                    .put("instructions", "Controls the Stone music/video player on this phone. Search the library first to get " +
                        "song ids. Tools that write files ask the user for confirmation on the phone.")
                "ping" -> JSONObject()
                "tools/list" -> JSONObject().put("tools", JSONArray(StoneAutomation.actions.map { action ->
                    JSONObject().put("name", action.toolName).put("description", action.description)
                        .put("inputSchema", action.inputSchema())
                        .put("annotations", JSONObject().put("readOnlyHint", action.risk == ActionRisk.READ)
                            .put("destructiveHint", action.risk == ActionRisk.PERSISTENT))
                }))
                "tools/call" -> callTool(params)
                else -> return error(id, -32601, "Method not found: $method")
            }
            JSONObject().put("jsonrpc", "2.0").put("id", id).put("result", result)
        } catch (failure: Exception) {
            error(id, -32603, failure.message ?: "Internal error")
        }
    }

    private fun callTool(params: JSONObject): JSONObject {
        val name = params.optString("name")
        val arguments = params.optJSONObject("arguments") ?: JSONObject()
        return try {
            val result = runBlocking { StoneAutomation.execute(name, arguments) }
            JSONObject().put("content", JSONArray().put(JSONObject().put("type", "text").put("text", result.toString())))
                .put("isError", false).apply { if (result is JSONObject) put("structuredContent", result) }
        } catch (failure: ActionException) {
            // Tool errors go in the result so the model can read them and correct itself.
            JSONObject().put("content", JSONArray().put(JSONObject().put("type", "text").put("text", failure.message)))
                .put("isError", true)
        }
    }

    private fun authorized(header: String?): Boolean {
        val given = header?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }?.substring(7)?.trim() ?: return false
        return token.isNotEmpty() && MessageDigest.isEqual(given.toByteArray(), token.toByteArray())
    }

    private fun error(id: Any?, code: Int, message: String) = JSONObject().put("jsonrpc", "2.0")
        .put("id", id ?: JSONObject.NULL).put("error", JSONObject().put("code", code).put("message", message))

    private fun respond(client: Socket, status: Int, body: Any?, extraHeader: String? = null) {
        val bytes = body?.toString()?.toByteArray(Charsets.UTF_8) ?: ByteArray(0)
        val reason = when (status) { 200 -> "OK"; 202 -> "Accepted"; 400 -> "Bad Request"; 401 -> "Unauthorized"
            403 -> "Forbidden"; 404 -> "Not Found"; 405 -> "Method Not Allowed"; else -> "Error" }
        val head = buildString {
            append("HTTP/1.1 $status $reason\r\n")
            if (bytes.isNotEmpty()) append("Content-Type: application/json\r\n")
            append("Content-Length: ${bytes.size}\r\nConnection: close\r\n")
            extraHeader?.let { append(it).append("\r\n") }
            append("\r\n")
        }
        client.getOutputStream().apply { write(head.toByteArray(Charsets.US_ASCII)); write(bytes); flush() }
    }

    private fun readLine(input: InputStream): String? {
        val line = StringBuilder()
        while (true) {
            val byte = input.read()
            if (byte < 0) return if (line.isEmpty()) null else line.toString()
            if (byte == '\n'.code) return line.toString().trimEnd('\r')
            if (line.length > 8192) return null
            line.append(byte.toChar())
        }
    }
}
