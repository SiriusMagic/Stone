package com.zirakusa.stone.nativeapp.automation

import com.zirakusa.stone.nativeapp.BuildConfig
import org.json.JSONArray
import org.json.JSONObject

/**
 * Discovery help for agents: a search tool over the catalogue and the plain-language guide the
 * server returns to simple GET requests, so a client that cannot speak MCP still learns how to
 * connect instead of only seeing "405".
 */
internal object StoneGuide {
    private val categories = linkedMapOf(
        "player" to "Playback, queue, volume, speed, shuffle/repeat, sleep timer",
        "equalizer" to "Ten-band equalizer and presets",
        "video" to "Video scale, screen brightness, orientation",
        "library" to "Search, browse, sort, favourites, lyrics, rescan",
        "playlist" to "Playlists and their songs",
        "metadata" to "Tags, lyrics and covers of songs and videos (titles, artists, albums, external cover URLs)",
        "appearance" to "Theme, colours, sizes, home icon",
        "settings" to "Language and audio mode",
        "stone" to "Help and tool search")

    private fun StoneAction.brief() = JSONObject().put("name", toolName).put("description", description)
        .put("needsPhoneApproval", risk == ActionRisk.PERSISTENT).put("readOnly", risk == ActionRisk.READ)
        .put("parameters", JSONArray(params.map { param ->
            JSONObject().put("name", param.name).put("type", param.type).put("required", param.required)
                .put("description", param.description).apply { param.options?.let { put("options", JSONArray(it)) } }
        }))

    /** Tools whose name, description or parameters contain every word of [query]. */
    fun search(query: String, category: String?): JSONObject {
        val words = query.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotBlank() }
        val matches = StoneAutomation.available.filter { action ->
            val text = (action.name + " " + action.description + " " + action.params.joinToString(" ") { it.name + " " + it.description }).lowercase()
            (category == null || action.name.substringBefore('.') == category) && words.all { it in text }
        }
        return JSONObject().put("categories", JSONObject(categories as Map<*, *>))
            .put("workflow", "1) library_search to get song ids. 2) player_play_song / player_queue_next / playlist_add_songs with those ids. " +
                "3) player_state to confirm. Tools marked needsPhoneApproval wait up to 60 s for the user to accept on the phone.")
            .put("total", matches.size).put("tools", JSONArray(matches.map { it.brief() }))
    }

    val action = StoneAction("stone.help", "START HERE if unsure. Lists Stone's tools by category or finds them by intention " +
        "(e.g. query \"equalizer\", \"brightness\", \"queue\"). Returns names, parameters and the usual workflow.",
        ActionRisk.READ, listOf(
            ActionParam("query", "string", "Words to look for in tool names and descriptions. Empty lists everything."),
            ActionParam("category", "string", "Restrict to one category.", options = categories.keys.toList()))) { args ->
        search(args.optString("query"), args.optString("category").ifBlank { null })
    }

    /** JSON shown to a plain GET (browser, curl, an agent probing the link). */
    fun overview(): JSONObject = JSONObject()
        .put("server", "Stone").put("version", BuildConfig.VERSION_NAME).put("protocol", "Model Context Protocol (MCP)")
        .put("status", "ok: the token is valid and Stone is reachable")
        .put("howToConnect", JSONObject()
            .put("mcp", "POST JSON-RPC 2.0 to this same URL (Streamable HTTP). Methods: initialize, tools/list, tools/call. " +
                "Headers: Content-Type: application/json, Accept: application/json, text/event-stream.")
            .put("legacySse", "GET /sse (same token) and POST to the endpoint it announces.")
            .put("withoutMcp", "If you can only make GET requests, change the end of the path and keep the same ?token=: " +
                ".../tools lists every tool with its parameters, and .../call/TOOL_NAME?param=value runs one. " +
                "Examples: /call/player_state , /call/player_set_volume?percent=40 , /call/library_search?query=love&limit=5")
            .put("health", ".../health needs no token and only says whether Stone is running."))
        .put("firstSteps", JSONArray(listOf("stone_help", "player_state", "library_search")))
        .put("access", access()).put("tools", JSONArray(StoneAutomation.available.map { JSONObject().put("name", it.toolName).put("description", it.description) }))

    fun toolList(): JSONObject = StoneAutomation.available.let { tools ->
        JSONObject().put("total", tools.size).put("access", access()).put("tools", JSONArray(tools.map { it.brief() }))
    }

    private fun access() = when {
        ApiMode.readOnly -> "read-only"
        !ApiMode.writeAccess -> "control (no file editing)"
        else -> "full (file edits need approval on the phone)"
    }

    /** Turns `?percent=40&enabled=true` into typed arguments following the tool's schema. */
    fun arguments(action: StoneAction, query: Map<String, String>): JSONObject {
        val args = JSONObject()
        query.filterKeys { it != "token" }.forEach { (key, raw) ->
            val type = action.params.firstOrNull { it.name == key }?.type
            args.put(key, when (type) {
                "integer" -> raw.toLongOrNull()?.let { if (it in Int.MIN_VALUE..Int.MAX_VALUE) it.toInt() else it } ?: raw
                "number" -> raw.toDoubleOrNull() ?: raw
                "boolean" -> raw.toBooleanStrictOrNull() ?: raw
                else -> raw
            })
        }
        return args
    }
}
