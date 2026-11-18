package com.zirakusa.stone.nativeapp.automation.provider

import com.zirakusa.stone.nativeapp.automation.actions.ActionRisk
import com.zirakusa.stone.nativeapp.automation.actions.StoneAutomation
import org.json.JSONArray
import org.json.JSONObject

/**
 * Stone as a capability provider for the Gateway Hub app on this phone: MCP JSON-RPC messages over a
 * protected Bound Service ([ProviderService]), handled by [McpDispatcher] (no HTTP, no relay, no Stone token).
 *
 * - Identity comes only from Android (the caller's uid → its packages → their signature); nothing the
 *   caller writes in a message says who it is.
 * - Rights: those Gateway delegates per request ([GatewayGrant]), never above Stone's local cap (read-only in
 *   this phase), checked on both tools/list and tools/call from each action's declared [ActionRisk].
 * - Nothing to switch on in Stone: the user can only cut it ("Desconectar de Gateway Hub", [ProviderState]).
 * - Changes need an execution package ([Packages]) the user approved in Stone; without one, read-only.
 * - Messages are bounded so they always fit in one Binder transaction.
 */
object ProviderBridge {
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

    /** Phase 2: no switch to turn on in Stone; only the user's "Desconectar de Gateway Hub" stops the bridge. */
    fun switchRefusal(): String? = if (ProviderState.disconnectedByUser) "DISCONNECTED_BY_USER" else null

    /** Handles one MCP message from an authenticated Gateway; null for a notification (nothing to answer). */
    fun handle(text: String?): String? {
        if (text == null || text.length > MAX_CHARS) return error(null, -32600, "Message missing or larger than $MAX_CHARS characters.")
        val message = runCatching { JSONObject(text) }.getOrNull()
            ?: return error(null, -32600, "Send one JSON-RPC object per message (no batches).")
        val id = message.opt("id")
        val method = message.optString("method")
        if (method.startsWith("gatewayhub/")) return GatewayMethods.handle(id, method, message.optJSONObject("params") ?: JSONObject())
        val packageId = GatewayGrant.packageId(message)
        val agent = GatewayGrant.agent(message) ?: "*"
        var grant = GatewayGrant.from(message)
        if (method == "tools/call") {
            val params = message.optJSONObject("params") ?: JSONObject()
            val name = params.optString("name")
            val action = StoneAutomation.find(name)
            if (action != null && packageId != null) {
                // An execution package decides; "revisar" only previews. CRITICAL still asks on screen (mode WRITE).
                val args = params.optJSONObject("arguments") ?: JSONObject().also { params.put("arguments", it) }
                // The first candidate that covers the call wins; if none does, the first one's reason is shown.
                val tried = GatewayGrant.packageIds(message).map { runCatching { Packages.authorize(it, agent, action, args) } }
                val act = tried.firstNotNullOfOrNull { it.getOrNull() }
                    ?: return refused(id, (tried.first().exceptionOrNull() as? PackageException)?.code ?: "NO_PACKAGE")
                if (act == Act.REVISAR) args.put("dryRun", true)
                grant = grant.copy(mode = GatewayGrant.PACKAGE_CAP)
            } else if (action != null && !grant.mode.permits(action.risk)) return forbidden(id, name)
        } else if (method == "tools/list" && packageId != null) grant = grant.copy(mode = GatewayGrant.PACKAGE_CAP)
        val reply = McpDispatcher.dispatch(message.toString(), grant).first?.toString() ?: return null
        val listed = if (method == "tools/list" && packageId != null) onlyPackaged(reply, agent) else reply
        val marked = if (method == "tools/list") markProbes(listed) else listed
        return if (marked.length > MAX_CHARS) error(id, -32000, "RESPONSE_TOO_LARGE: the answer does not fit the local bridge.") else marked
    }

    /** tools/list under packages: only the tools some approved package of [agent] covers. */
    private fun onlyPackaged(reply: String, agent: String): String {
        val json = JSONObject(reply)
        val tools = json.optJSONObject("result")?.optJSONArray("tools") ?: return reply
        val kept = JSONArray()
        for (i in 0 until tools.length()) tools.getJSONObject(i).let { t ->
            StoneAutomation.find(t.optString("name"))?.let { if (Packages.lists(agent, it)) kept.put(t) }
        }
        json.getJSONObject("result").put("tools", kept)
        return json.toString()
    }

    private fun refused(id: Any?, code: String): String = JSONObject().put("jsonrpc", "2.0").put("id", id ?: JSONObject.NULL)
        .put("result", JSONObject().put("isError", true)
            .put("content", JSONArray().put(JSONObject().put("type", "text").put("text", "[$code] No approved Stone package allows this.")))
            .put("structuredContent", JSONObject().put("ok", false).put("error", JSONObject().put("code", code)))).toString()

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
            .put("content", JSONArray().put(JSONObject().put("type", "text").put("text", "[READ_ONLY_BRIDGE] '$name' changes something; Gateway Hub needs an approved Stone package for that.")))
            .put("structuredContent", JSONObject().put("ok", false).put("error", JSONObject().put("code", "READ_ONLY_BRIDGE")))).toString()

    fun error(id: Any?, code: Int, message: String): String = McpDispatcher.error(id, code, message).toString()
}
