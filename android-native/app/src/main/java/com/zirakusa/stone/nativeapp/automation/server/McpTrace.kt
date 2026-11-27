package com.zirakusa.stone.nativeapp.automation.server

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject

/** A receipt observable on the phone even when the reply never reached the caller. */
internal object McpTrace {
    private val idShape = Regex("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}")
    private val tools = setOf("player_play_by_title", "player_state", "ping")

    data class Receipt(val id: String, val tool: String, val local: Boolean, val code: String, val millis: Long)

    var last by mutableStateOf<Receipt?>(null)
        private set

    fun track(id: Any, tool: String, local: Boolean, call: () -> JSONObject): JSONObject {
        if (id !is String || !idShape.matches(id) || tool !in tools) return call()
        val started = SystemClock.elapsedRealtime()
        last = Receipt(id, tool, local, "RECIBIDA", 0)
        var code = "ERROR"
        try {
            val result = call()
            val data = result.optJSONObject("structuredContent")
            code = if (result.optBoolean("isError")) data?.optJSONObject("error")?.optString("code") ?: "ERROR"
                else data?.optString("status")?.takeIf(String::isNotBlank) ?: "OK"
            return result
        } finally {
            if (last?.id == id) last = Receipt(id, tool, local, code, SystemClock.elapsedRealtime() - started)
        }
    }
}
