package com.zirakusa.stone.nativeapp.automation.server

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
import com.zirakusa.stone.nativeapp.automation.actions.ActionException
import com.zirakusa.stone.nativeapp.automation.actions.ActionRisk
import com.zirakusa.stone.nativeapp.automation.actions.StoneAutomation
import com.zirakusa.stone.nativeapp.automation.actions.StoneGuide
import com.zirakusa.stone.nativeapp.automation.activity.RequestStats
import com.zirakusa.stone.nativeapp.automation.auth.AccessMode
import com.zirakusa.stone.nativeapp.automation.auth.ApiToken
import com.zirakusa.stone.nativeapp.automation.auth.ApiTokens
import com.zirakusa.stone.nativeapp.automation.auth.AuthThrottle
import com.zirakusa.stone.nativeapp.automation.relay.RelayGuard
import com.zirakusa.stone.nativeapp.automation.relay.RelayInfo

/**
 * Model Context Protocol server exposing [StoneAutomation] actions as tools. Two transports,
 * so it works with as many clients as possible:
 * - Streamable HTTP: `POST /mcp` (JSON responses, optional `Mcp-Session-Id`).
 * - Legacy HTTP+SSE: `GET /sse` opens the stream, `POST /messages?sessionId=…` sends requests.
 * For clients that cannot speak MCP there is a plain bridge: `GET /tools`, `GET /call/<tool>?arg=…`
 * and a guide on a plain `GET /mcp`. `GET /health` is the only route that needs no token.
 *
 * Security: every request needs the token, as `Authorization: Bearer <token>`, `X-Api-Key` or
 * `?token=` (for clients that only take a URL). It listens only on loopback (127.0.0.1): it is
 * never reachable from the Wi-Fi, only from this phone and through the encrypted relay. Each response carries `X-Stone-Server` so a different program using the
 * same port is easy to spot.
 */
object McpServer {
    const val DEFAULT_PORT = 8765
    const val PATH = "/mcp"
    private const val TAG = "StoneMcp"
    private val protocolVersions = listOf("2025-11-25", "2025-06-18", "2025-03-26", "2024-11-05")
    val identity: String get() = "Stone ${BuildConfig.VERSION_NAME} (${BuildConfig.APPLICATION_ID})"

    @Volatile private var socket: ServerSocket? = null
    /** Valid tokens by secret. */
    @Volatile private var grants: Map<String, ApiToken> = emptyMap()
    @Volatile var port: Int = DEFAULT_PORT; private set
    private var workers: ExecutorService? = null
    /** Open legacy SSE streams by session id. */
    private val streams = ConcurrentHashMap<String, OutputStream>()
    private val streamGrants = ConcurrentHashMap<String, ApiToken>()

    /** Key of the token details in the initialize result (`_meta`), read by Gateway Hub. */
    const val TOKEN_META = "com.zirakusa.stone/token"

    val running: Boolean get() = socket?.isClosed == false

    /** Single-token start (tests, older callers): that token gets "Lectura y escritura". */
    fun start(token: String, port: Int = DEFAULT_PORT): Boolean =
        start(listOf(ApiToken("main", "Principal", token, AccessMode.WRITE, 0)), port)

    fun updateTokens(tokens: List<ApiToken>) {
        grants = tokens.associateBy { it.secret }
        // Revoked, rotated or expired tokens also lose their open SSE sessions at once.
        val now = System.currentTimeMillis()
        streamGrants.forEach { (id, grant) ->
            if (tokens.none { it.id == grant.id && it.secret == grant.secret && !it.paused && (it.expiresAt == 0L || it.expiresAt > now) }) {
                streamGrants.remove(id); streams.remove(id)
            }
        }
    }

    @Synchronized fun start(tokens: List<ApiToken>, port: Int = DEFAULT_PORT): Boolean {
        stop()
        updateTokens(tokens)
        this.port = port
        return runCatching {
            val server = ServerSocket(port, 32, InetAddress.getLoopbackAddress())
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
        streamGrants.clear()
        workers?.shutdownNow()
        workers = null
    }

    /** Relay details of the request this thread is serving (null for direct local calls). */
    private val currentRelay = ThreadLocal<RelayInfo?>()

    private fun serve(client: Socket) {
        client.soTimeout = 15_000
        val request = McpRequest.read(client) ?: return
        val started = System.currentTimeMillis()
        // Relay details count only with the per-run relay key: other apps on the phone cannot fake them.
        val relay = if (RelayGuard.isRelay(request.header("x-stone-relay-key")))
            RelayInfo(request.header("x-stone-relay-received")?.toLongOrNull() ?: started, request.header("x-stone-relay-id"),
                RelayGuard.cleanAddress(request.header("x-stone-relay-client")))
            else null
        currentRelay.set(relay)
        McpHttp.lastStatus.remove()
        try { route(client, request, relay) } finally {
            currentRelay.remove()
            ServerMetrics.request(request.method, request.path, McpHttp.lastStatus.get() ?: 0,
                System.currentTimeMillis() - (relay?.receivedAt ?: started), relay != null, relay?.let { started - it.receivedAt })
        }
    }

    private fun route(client: Socket, request: McpRequest, relay: RelayInfo?) {
        val out = client.getOutputStream()
        if (request.method == "OPTIONS") return McpHttp.respond(out, 204, null)
        // Agents often append to the MCP URL, so /mcp/tools works the same as /tools.
        val path = request.path.removePrefix(PATH).takeIf { it.startsWith("/") && request.path.startsWith("$PATH/") } ?: request.path
        // Availability check without the token: says nothing about the library or the user.
        if (path == "/health") return McpHttp.respond(out, 200, JSONObject().put("status", "ok").put("server", "Stone")
            .put("version", BuildConfig.VERSION_NAME).put("mcpPath", PATH).put("tokenRequired", true))
        val session = request.query["sessionId"]?.takeIf { streams.containsKey(it) }
        // Waited too long in the queue: the relay has given up (504), so running it now would only
        // do the work behind the agent's back. Nothing is executed.
        if (relay != null && System.currentTimeMillis() - relay.receivedAt > RelayGuard.queueDeadlineMs) {
            ServerMetrics.event("staleRequestsNotRun")
            return McpHttp.respond(out, 503, error(null, -32000,
                "Stone was busy and did not run this request. Nothing was changed; send it again."), listOf("Retry-After: 10"))
        }
        // Every request needs the token, SSE messages included: a leaked sessionId alone is not enough.
        val grant = grantFor(request)
        if (grant == null) {
            // Wrong tokens are counted per origin: the internet client's address as reported by the relay
            // (Cloudflare's own value), or the local caller. Spoofable headers like X-Forwarded-For are ignored,
            // and a valid token is never blocked by someone else's mistakes.
            val origin = if (relay != null) "relay:" + (relay.client ?: "unknown") else "local:" + client.inetAddress?.hostAddress.orEmpty()
            RequestStats.record("", RequestStats.REJECTED)
            if (AuthThrottle.blocked(origin)) {
                ServerMetrics.event("authThrottled")
                return McpHttp.respond(out, 429, error(null, -32001,
                    "Stone: too many wrong tokens. Wait a minute and use the current link."), listOf("Retry-After: 60"))
            }
            AuthThrottle.failed(origin); ServerMetrics.event("authRejected")
        }
        if (grant == null) return McpHttp.respond(out, 401,
            error(null, -32001, if (givenToken(request) == null)
                "Stone: missing token. Send it as 'Authorization: Bearer TOKEN' or add ?token=TOKEN to the URL."
            else if (isPaused(request)) "Stone: this token is paused. The user can resume it in Stone > Ajustes > Modo MCP."
            else "Stone: wrong or revoked token. Copy the link again from Stone > Ajustes > Modo MCP."),
            listOf("WWW-Authenticate: Bearer"))
        ApiTokens.touch(grant.id)
        when {
            path == "/sse" && request.method == "GET" -> openStream(client, out, request, grant)
            path == "/messages" && request.method == "POST" -> postToStream(out, request, session, grant)
            path == "/tools" && request.method == "GET" -> McpHttp.respond(out, 200, StoneGuide.toolList(grant.mode, grant.disabledTools))
            path.startsWith("/call/") && (request.method == "GET" || request.method == "POST") ->
                simpleCall(out, request, path.removePrefix("/call/"), grant)
            path == PATH || path == "/" -> streamableHttp(out, request, grant)
            else -> McpHttp.respond(out, 404, error(null, -32601,
                "Stone: unknown path. Use POST $PATH (MCP), GET /sse, GET /tools, GET /call/TOOL_NAME or GET /health."))
        }
    }

    /** One tool per plain request, for agents that cannot speak MCP: `GET /call/player_state?token=…`. */
    private fun simpleCall(out: OutputStream, request: McpRequest, tool: String, grant: ApiToken) {
        val action = StoneAutomation.find(tool) ?: return McpHttp.respond(out, 404, JSONObject().put("ok", false)
            .put("error", "Unknown tool '$tool'. GET /tools lists them, or call stone_help."))
        val reply = try {
            val body = request.body.takeIf { it.startsWith("{") }?.let(::JSONObject)
            val arguments = body ?: StoneGuide.arguments(action, request.query)
            JSONObject().put("ok", true).put("tool", action.toolName)
                .put("result", runBlocking { StoneAutomation.execute(action.toolName, arguments, grant.mode, grant.name, grant.id, grant.disabledTools, currentRelay.get()) })
        } catch (failure: ActionException) {
            JSONObject().put("ok", false).put("tool", action.toolName).put("error", failure.message)
                .put("code", failure.code).apply { failure.hint?.let { put("hint", it) } }
        } catch (failure: Exception) {
            JSONObject().put("ok", false).put("tool", action.toolName).put("error", failure.message ?: "Action failed")
                .put("code", "INVALID_REQUEST")
        }
        McpHttp.respond(out, if (reply.getBoolean("ok")) 200 else 400, reply)
    }

    /** Streamable HTTP: one POST per JSON-RPC message or batch; GET has no server stream here. */
    private fun streamableHttp(out: OutputStream, request: McpRequest, grant: ApiToken) {
        when (request.method) {
            "POST" -> {
                val (reply, initialized) = dispatch(request.body, grant)
                val headers = if (initialized) listOf("Mcp-Session-Id: ${UUID.randomUUID()}") else emptyList()
                if (reply == null) McpHttp.respond(out, 202, null, headers) else McpHttp.respond(out, 200, reply, headers)
            }
            "DELETE" -> McpHttp.respond(out, 200, null)
            // MCP clients probe GET for a server stream (which Stone does not offer) and expect 405.
            // Anything else doing a GET (a browser, curl, an agent reading the link) gets a guide.
            else -> if (request.method == "GET" && request.header("accept")?.contains("text/event-stream") != true)
                McpHttp.respond(out, 200, StoneGuide.overview(grant.mode, grant.disabledTools))
            else McpHttp.respond(out, 405, error(null, -32000,
                "Stone: this endpoint has no server stream. Send MCP requests with POST (or use GET /sse)."), listOf("Allow: POST, DELETE"))
        }
    }

    /** Legacy SSE transport: announce where to post, then keep the stream alive until it closes. */
    private fun openStream(client: Socket, out: OutputStream, request: McpRequest, grant: ApiToken) {
        val id = UUID.randomUUID().toString()
        client.soTimeout = 0
        McpHttp.startEventStream(out)
        val tokenSuffix = request.query["token"]?.let { "&token=$it" }.orEmpty()
        McpHttp.event(out, "endpoint", "/messages?sessionId=$id$tokenSuffix")
        streams[id] = out
        streamGrants[id] = grant
        try {
            while (running && streams.containsKey(id)) {
                Thread.sleep(15_000)
                McpHttp.event(out, null, "") // keep-alive; throws once the client is gone
            }
        } catch (_: Exception) {
        } finally { streams.remove(id); streamGrants.remove(id) }
    }

    private fun postToStream(out: OutputStream, request: McpRequest, session: String?, grant: ApiToken) {
        val stream = session?.let { streams[it] }
            ?: return McpHttp.respond(out, 404, error(null, -32000, "Stone: unknown or closed SSE session"))
        // The session belongs to the token that opened it; another valid token cannot post into it.
        if (streamGrants[session]?.id != grant.id) return McpHttp.respond(out, 403,
            error(null, -32001, "Stone: this SSE session was opened with a different token."))
        McpHttp.respond(out, 202, null)
        val (reply, _) = dispatch(request.body, grant)
        if (reply != null) runCatching { McpHttp.event(stream, "message", reply.toString()) }
    }

    /** Parses one message or a batch; returns the reply (null for notifications) and whether it initialized. */
    /**
     * Lucía (Gateway Hub's voice on this phone) runs one tool without internet, as the hub's own token: the same checks
     * as through the relay. [HubLink] only calls this while «Redireccionar a Hub» is on.
     */
    fun local(body: String): String? = dispatch(body, com.zirakusa.stone.nativeapp.automation.auth.ApiTokens.redirectToken()).first?.toString()

    /** In-process dispatch for Stone's signature-protected provider. The grant is never registered for HTTP. */
    internal fun localRead(body: String, allowed: Set<String>): String? {
        val blocked = StoneAutomation.actions.map { it.toolName }.toSet() - allowed
        val grant = ApiToken("lucia-local", "Lucía local", "", AccessMode.READ, 0, disabledTools = blocked)
        return dispatch(body, grant).first?.toString()
    }

    private fun dispatch(text: String, grant: ApiToken): Pair<Any?, Boolean> = try {
        if (text.startsWith("[")) {
            val batch = JSONArray(text)
            val messages = (0 until batch.length()).map { batch.getJSONObject(it) }
            JSONArray(messages.mapNotNull { handle(it, grant) }).takeIf { it.length() > 0 } to messages.any { it.optString("method") == "initialize" }
        } else {
            val message = JSONObject(text)
            handle(message, grant) to (message.optString("method") == "initialize")
        }
    } catch (parse: org.json.JSONException) {
        error(null, -32700, "Invalid JSON") to false
    }

    /** Returns the JSON-RPC response, or null for notifications. */
    private fun handle(message: JSONObject, grant: ApiToken): JSONObject? {
        val id = message.opt("id")
        val method = message.optString("method")
        ServerMetrics.rpc(method.ifBlank { "none" }, if (method == "tools/call") message.optJSONObject("params")?.optString("name") else null)
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
                        "Edit tools accept dryRun=true to preview changes; every edit is backed up and metadata_undo reverts it. " +
                        "Errors carry a code (structuredContent.error.code). " + when (grant.mode) {
                            AccessMode.READ -> "This token is READ-ONLY: only query tools are listed."
                            AccessMode.WRITE -> "Only critical actions (like deleting a playlist) ask the user on the phone (up to 60 s)."
                            AccessMode.FULL -> "Full access: nothing asks the user for approval."
                        })
                    // For Gateway Hub: which token this is and what it is for, so the hub can show it.
                    .put("_meta", JSONObject().put(TOKEN_META, JSONObject().put("name", grant.name)
                        .put("kind", grant.kind.key).put("usage", grant.usage).put("mode", grant.mode.key)
                        .put("expiresAt", grant.expiresAt)))
                "ping" -> JSONObject()
                "tools/list" -> JSONObject().put("tools", JSONArray(StoneAutomation.available(grant.mode, grant.disabledTools).map { action ->
                    JSONObject().put("name", action.toolName).put("description", action.description)
                        .put("inputSchema", action.inputSchema())
                        .put("annotations", JSONObject().put("readOnlyHint", action.risk == ActionRisk.READ)
                            .put("destructiveHint", action.risk == ActionRisk.CRITICAL))
                }))
                "tools/call" -> callTool(params, grant)
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

    private fun callTool(params: JSONObject, grant: ApiToken): JSONObject {
        val name = params.optString("name")
        val arguments = params.optJSONObject("arguments") ?: JSONObject()
        return try {
            val result = runBlocking { StoneAutomation.execute(name, arguments, grant.mode, grant.name, grant.id, grant.disabledTools, currentRelay.get()) }
            JSONObject().put("content", JSONArray().put(JSONObject().put("type", "text").put("text", result.toString())))
                .put("isError", false).apply { if (result is JSONObject) put("structuredContent", result) }
        } catch (failure: ActionException) {
            if (StoneAutomation.find(name) == null) throw IllegalArgumentException(
                "Unknown tool '$name'. Call tools/list or the stone_help tool to see the valid names.")
            // Tool errors go in the result, with a stable code, so the model can read them and correct itself.
            val text = "[${failure.code}] ${failure.message}" + (failure.hint?.let { " Hint: $it" } ?: "")
            JSONObject().put("content", JSONArray().put(JSONObject().put("type", "text").put("text", text)))
                .put("structuredContent", JSONObject().put("ok", false).put("error", failure.json()))
                .put("isError", true)
        }
    }

    private fun headerToken(request: McpRequest): String? {
        val bearer = request.header("authorization")?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }?.substring(7)?.trim()
        return (bearer ?: request.header("x-api-key")?.trim())?.takeIf { it.isNotEmpty() }
    }

    private fun givenToken(request: McpRequest): String? = headerToken(request)
        ?: request.query["token"]?.trim()?.takeIf { it.isNotEmpty() }

    /** The token the request carries, compared in constant time, or null when missing or revoked. */
    private fun grantFor(request: McpRequest): ApiToken? {
        val given = givenToken(request)?.toByteArray() ?: return null
        val now = System.currentTimeMillis()
        return grants.values.firstOrNull { MessageDigest.isEqual(given, it.secret.toByteArray()) }
            ?.takeIf { (it.expiresAt == 0L || it.expiresAt > now) && !it.paused }
    }

    /** Whether [tokenId] can still be used right now (exists, not paused, not expired). */
    fun isActive(tokenId: String): Boolean {
        val now = System.currentTimeMillis()
        return grants.values.any { it.id == tokenId && !it.paused && (it.expiresAt == 0L || it.expiresAt > now) }
    }

    private fun isPaused(request: McpRequest): Boolean {
        val given = givenToken(request)?.toByteArray() ?: return false
        return grants.values.any { it.paused && MessageDigest.isEqual(given, it.secret.toByteArray()) }
    }

    private fun error(id: Any?, code: Int, message: String) = JSONObject().put("jsonrpc", "2.0")
        .put("id", id ?: JSONObject.NULL).put("error", JSONObject().put("code", code).put("message", message))
}
