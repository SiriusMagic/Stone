package com.zirakusa.stone.nativeapp.automation.activity

import com.zirakusa.stone.nativeapp.data.persistence.StoneSettingsStore
import org.json.JSONArray
import org.json.JSONObject
import com.zirakusa.stone.nativeapp.automation.actions.ActionRecord
import com.zirakusa.stone.nativeapp.automation.actions.ActionRisk
import com.zirakusa.stone.nativeapp.automation.actions.RecordStatus
import com.zirakusa.stone.nativeapp.automation.actions.StoneAutomation

/**
 * Keeps Ajustes › Gateway Hub › Actividad on the phone, so the last calls and their errors are still
 * there after Stone is closed and an agent can read them with stone_activity. Failures are saved
 * at once; successful calls at most every few seconds.
 */
internal object ActivityLog {
    private const val KEY = "api.activity"
    private const val SAVE_EVERY_MS = 5_000L
    private var store: StoneSettingsStore? = null
    private var lastSaved = 0L

    fun attach(store: StoneSettingsStore) {
        this.store = store
        StoneAutomation.loadHistory(parse(store.look(KEY, "[]")))
    }

    fun recorded(history: List<ActionRecord>, failed: Boolean) {
        val target = store ?: return
        val now = System.currentTimeMillis()
        if (!failed && now - lastSaved < SAVE_EVERY_MS) return
        lastSaved = now
        runCatching { target.setLook(KEY, serialize(history)) }
    }

    fun serialize(history: List<ActionRecord>): String = JSONArray(history.map { record ->
        JSONObject().put("t", record.timeMs).put("a", record.action).put("s", record.status.name).put("d", record.detail)
            .put("r", record.risk.name).put("k", record.token).put("c", record.code).put("i", record.tokenId)
    }).toString()

    fun parse(raw: String): List<ActionRecord> = runCatching {
        val list = JSONArray(raw)
        (0 until list.length()).mapNotNull { index ->
            val item = list.optJSONObject(index) ?: return@mapNotNull null
            val status = runCatching { RecordStatus.valueOf(item.optString("s")) }.getOrDefault(RecordStatus.FAILED)
            ActionRecord(item.optLong("t"), item.optString("a"), status == RecordStatus.OK, item.optString("d"), status,
                runCatching { ActionRisk.valueOf(item.optString("r")) }.getOrDefault(ActionRisk.READ),
                item.optString("k"), item.optString("c"), item.optString("i"))
        }
    }.getOrDefault(emptyList())

    /** stone_activity: newest first, optionally only failures, one tool or one token. */
    fun query(history: List<ActionRecord>, onlyErrors: Boolean, tool: String?, token: String?, limit: Int,
        ownerTokenId: String = ""): JSONObject {
        val selected = history.filter { record ->
            (ownerTokenId.isEmpty() || record.tokenId == ownerTokenId) &&
            (!onlyErrors || record.status != RecordStatus.OK) &&
                (tool == null || record.action.replace('.', '_') == tool.replace('.', '_')) &&
                (token == null || record.token.equals(token, ignoreCase = true))
        }
        val codes = selected.filter { it.code.isNotBlank() }.groupingBy { it.code }.eachCount()
        return JSONObject().put("total", selected.size).put("errorCodes", JSONObject(codes as Map<*, *>))
            .put("tip", "For each code, stone_troubleshoot code=CODE explains the cause and the fix.")
            .put("calls", JSONArray(selected.take(limit).map { record ->
                JSONObject().put("time", record.timeMs).put("tool", record.action.replace('.', '_'))
                    .put("status", record.status.name.lowercase()).put("token", record.token)
                    .put(if (record.status == RecordStatus.OK) "arguments" else "error", record.detail)
                    .apply { if (record.code.isNotBlank()) put("code", record.code) }
            }))
    }
}
