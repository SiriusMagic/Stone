package com.zirakusa.stone.nativeapp.automation

import org.json.JSONArray
import org.json.JSONObject

/**
 * What a token lets an agent do. Approval is asked only where it matters:
 * - READ: queries only.
 * - WRITE: everything; only CRITICAL actions (destructive, cannot be undone) ask on the phone.
 * - FULL: everything, nothing asks. Edits are still backed up and can be undone.
 */
enum class AccessMode(val key: String) {
    READ("read"), WRITE("write"), FULL("full");

    fun permits(risk: ActionRisk): Boolean = this != READ || risk == ActionRisk.READ
    fun needsApproval(risk: ActionRisk): Boolean = this == WRITE && risk == ActionRisk.CRITICAL

    companion object {
        fun from(key: String?) = entries.firstOrNull { it.key == key } ?: WRITE
    }
}

/** One access token. Each agent (Claude, ChatGPT, tests…) can have its own, revocable on its own. */
data class ApiToken(val id: String, val name: String, val secret: String, val mode: AccessMode,
    val createdAt: Long, val lastUsedAt: Long = 0,
    /** 0 = never; after this time the server rejects the token. */
    val expiresAt: Long = 0,
    /** Tools this token may not use, on top of the global switches (checked on every request). */
    val disabledTools: Set<String> = emptySet()) {
    val expired: Boolean get() = expiresAt in 1..System.currentTimeMillis()


    fun json(): JSONObject = JSONObject().put("id", id).put("name", name).put("secret", secret).put("mode", mode.key)
        .put("createdAt", createdAt).put("lastUsedAt", lastUsedAt).put("expiresAt", expiresAt)
        .put("disabledTools", JSONArray(disabledTools.sorted()))

    /** Shown in lists: first and last characters only. */
    val preview: String get() = if (secret.length <= 10) secret else "${secret.take(8)}•••••${secret.takeLast(4)}"

    companion object {
        fun parse(text: String): List<ApiToken> = runCatching {
            val array = JSONArray(text)
            (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                ApiToken(item.getString("id"), item.optString("name"), item.getString("secret"),
                    AccessMode.from(item.optString("mode")), item.optLong("createdAt"), item.optLong("lastUsedAt"),
                    item.optLong("expiresAt"), item.optJSONArray("disabledTools")?.let { list ->
                        (0 until list.length()).map { list.getString(it) }.toSet() } ?: emptySet())
            }.filter { it.secret.isNotBlank() }
        }.getOrDefault(emptyList())

        fun serialize(tokens: List<ApiToken>): String = JSONArray(tokens.map { it.json() }).toString()
    }
}
