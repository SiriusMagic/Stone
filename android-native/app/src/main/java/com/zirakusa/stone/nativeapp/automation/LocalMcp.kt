package com.zirakusa.stone.nativeapp.automation

import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import com.zirakusa.stone.nativeapp.automation.actions.StoneAutomation
import com.zirakusa.stone.nativeapp.automation.server.McpServer
import com.zirakusa.stone.nativeapp.data.persistence.StoneSettingsStore
import org.json.JSONObject

/** The phone-only MCP channel. Its permission and read-only grant are independent of the relay. */
internal object LocalMcp {
    const val SETTING = "api.localMcp"
    private val tools = setOf("library_search", "player_state")

    fun answer(sameDeveloper: Boolean, enabled: Boolean, body: String?,
        dispatch: (String, Set<String>) -> String? = McpServer::localRead): Bundle {
        if (!sameDeveloper) return failure("NOT_SAME_DEVELOPER")
        if (!enabled) return failure("LOCAL_MCP_OFF")
        if (body == null || body.length > 8_192) return failure("BAD_REQUEST")
        val request = runCatching { JSONObject(body) }.getOrNull() ?: return failure("BAD_REQUEST")
        if (request.optString("jsonrpc") != "2.0" || request.isNull("id")) return failure("BAD_REQUEST")
        when (request.optString("method")) {
            "tools/list" -> Unit
            "tools/call" -> {
                val params = request.optJSONObject("params") ?: return failure("BAD_REQUEST")
                if (params.optString("name") !in tools) return failure("TOOL_NOT_ALLOWED")
                if (params.has("arguments") && params.optJSONObject("arguments") == null) return failure("BAD_REQUEST")
            }
            else -> return failure("BAD_REQUEST")
        }
        return dispatch(request.toString(), tools)?.let { Bundle().apply { putString("response", it) } }
            ?: failure("NOT_READY")
    }

    fun failure(code: String) = Bundle().apply { putString("error", code) }
}

class LocalMcpProvider : ContentProvider() {
    override fun onCreate() = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (method != "rpc") return null
        val app = context ?: return null
        val caller = callingPackage
        val trusted = caller != null &&
            app.packageManager.checkSignatures(app.packageName, caller) == PackageManager.SIGNATURE_MATCH
        // Read the existing opt-in directly. Loading the remote MCP configuration could start the relay.
        val enabled = trusted && StoneSettingsStore(app).look(LocalMcp.SETTING, "false").toBoolean()
        return LocalMcp.answer(trusted, enabled, extras?.getString("request")) { request, allowed ->
            if (JSONObject(request).optString("method") == "tools/call" && StoneAutomation.host == null) null
            else McpServer.localRead(request, allowed)
        }
    }

    override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, s: String?, a: Array<out String>?): Int = 0
}
