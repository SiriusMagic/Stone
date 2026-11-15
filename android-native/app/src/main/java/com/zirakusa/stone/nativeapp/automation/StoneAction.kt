package com.zirakusa.stone.nativeapp.automation

import org.json.JSONArray
import org.json.JSONObject

/** How dangerous an action is; decides whether it runs on its own or asks the user. */
enum class ActionRisk {
    /** Only reads state. Always automatic. */
    READ,
    /** Changes something easy to undo (playback, theme). Automatic while API mode is on. */
    REVERSIBLE,
    /** Writes files or library data. Always asks for confirmation on the phone. */
    PERSISTENT,
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

class ActionException(message: String) : Exception(message)

/** Entry of the in-app activity list (Ajustes › Modo API). */
data class ActionRecord(val timeMs: Long, val action: String, val ok: Boolean, val detail: String)
