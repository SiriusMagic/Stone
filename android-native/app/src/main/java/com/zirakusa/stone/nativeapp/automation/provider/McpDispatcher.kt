package com.zirakusa.stone.nativeapp.automation.provider

import com.zirakusa.stone.nativeapp.BuildConfig
import com.zirakusa.stone.nativeapp.automation.actions.ActionException
import com.zirakusa.stone.nativeapp.automation.actions.ActionRisk
import com.zirakusa.stone.nativeapp.automation.actions.StoneAutomation
import com.zirakusa.stone.nativeapp.automation.auth.AccessMode
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject

/** Who a request runs for and with which rights (Gateway Hub over IPC, with an execution package). */
data class CallerGrant(val id: String, val name: String, val mode: AccessMode, val disabledTools: Set<String>)

/**
 * Stone's MCP JSON-RPC handling, with no sockets and no tokens: initialize, ping, tools/list, tools/call.
 * Stone has no network server of its own: Gateway Hub reaches it over IPC ([ProviderService]).
 */
object McpDispatcher {
    private val protocolVersions = listOf("2025-11-25", "2025-06-18", "2025-03-26", "2024-11-05")

    /** Parses one message or a batch; returns the reply (null for notifications) and whether it initialized. */
    fun dispatch(text: String, grant: CallerGrant): Pair<Any?, Boolean> = try {
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
    private fun handle(message: JSONObject, grant: CallerGrant): JSONObject? {
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
                        "Edit tools accept dryRun=true to preview changes; every edit is backed up and metadata_undo reverts it. " +
                        "Errors carry a code (structuredContent.error.code). " + when (grant.mode) {
                            AccessMode.READ -> "This token is READ-ONLY: only query tools are listed."
                            AccessMode.WRITE -> "Only critical actions (like deleting a playlist) ask the user on the phone (up to 60 s)."
                            AccessMode.FULL -> "Full access: nothing asks the user for approval."
                        })
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

    private fun callTool(params: JSONObject, grant: CallerGrant): JSONObject {
        val name = params.optString("name")
        val arguments = params.optJSONObject("arguments") ?: JSONObject()
        return try {
            val result = runBlocking { StoneAutomation.execute(name, arguments, grant.mode, grant.name, grant.id, grant.disabledTools) }
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

    fun error(id: Any?, code: Int, message: String): JSONObject = JSONObject().put("jsonrpc", "2.0")
        .put("id", id ?: JSONObject.NULL).put("error", JSONObject().put("code", code).put("message", message))
}
