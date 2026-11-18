package com.zirakusa.stone.nativeapp.automation.actions

import org.json.JSONArray
import org.json.JSONObject

/**
 * Dictionary of solutions for agents, like a skill they can look up: every error code Stone
 * returns and the usual workflows, each with its cause and the steps (and tools) that fix it.
 * Errors point here with "solution": "stone_troubleshoot code=…".
 */
internal object StoneSolutions {
    class Entry(val key: String, val title: String, val cause: String, val steps: List<String>, val tools: List<String>)

    private fun e(key: String, title: String, cause: String, tools: List<String>, vararg steps: String) =
        Entry(key, title, cause, steps.toList(), tools)

    val entries = listOf(
        // ---- error codes ----
        e("RATE_LIMITED", "A web service is limiting requests (HTTP 429)", "lrclib.net or iTunes received too many " +
            "searches in a short time. Stone already paces them and waits, but long bursts still get blocked for a while.",
            listOf("stone_job_status", "stone_job_resume"),
            "Wait retryAfterSeconds before calling again; do not retry in a loop.",
            "Work in batches of 5-10 songs, or pass background=true: Stone waits and resumes by itself.",
            "If a sync call stopped, call it again later with remainingSongIds.",
            "If a job says paused, call stone_job_resume after a few minutes."),
        e("LOW_CONFIDENCE", "No result is clearly the same song", "The best candidate scored below minConfidence " +
            "(0.75): title, artist or duration differ. Generic titles (Mashup, Demons, After Dark) have many versions.",
            listOf("metadata_search_lyrics", "metadata_fetch_lyrics"),
            "Do not lower minConfidence for the whole batch.",
            "Check each candidate: title, artist, album, duration (±5 s) and version (remix, live, slowed, sped up).",
            "If one is right, save exactly that one with metadata_fetch_lyrics choices=[{songId, lrclibId}].",
            "If the title has extras, clean it first (see code TITLE_HAS_EXTRAS)."),
        e("LYRICS_NOT_FOUND", "lrclib.net has no lyrics for this search", "Mixes, mashups, channel uploads and " +
            "videos converted to MP3 are often not indexed, or the title/artist in the file are wrong.",
            listOf("metadata_get", "metadata_rename", "metadata_update", "metadata_search_lyrics", "metadata_set_lyrics"),
            "metadata_get: read the real title and artist.",
            "Clean the title (metadata_rename stripExtras=true dryRun=true) and fix an empty or channel-name artist.",
            "Search again with metadata_search_lyrics: Stone reads the file's current tags, no rescan needed.",
            "Still nothing: the song is not in the free database; ask the user or save lyrics with metadata_set_lyrics."),
        e("TITLE_HAS_EXTRAS", "The title contains more than the song name", "Downloads keep 'Artist - Song (Official " +
            "Video) [Lyrics] | Channel'. Searches then fail or match the wrong song.",
            listOf("metadata_rename", "metadata_update"),
            "metadata_rename songIds=… stripExtras=true dryRun=true shows the clean titles.",
            "Keep remix, live, acoustic and sped up: they are a different recording.",
            "Put the artist in its own field with metadata_update when it was only in the title.",
            "Apply without dryRun; the batchId lets metadata_undo revert it."),
        e("ANDROID_CONSENT", "Android did not allow writing the file", "Files from the device-wide index need the " +
            "user's approval once per batch (Android 11+).", listOf("metadata_prepare_write"),
            "Before a bulk edit, call metadata_prepare_write for all the songs: the user approves once on the phone.",
            "Stone must be open on the phone to show the dialog.",
            "If the user refused, ask them and run metadata_prepare_write again."),
        e("NETWORK", "No internet on the phone", "The phone could not reach lrclib.net or iTunes.", emptyList(),
            "Ask the user to check Wi-Fi or data and try again later."),
        e("STONE_CLOSED", "Stone is not open", "Tools that use the library or ask the user need the app on screen " +
            "or at least its player running.", listOf("player_state"), "Ask the user to open Stone and retry."),
        e("NOT_FOUND", "Unknown id", "The song, playlist, job or backup id does not exist (ids change after files move).",
            listOf("library_search", "stone_job_status", "metadata_history"), "Get fresh ids with library_search."),
        e("UNSUPPORTED_FORMAT", "Tags cannot be written in this file", "Stone writes tags into MP3, FLAC and M4A only.",
            listOf("metadata_set_lyrics"), "Lyrics are still saved in Stone with metadata_set_lyrics."),
        e("USER_DENIED", "The user said no on the phone", "Critical changes (like deleting) always ask on Stone's screen.",
            emptyList(), "Ask the user to approve it on the phone."),
        e("READ_ONLY_TOKEN", "This agent may only read", "Its Gateway Hub packages allow reading only.", emptyList(),
            "The owner gives it a package that allows changes in Gateway Hub, and approves it in Stone."),
        e("TOOL_DISABLED", "This tool is blocked for this agent", "Blocked in Gateway Hub.", emptyList(),
            "Ask the owner to allow it in Gateway Hub."),
        e("TIMEOUT", "The call took too long through the link", "Big batches can exceed the hub's 75 s.",
            listOf("stone_job_status"), "Use 5-10 songs per call or background=true and poll stone_job_status."),
        // ---- workflows ----
        e("WORKFLOW_LYRICS", "Fix songs without lyrics", "Recommended order; process 5-10 songs per call.",
            listOf("library_lyrics", "metadata_rename", "metadata_search_lyrics", "metadata_fetch_lyrics", "metadata_history"),
            "1. Clean titles: metadata_rename stripExtras=true dryRun=true, then apply.",
            "2. Fix artists that are empty or a channel name (metadata_update).",
            "3. metadata_fetch_lyrics on 5-10 songs (or background=true for many). Stone reads the current tags itself.",
            "4. For LOW_CONFIDENCE: compare artist, album, duration and version; save with choices.",
            "5. Keep the batchId of every call; metadata_undo batchId=… reverts it."),
        e("WORKFLOW_GENRES", "Fill in genres", "iTunes suggestions are matched by title, artist and duration.",
            listOf("metadata_suggest_tags_many", "metadata_update_many"),
            "metadata_suggest_tags_many onlyMissing=genre (10 per call, or background=true).",
            "With apply=true only matches at or above minConfidence (0.8) are written; the rest come back to review.",
            "Review the rest one by one or set the genre by hand with metadata_update_many."),
        e("WORKFLOW_BULK", "Large libraries and two agents at once", "Free services and the link have limits.",
            listOf("stone_job_status", "stone_activity"),
            "Batches of 5-10, or background=true and poll stone_job_status every 10-20 s.",
            "Two agents share the same pacing, so they do not double the request rate.",
            "stone_activity onlyErrors=true shows what failed and with which code."),
    )

    private val byKey = entries.associateBy { it.key }
    fun has(code: String) = code in byKey

    fun json(entry: Entry): JSONObject = JSONObject().put("key", entry.key).put("title", entry.title)
        .put("cause", entry.cause).put("steps", JSONArray(entry.steps)).put("tools", JSONArray(entry.tools))

    fun lookup(code: String?, query: String?): JSONObject {
        code?.uppercase()?.let { key -> byKey[key]?.let { return json(it) } }
        val words = query.orEmpty().lowercase().split(Regex("[^\\p{L}\\p{N}_]+")).filter { it.length > 2 }
        val found = if (words.isEmpty()) entries else entries.filter { entry ->
            val text = (entry.key + " " + entry.title + " " + entry.cause + " " + entry.steps.joinToString(" ")).lowercase()
            words.any { it in text }
        }
        return JSONObject().put("total", found.size).put("entries", JSONArray(found.map { json(it) }))
    }
}
