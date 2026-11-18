package com.zirakusa.stone.nativeapp.automation.provider

import com.zirakusa.stone.nativeapp.automation.auth.AccessMode
import org.json.JSONObject

/**
 * The rights Gateway Hub delegates for one request, sent in `params._meta["com.zirakusa.gatewayhub/grant"]`:
 * `{"mode": "read"|"write"|"full", "blockedTools": [...], "agent": "<id>"}`.
 *
 * Stone accepts it only because the sender was already verified by Android (uid + signature, see
 * [ProviderBridge]); it never says who the sender is. It is always cut down to Stone's own [LOCAL_CAP], and
 * anything missing or malformed means "no extra rights" (the cap, nothing blocked beyond it).
 */
object GatewayGrant {
    const val META = "com.zirakusa.gatewayhub/grant"
    /** The most Stone lets Gateway do WITHOUT an execution package: read only. */
    val LOCAL_CAP = AccessMode.READ
    /** With an approved package: WRITE, never FULL, so CRITICAL actions always ask on screen. */
    val PACKAGE_CAP = AccessMode.WRITE
    private const val MAX_BLOCKED = 300
    private val NAME = Regex("^[a-z][a-z0-9_]{1,63}$")
    private val AGENT = Regex("^[A-Za-z0-9_-]{1,40}$")

    fun from(message: JSONObject?): CallerGrant {
        val meta = message?.optJSONObject("params")?.optJSONObject("_meta")?.optJSONObject(META)
        val requested = when (meta?.optString("mode")) { "full" -> AccessMode.FULL; "write" -> AccessMode.WRITE; else -> AccessMode.READ }
        val mode = if (requested.ordinal <= LOCAL_CAP.ordinal) requested else LOCAL_CAP
        val blocked = meta?.optJSONArray("blockedTools")?.let { list ->
            (0 until minOf(list.length(), MAX_BLOCKED)).mapNotNull { list.optString(it).takeIf(NAME::matches) }.toSet()
        }.orEmpty()
        val agent = meta?.optString("agent")?.takeIf(AGENT::matches)
        return CallerGrant(if (agent == null) "gateway" else "gateway:$agent", "Gateway Hub", mode, blocked)
    }

    /** The agent and the execution package Gateway names for this request (both checked by [Packages]). */
    fun agent(message: JSONObject?): String? = meta(message)?.optString("agent")?.takeIf(AGENT::matches)
    fun packageId(message: JSONObject?): String? = meta(message)?.optString("package")?.takeIf { it.startsWith("pkg_") && it.length <= 70 }

    private fun meta(message: JSONObject?) = message?.optJSONObject("params")?.optJSONObject("_meta")?.optJSONObject(META)
}
