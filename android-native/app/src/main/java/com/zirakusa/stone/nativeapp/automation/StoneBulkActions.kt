package com.zirakusa.stone.nativeapp.automation

import com.zirakusa.stone.nativeapp.automation.MetadataBatch.context
import com.zirakusa.stone.nativeapp.automation.MetadataBatch.dryRunParam
import com.zirakusa.stone.nativeapp.data.library.MediaFileIo
import com.zirakusa.stone.nativeapp.data.library.TagSuggestions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Many songs at once: tag suggestions from iTunes and Android's write permission ahead of a bulk edit. */
internal object StoneBulkActions {
    private val tagFields = setOf("genre", "album", "artist", "title")

    fun all(): List<StoneAction> = listOf(
        StoneAction("metadata.suggest_tags_many", "Look many songs up in iTunes (paced to avoid HTTP 429) and fill in " +
            "tags. dryRun=true only returns the best suggestion per song with its confidence. Without dryRun it writes " +
            "'fields' (default genre) only where confidence >= minConfidence; the others come back with needsReview=true. " +
            "Up to ${StoneJobs.SYNC_LIMIT} songs answer directly; more run as a background job (jobId). Backed up as one batch.",
            ActionRisk.PERSISTENT, listOf(
                ActionParam("songIds", "string", "Comma separated ids. Omit to use kind/query."),
                StoneLibraryEditActions.kindParam, StoneLibraryEditActions.queryParam,
                ActionParam("onlyMissing", "string", "Only songs where this field is empty (default genre).",
                    options = listOf("genre", "album", "artist", "none")),
                ActionParam("fields", "string", "Comma separated fields to write: genre, album, artist, title (default genre)."),
                ActionParam("minConfidence", "number", "0-1, default 0.8. Below it nothing is written for that song."),
                ActionParam("limit", "integer", "Maximum songs (default ${StoneJobs.SYNC_LIMIT}, max 500)."),
                dryRunParam, MetadataBatch.backgroundParam)) { args ->
            val fields = args.optString("fields", "genre").split(',').map { it.trim() }.filter { it.isNotEmpty() }
            fields.firstOrNull { it !in tagFields }?.let {
                throw ActionException("Unknown field '$it'.", "INVALID_PARAM", "Use: ${tagFields.joinToString()}")
            }
            val minimum = args.optDouble("minConfidence", 0.8).coerceIn(0.0, 1.0)
            val missing = args.optString("onlyMissing", "genre")
            val songs = StoneLibraryEditActions.targets(args, "audio").filter { song ->
                args.has("songIds") || when (missing) {
                    "genre" -> song.genre.isBlank(); "album" -> song.album.isBlank(); "artist" -> song.artist.isBlank(); else -> true
                }
            }.take(args.optInt("limit", StoneJobs.SYNC_LIMIT).coerceIn(1, 500))
            if (songs.isEmpty()) throw ActionException("No songs match those filters.", "NOT_FOUND")
            val edits = MetadataEdits(context())
            val dryRun = args.optBoolean("dryRun")
            val locked = if (dryRun) emptySet() else edits.ensureWriteAccess(songs)
            fun suggest(item: BatchItem, batch: String?): JSONObject {
                val song = MetadataBatch.fresh(item.song!!)
                val best = TagSuggestions.find(song.title, song.artist, song.album, song.durationMs).firstOrNull()
                val result = JSONObject().put("id", song.id).put("title", song.title).put("artist", song.artist)
                if (best == null) return result.put("applied", false).put("needsReview", true).put("reason", "NO_MATCH")
                val values = mapOf("genre" to best.genre, "album" to best.album, "artist" to best.artist, "title" to best.title)
                result.put("suggestion", JSONObject().put("title", best.title).put("artist", best.artist).put("album", best.album)
                    .put("genre", best.genre).put("year", best.year).put("durationSeconds", best.durationSeconds.toInt())
                    .put("confidence", Math.round(best.confidence * 100) / 100.0))
                if (batch == null) return result
                if (best.confidence < minimum) return result.put("applied", false).put("needsReview", true).put("reason", "LOW_CONFIDENCE")
                val change = JSONObject().apply { fields.forEach { field -> values[field]?.takeIf { it.isNotBlank() }?.let { put(field, it) } } }
                if (change.length() == 0) return result.put("applied", false).put("reason", "EMPTY_SUGGESTION")
                edits.apply(item.song, change, batch)
                return result.put("applied", true).put("written", change)
            }
            MetadataBatch.runOrStart("metadata_suggest_tags_many", songs.map { BatchItem(it.id, it, JSONObject()) }, args, locked,
                preview = { song, _ -> suggest(BatchItem(song.id, song, JSONObject()), null) }) { item, batch -> suggest(item, batch) }
        },
        StoneAction("metadata.prepare_write", "Ask Android ONCE for permission to write many files before a bulk edit " +
            "(tags, lyrics inside the file). Without songIds it takes every song (kind/query filter). Stone must be open; " +
            "the user approves on the phone. Returns how many files are writable now.", ActionRisk.REVERSIBLE, listOf(
            ActionParam("songIds", "string", "Comma separated ids. Omit to use kind/query."),
            StoneLibraryEditActions.kindParam, StoneLibraryEditActions.queryParam)) { args ->
            val context = context()
            val songs = StoneLibraryEditActions.targets(args, "audio")
            val needed = withContext(Dispatchers.IO) { songs.filter { MediaFileIo.needsWriteConsent(context, it.uri) } }
            val locked = if (needed.isEmpty()) emptySet() else MetadataEdits(context).ensureWriteAccess(needed)
            JSONObject().put("songs", songs.size).put("neededPermission", needed.size).put("granted", needed.size - locked.size)
                .put("stillLocked", locked.size).apply {
                    if (locked.isNotEmpty()) put("lockedSongIds", JSONArray(locked.take(50)))
                        .put("hint", "The user did not approve. " + MetadataEdits.ANDROID_HINT)
                }
        },
    )
}
