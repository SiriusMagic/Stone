package com.zirakusa.stone.nativeapp.automation

import com.zirakusa.stone.nativeapp.automation.actions.ActionRisk
import com.zirakusa.stone.nativeapp.automation.actions.StoneAutomation
import com.zirakusa.stone.nativeapp.automation.auth.AccessMode
import com.zirakusa.stone.nativeapp.automation.server.McpServer
import org.json.JSONArray
import org.json.JSONObject

/**
 * Local bridge for the Gateway Hub app on this phone (phase 1 of the hub migration): MCP JSON-RPC
 * messages over a protected Bound Service ([HubBridgeService]) instead of HTTP and the relay.
 *
 * - Identity comes only from Android (the caller's uid → its packages → their signature); nothing the
 *   caller writes in a message says who it is.
 * - READ-ONLY in this phase, on both tools/list and tools/call, from each action's declared [ActionRisk].
 * - Same switches as Redirección hub: the user's "Redirección hub" and Modo API (see ../../docs: the Modo API
 *   dependency is kept on purpose until a separate consent switch exists).
 * - Messages are bounded so they always fit in one Binder transaction.
 */
object HubBridge {
    const val ACTION = "com.zirakusa.gatewayhub.action.MCP"
    const val GATEWAY_PACKAGE = "com.zirakusa.gatewayhub"
    const val MSG_RPC = 1
    const val KEY_MESSAGE = "message"
    /** Characters per message. A Parcel stores text as UTF-16 (2 bytes per char): ~400 KB, under Binder's 1 MB shared buffer. */
    const val MAX_CHARS = 200_000
    /** Tool metadata key that marks the one tool Gateway may run to test the bridge. */
    const val PROBE_META = "com.zirakusa.gatewayhub/probe"
    /** Reviewed by hand: reads only, no side effects, works without Stone's screen. Nothing else is a probe. */
    val PROBE_TOOLS = setOf("stone_help")

    /** Why [callerPackages] may not use the bridge, or null when one of them is Gateway Hub signed like Stone. */
    fun callerRefusal(callerPackages: List<String>, sameSignature: (String) -> Boolean): String? = when {
        GATEWAY_PACKAGE !in callerPackages -> "NOT_GATEWAY"
        !sameSignature(GATEWAY_PACKAGE) -> "NOT_SAME_DEVELOPER"
        else -> null
    }

    /** The user's switches; null when the bridge may answer. */
    fun switchRefusal(): String? = when {
        !ApiMode.hubRedirect -> "REDIRECT_OFF"
        !ApiMode.enabled -> "API_OFF"
        else -> null
    }

    /** Handles one MCP message from an authenticated Gateway; null for a notification (nothing to answer). */
    fun handle(text: String?): String? {
        if (text == null || text.length > MAX_CHARS) return error(null, -32600, "Message missing or larger than $MAX_CHARS characters.")
        val message = runCatching { JSONObject(text) }.getOrNull()
            ?: return error(null, -32600, "Send one JSON-RPC object per message (no batches).")
        val id = message.opt("id")
        val method = message.optString("method")
        if (method == "tools/call") {
            val name = message.optJSONObject("params")?.optString("name").orEmpty()
            val action = StoneAutomation.find(name)
            if (action != null && action.risk != ActionRisk.READ) return forbidden(id, name)
        }
        val token = ApiMode.hubToken()
        if (token.paused || (token.expiresAt != 0L && token.expiresAt <= System.currentTimeMillis()))
            return error(id, -32001, "The 'Gateway Hub' token is paused or expired in Stone.")
        // The bridge always works with read-only rights, whatever the reserved token allows on HTTP.
        val reply = McpServer.handleLocal(message.toString(), token.copy(mode = AccessMode.READ)) ?: return null
        val marked = if (method == "tools/list") markProbes(reply) else reply
        return if (marked.length > MAX_CHARS) error(id, -32000, "RESPONSE_TOO_LARGE: the answer does not fit the local bridge.") else marked
    }

    private fun markProbes(reply: String): String {
        val json = JSONObject(reply)
        val tools = json.optJSONObject("result")?.optJSONArray("tools") ?: return reply
        for (i in 0 until tools.length()) tools.getJSONObject(i).let { tool ->
            if (tool.optString("name") in PROBE_TOOLS) tool.put("_meta", JSONObject().put(PROBE_META, true))
        }
        return json.toString()
    }

    private fun forbidden(id: Any?, name: String): String = JSONObject().put("jsonrpc", "2.0").put("id", id ?: JSONObject.NULL)
        .put("result", JSONObject().put("isError", true)
            .put("content", JSONArray().put(JSONObject().put("type", "text").put("text", "[READ_ONLY_BRIDGE] '$name' changes something; the local bridge only reads.")))
            .put("structuredContent", JSONObject().put("ok", false).put("error", JSONObject().put("code", "READ_ONLY_BRIDGE")))).toString()

    fun error(id: Any?, code: Int, message: String): String = JSONObject().put("jsonrpc", "2.0").put("id", id ?: JSONObject.NULL)
        .put("error", JSONObject().put("code", code).put("message", message)).toString()
}
