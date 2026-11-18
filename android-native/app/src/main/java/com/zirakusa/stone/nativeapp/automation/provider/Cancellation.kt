package com.zirakusa.stone.nativeapp.automation.provider

import org.json.JSONObject

/** Reads, without trusting more than needed, which request a message is (or which one it cancels). */
object Cancellation {
    /** The requestId a `notifications/cancelled` names, or null for any other message. */
    fun target(text: String?): String? = runCatching {
        val m = JSONObject(text ?: return null)
        if (m.optString("method") != "notifications/cancelled") return null
        m.optJSONObject("params")?.opt("requestId")?.toString()?.take(64)
    }.getOrNull()

    /** The id of a tools/call (the only calls worth cancelling). */
    fun key(text: String?): String? = runCatching {
        val m = JSONObject(text ?: return null)
        if (m.optString("method") == "tools/call") m.opt("id")?.toString()?.take(64) else null
    }.getOrNull()
}
