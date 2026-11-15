package com.zirakusa.stone.nativeapp.automation

import android.util.Log
import com.zirakusa.stone.nativeapp.BuildConfig
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Model Context Protocol server exposing [StoneAutomation] actions as tools. Two transports,
 * so it works with as many clients as possible:
 * - Streamable HTTP: `POST /mcp` (JSON responses, optional `Mcp-Session-Id`).
 * - Legacy HTTP+SSE: `GET /sse` opens the stream, `POST /messages?sessionId=…` sends requests.
 * For clients that cannot speak MCP there is a plain bridge: `GET /tools`, `GET /call/<tool>?arg=…`
 * and a guide on a plain `GET /mcp`. `GET /health` is the only route that needs no token.
 *
 * Security: every request needs the token, as `Authorization: Bearer <token>`, `X-Api-Key` or
 * `?token=` (for clients that only take a URL). It listens on loopback unless the user enables
 * the local network. Each response carries `X-Stone-Server` so a different program using the
 * same port is easy to spot.
 */
object McpServer {
    const val DEFAULT_PORT = 8765
    const val PATH = "/mcp"
    private const val TAG = "StoneMcp"
    private val protocolVersions = listOf("2025-11-25", "2025-06-18", "2025-03-26", "2024-11-05")
    val identity: String get() = "Stone ${BuildConfig.VERSION_NAME} (${BuildConfig.APPLICATION_ID})"

    @Volatile private var socket: ServerSocket? = null
    @Volatile private var token: String = ""
    @Volatile var port: Int = DEFAULT_PORT; private set
    private var workers: ExecutorService? = null
    /** Open legacy SSE streams by session id. */
    private val streams = ConcurrentHashMap<String, OutputStream>()

    val running: Boolean get() = socket?.isClosed == false

    @Synchronized fun start(token: String, localNetwork: Boolean, port: Int = DEFAULT_PORT): Boolean {
        stop()
        this.token = token
        this.port = port
        return runCatching {
            val address = if (localNetwork) InetAddress.getByName("0.0.0.0") else InetAddress.getLoopbackAddress()
            val server = ServerSocket(port, 32, address)
            socket = server
            val pool = Executors.newFixedThreadPool(8).also { workers = it }
            Thread({
                while (!server.isClosed) {
                    val client = runCatching { server.accept() }.getOrNull() ?: break
                    pool.execute { runCatching { client.use(::serve) }.onFailure { Log.w(TAG, "request failed", it) } }
                }
            }, "stone-mcp").apply { isDaemon = true }.start()
            true
        }.onFailure { Log.w(TAG, "could not start on port $port", it) }.getOrDefault(false)
    }

    @Synchronized fun stop() {
        runCatching { socket?.close() }
        socket = null
        streams.values.forEach { runCatching { it.close() } }
        streams.clear()
        workers?.shutdownNow()
        workers = null
    }

    private fun serve(client: Socket) {
        client.soTimeout = 15_000
        val request = McpRequest.read(client) ?: return
        val out = client.getOutputStream()
        if (request.method == "OPTIONS") return McpHttp.respond(out, 204, null)
        // Agents often append to the MCP URL, so /mcp/tools works the same as /tools.
        val path = request.path.removePrefix(PATH).takeIf { it.startsWith("/") && request.path.startsWith("$PATH/") } ?: request.path
        // Availability check without the token: says nothing about the library or the user.
        if (path == "/health") return McpHttp.respond(out, 200, JSONObject().put("status", "ok").put("server", "Stone")
            .put("version", BuildConfig.VERSION_NAME).put("mcpPath", PATH).put("tokenRequired", true))
        val session = request.query["sessionId"]?.takeIf { streams.containsKey(it) }
        if (!authorized(request) && session == null) return McpHttp.respond(out, 401,
            error(null, -32001, if (givenToken(request) == null)
                "Stone: missing token. Send it as 'Authorization: Bearer TOKEN' or add ?token=TOKEN to the URL."
            else "Stone: wrong or revoked token. Copy the link again from Stone > Ajustes > Modo API."),
            listOf("WWW-Authenticate: Bearer"))
        when {
            path == "/sse" && request.method == "GET" -> openStream(client, out, request)
            path == "/messages" && request.method == "POST" -> postToStream(out, request, session)
            path == "/tools" && request.method == "GET" -> McpHttp.respond(out, 200, StoneGuide.toolList())
            path.startsWith("/call/") && (request.method == "GET" || request.method == "POST") ->
                simpleCall(out, request, path.removePrefix("/call/"))
            path == PATH || path == "/" -> streamableHttp(out, request)
            else -> McpHttp.respond(out, 404, error(null, -32601,
                "Stone: unknown path. Use POST $PATH (MCP), GET /sse, GET /tools, GET /call/TOOL_NAME or GET /health."))
        }
    }

    /** One tool per plain request, for agents that cannot speak MCP: `GET /call/player_state?token=…`. */
    private fun simpleCall(out: OutputStream, request: McpRequest, tool: String) {
        val action = StoneAutomation.find(tool) ?: return McpHttp.respond(out, 404, JSONObject().put("ok", false)
            .put("error", "Unknown tool '$tool'. GET /tools lists them, or call stone_help."))
        val reply = try {
            val body = request.body.takeIf { it.startsWith("{") }?.let(::JSONObject)
            val arguments = body ?: StoneGuide.arguments(action, request.query)
            JSONObject().put("ok", true).put("tool", action.toolName)
                .put("result", runBlocking { StoneAutomation.execute(action.toolName, arguments) })
        } catch (failure: Exception) {
            JSONObject().put("ok", false).put("tool", action.toolName).put("error", failure.message ?: "Action failed")
        }
        McpHttp.respond(out, if (reply.getBoolean("ok")) 200 else 400, reply)
    }

    /** Streamable HTTP: one POST per JSON-RPC message or batch; GET has no server stream here. */
    private fun streamableHttp(out: OutputStream, request: McpRequest) {
        when (request.method) {
            "POST" -> {
                val (reply, initialized) = dispatch(request.body)
                val headers = if (initialized) listOf("Mcp-Session-Id: ${UUID.randomUUID()}") else emptyList()
                if (reply == null) McpHttp.respond(out, 202, null, headers) else McpHttp.respond(out, 200, reply, headers)
            }
            "DELETE" -> McpHttp.respond(out, 200, null)
            // MCP clients probe GET for a server stream (which Stone does not offer) and expect 405.
            // Anything else doing a GET (a browser, curl, an agent reading the link) gets a guide.
            else -> if (request.method == "GET" && request.header("accept")?.contains("text/event-stream") != true)
                McpHttp.respond(out, 200, StoneGuide.overview())
            else McpHttp.respond(out, 405, error(null, -32000,
                "Stone: this endpoint has no server stream. Send MCP requests with POST (or use GET /sse)."), listOf("Allow: POST, DELETE"))
        }
    }

    /** Legacy SSE transport: announce where to post, then keep the stream alive until it closes. */
    private fun openStream(client: Socket, out: OutputStream, request: McpRequest) {
        val id = UUID.randomUUID().toString()
        client.soTimeout = 0
        McpHttp.startEventStream(out)
        val tokenSuffix = request.query["token"]?.let { "&token=$it" }.orEmpty()
        McpHttp.event(out, "endpoint", "/messages?sessionId=$id$tokenSuffix")
        streams[id] = out
        try {
            while (running && streams.containsKey(id)) {
                Thread.sleep(15_000)
                McpHttp.event(out, null, "") // keep-alive; throws once the client is gone
            }
        } catch (_: Exception) {
        } finally { streams.remove(id) }
    }

    private fun postToStream(out: OutputStream, request: McpRequest, session: String?) {
        val stream = session?.let { streams[it] }
            ?: return McpHttp.respond(out, 404, error(null, -32000, "Stone: unknown or closed SSE session"))
        McpHttp.respond(out, 202, null)
        val (reply, _) = dispatch(request.body)
        if (reply != null) runCatching { McpHttp.event(stream, "message", reply.toString()) }
    }

    /** Parses one message or a batch; returns the reply (null for notifications) and whether it initialized. */
    private fun dispatch(text: String): Pair<Any?, Boolean> = try {
        if (text.startsWith("[")) {
            val batch = JSONArray(text)
            val messages = (0 until batch.length()).map { batch.getJSONObject(it) }
            JSONArray(messages.mapNotNull(::handle)).takeIf { it.length() > 0 } to messages.any { it.optString("method") == "initialize" }
        } else {
            val message = JSONObject(text)
            handle(message) to (message.optString("method") == "initialize")
        }
    } catch (parse: org.json.JSONException) {
        error(null, -32700, "Invalid JSON") to false
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
                    .put("instructions", "Controls the Stone music/video player on this phone. Call stone_help to find a tool by " +
                        "intention. Search the library first (library_search) to get song ids; player_state shows what is playing. " +
                        "Tools that write files ask the user for confirmation on the phone (up to 60 s). " +
                        if (ApiMode.readOnly) "This link is READ-ONLY: only query tools are listed."
                        else if (!ApiMode.writeAccess) "Editing files is disabled for this link." else "")
                "ping" -> JSONObject()
                "tools/list" -> JSONObject().put("tools", JSONArray(StoneAutomation.available.map { action ->
                    JSONObject().put("name", action.toolName).put("description", action.description)
                        .put("inputSchema", action.inputSchema())
                        .put("annotations", JSONObject().put("readOnlyHint", action.risk == ActionRisk.READ)
                            .put("destructiveHint", action.risk == ActionRisk.PERSISTENT))
                }))
                "tools/call" -> callTool(params)
                // Optional lists some clients request at start-up; Stone has none of these.
                "resources/list" -> JSONObject().put("resources", JSONArray())
                "resources/templates/list" -> JSONObject().put("resourceTemplates", JSONArray())
                "prompts/list" -> JSONObject().put("prompts", JSONArray())
                else -> return error(id, -32601, "Method not found: $method. Supported: initialize, ping, tools/list, tools/call.")
            }
            JSONObject().put("jsonrpc", "2.0").put("id", id).put("result", result)
        } catch (failure: IllegalArgumentException) {
            error(id, -32602, failure.message ?: "Invalid params")
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
            if (StoneAutomation.find(name) == null) throw IllegalArgumentException(
                "Unknown tool '$name'. Call tools/list or the stone_help tool to see the valid names.")
            // Tool errors go in the result so the model can read them and correct itself.
            JSONObject().put("content", JSONArray().put(JSONObject().put("type", "text").put("text", failure.message)))
                .put("isError", true)
        }
    }

    private fun givenToken(request: McpRequest): String? {
        val bearer = request.header("authorization")?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }?.substring(7)?.trim()
        return (bearer ?: request.header("x-api-key")?.trim() ?: request.query["token"]?.trim())?.takeIf { it.isNotEmpty() }
    }

    private fun authorized(request: McpRequest): Boolean {
        val given = givenToken(request) ?: return false
        return token.isNotEmpty() && MessageDigest.isEqual(given.toByteArray(), token.toByteArray())
    }

    private fun error(id: Any?, code: Int, message: String) = JSONObject().put("jsonrpc", "2.0")
        .put("id", id ?: JSONObject.NULL).put("error", JSONObject().put("code", code).put("message", message))
}
