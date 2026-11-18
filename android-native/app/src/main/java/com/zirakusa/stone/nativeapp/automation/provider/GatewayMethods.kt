package com.zirakusa.stone.nativeapp.automation.provider

import com.zirakusa.stone.nativeapp.BuildConfig
import org.json.JSONArray
import org.json.JSONObject

/**
 * The provider contract's own methods (prefixed, so they never pass for standard MCP methods):
 * gatewayhub/link, gatewayhub/packages/offer|revoke|list, gatewayhub/state. The caller was already verified.
 */
object GatewayMethods {
    fun handle(id: Any?, method: String, params: JSONObject): String? {
        if (id == null || id == JSONObject.NULL) return null
        val result: JSONObject = when (method) {
            "gatewayhub/link" -> {
                if (!ProviderState.link(params.optString("env"), params.optString("publicKey")))
                    return ProviderBridge.error(id, -32001, if (ProviderState.disconnectedByUser) "DISCONNECTED_BY_USER" else "BAD_LINK")
                JSONObject().put("instance", ProviderState.instanceId).put("contract", ProviderState.CONTRACT)
                    .put("prefix", "music_").put("version", BuildConfig.VERSION_NAME).put("packages", Packages.statusJson())
            }
            "gatewayhub/packages/offer" -> {
                val offered = params.optJSONArray("packages") ?: JSONArray()
                JSONObject().put("packages", JSONArray((0 until minOf(offered.length(), 16)).map { i ->
                    try {
                        val p = Packages.install(offered.getJSONObject(i), BuildConfig.APPLICATION_ID)
                        JSONObject().put("id", p.id).put("status", if (p.approved) "active" else "pending")
                    } catch (e: Exception) {
                        JSONObject().put("index", i).put("error", (e as? PackageException)?.code ?: "MALFORMED")
                    }
                }))
            }
            "gatewayhub/packages/revoke" -> { Packages.revoke(params.optString("id")); JSONObject().put("packages", Packages.statusJson()) }
            "gatewayhub/packages/list" -> JSONObject().put("packages", Packages.statusJson())
            "gatewayhub/state" -> {
                ProviderState.report(params.optString("environment"), params.optString("mode"), params.optLong("lastActivity"))
                JSONObject()
            }
            else -> return ProviderBridge.error(id, -32601, "Unknown method $method.")
        }
        return JSONObject().put("jsonrpc", "2.0").put("id", id).put("result", result).toString()
    }
}
