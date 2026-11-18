package com.zirakusa.stone.nativeapp.automation.actions

import com.zirakusa.stone.nativeapp.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import com.zirakusa.stone.nativeapp.automation.auth.AccessMode
import com.zirakusa.stone.nativeapp.automation.auth.AuthContext
import com.zirakusa.stone.nativeapp.automation.auth.currentAuth

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
        "library" to "Search, browse, sort, like/favourites, lyrics, rescan",
        "playlist" to "Playlists and their songs",
        "metadata" to "Tags, lyrics and covers of songs and videos (titles, artists, albums, external cover URLs)",
        "appearance" to "Theme, colours, sizes, home icon",
        "audio" to "Volume ceiling and gain of each audio mode (Niveles de audio)",
        "settings" to "Language, audio mode, gapless/crossfade, hardware acceleration and other switches",
        "stone" to "Help, tool search, solutions dictionary (stone_troubleshoot), recent errors (stone_activity), background jobs")

    private fun StoneAction.brief() = JSONObject().put("name", toolName).put("description", description)
        .put("risk", risk.name.lowercase()).put("readOnly", risk == ActionRisk.READ)
        .put("undoable", risk == ActionRisk.PERSISTENT).put("critical", risk == ActionRisk.CRITICAL)
        .put("parameters", JSONArray(params.map { param ->
            JSONObject().put("name", param.name).put("type", param.type).put("required", param.required)
                .put("description", param.description).apply { param.options?.let { put("options", JSONArray(it)) } }
        }))

    /** Tools whose name, description or parameters contain every word of [query]. */
    fun search(query: String, category: String?, auth: AuthContext = AuthContext.LOCAL): JSONObject {
        val access = auth.access
        val words = query.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotBlank() }
        val matches = StoneAutomation.available(access, auth.blocked).filter { action ->
            val text = (action.name + " " + action.description + " " + action.params.joinToString(" ") { it.name + " " + it.description }).lowercase()
            (category == null || action.name.substringBefore('.') == category) && words.all { it in text }
        }
        return JSONObject().put("categories", JSONObject(categories as Map<*, *>))
            .put("workflow", "1) library_search to get song ids. 2) player_play_song / player_queue_next / playlist_add_songs with those ids. " +
                "3) player_state to confirm. Edits accept dryRun=true and can be reverted with metadata_undo. " +
                "Critical tools may wait up to 60 s for the user to accept on the phone. " +
                "Work in batches of 5-10 songs; bigger batches return a jobId (stone_job_status). " +
                "When a tool fails, stone_troubleshoot code=CODE explains the fix; stone_activity onlyErrors=true lists recent failures.").put("access", access.key)
            .put("total", matches.size).put("tools", JSONArray(matches.map { it.brief() }))
    }

    val action = StoneAction("stone.help", "START HERE if unsure. Lists Stone's tools by category or finds them by intention " +
        "(e.g. query \"equalizer\", \"brightness\", \"queue\"). Returns names, parameters and the usual workflow.",
        ActionRisk.READ, listOf(
            ActionParam("query", "string", "Words to look for in tool names and descriptions. Empty lists everything."),
            ActionParam("category", "string", "Restrict to one category.", options = categories.keys.toList()))) { args ->
        search(args.optString("query"), args.optString("category").ifBlank { null }, currentAuth())
    }
}
