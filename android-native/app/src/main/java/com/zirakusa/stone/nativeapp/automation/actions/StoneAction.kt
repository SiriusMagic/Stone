package com.zirakusa.stone.nativeapp.automation.actions

import org.json.JSONArray
import org.json.JSONObject

/** How dangerous an action is; with the token's [AccessMode] it decides whether the user is asked. */
enum class ActionRisk {
    /** Only reads state. */
    READ,
    /** Changes something easy to undo (playback, theme). */
    REVERSIBLE,
    /** Writes tags, lyrics or covers. A backup is kept first, so metadata_undo can revert it. */
    PERSISTENT,
    /** Destructive and not undoable (deleting a playlist). Asks on the phone in "Lectura y escritura". */
    CRITICAL,
}

class ActionParam(val name: String, val type: String, val description: String, val required: Boolean = false,
    val options: List<String>? = null)

/**
 * One capability of Stone that an agent can use. The same definition feeds the MCP tool list
 * (name, description, JSON Schema) and the executor, so a feature is registered only once.
 */
class StoneAction(
    val name: String,
    val description: String,
    val risk: ActionRisk,
    val params: List<ActionParam> = emptyList(),
    /** Human sentence for the confirmation dialog of PERSISTENT actions. */
    val summary: (JSONObject) -> String = { name },
    val run: suspend (JSONObject) -> Any,
) {
    /** MCP tool names cannot contain dots. */
    val toolName: String get() = name.replace('.', '_')

    fun inputSchema(): JSONObject = JSONObject().put("type", "object")
        .put("properties", JSONObject().apply {
            params.forEach { param ->
                put(param.name, JSONObject().put("type", param.type).put("description", param.description).apply {
                    param.options?.let { put("enum", JSONArray(it)) }
                })
            }
        })
        .put("required", JSONArray(params.filter { it.required }.map { it.name }))
        .put("additionalProperties", false)
}

/**
 * Error returned to the agent with a stable [code] (see docs/MCP.md) and an optional [hint]
 * telling it how to recover.
 */
class ActionException(message: String, val code: String = "ACTION_FAILED", val hint: String? = null) : Exception(message) {
    fun json(): JSONObject = JSONObject().put("code", code).put("message", message).apply {
        hint?.let { put("hint", it) }
        if (StoneSolutions.has(code)) put("solution", "stone_troubleshoot code=$code")
    }

    companion object {
        /** Any failure as an error with a stable code: web limits and network problems included. */
        fun of(error: Throwable): ActionException = when (error) {
            is ActionException -> error
            is com.zirakusa.stone.nativeapp.data.library.web.RateLimitedException -> ActionException(error.message.orEmpty(),
                "RATE_LIMITED", "Wait ${error.retryAfterSeconds} s, then continue with 5-10 songs per call " +
                    "(or use background=true so Stone waits and resumes by itself).")
            is java.net.SocketTimeoutException, is java.net.UnknownHostException, is java.net.ConnectException,
            is javax.net.ssl.SSLException -> ActionException("Network problem: ${error.message ?: error.javaClass.simpleName}",
                "NETWORK", "Check the phone's internet connection and try again.")
            else -> ActionException(error.message ?: "Action failed")
        }
    }
}

/** Outcome of an agent request, as shown in Ajustes › Modo MCP › Actividad. */
enum class RecordStatus { OK, FAILED, DENIED }

/** Entry of the in-app activity list. [risk] tells queries from changes. */
data class ActionRecord(val timeMs: Long, val action: String, val ok: Boolean, val detail: String,
    val status: RecordStatus = if (ok) RecordStatus.OK else RecordStatus.FAILED, val risk: ActionRisk = ActionRisk.READ,
    val token: String = "",
    /** Error code (RATE_LIMITED, LOW_CONFIDENCE…) when the call failed. */
    val code: String = "",
    /** Id of the token that made the call ("" = Stone itself); an agent only sees its own calls. */
    val tokenId: String = "")
