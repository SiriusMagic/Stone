package com.zirakusa.stone.nativeapp.automation

import android.content.Context
import com.zirakusa.stone.core.Song
import com.zirakusa.stone.nativeapp.data.library.AudioTagEditor
import com.zirakusa.stone.nativeapp.data.library.LyricsOverrides
import com.zirakusa.stone.nativeapp.data.library.MediaFileIo
import com.zirakusa.stone.nativeapp.data.library.VideoMetadataEditor
import com.zirakusa.stone.nativeapp.automation.MetadataBatch.context
import com.zirakusa.stone.nativeapp.automation.MetadataBatch.dryRunParam
import com.zirakusa.stone.nativeapp.automation.MetadataBatch.song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Tags, lyrics and covers of songs and videos: read, edit (single and batch), history and undo. */
internal object StoneMetadataActions {
    private val editFields = (MetadataEdits.FIELDS + listOf("coverUrl", "removeCover")).toSet()

    /** Lyrics typed in Stone win over the ones inside the file; an empty override means deleted. */
    fun lyrics(context: Context?, song: Song): String =
        context?.let { LyricsOverrides(it).get(song.id) }
            ?: song.lyrics.ifEmpty { song.syncedLyrics.map { it.text } }.joinToString("\n")

    private val editParams = listOf(
        ActionParam("title", "string", "New title (songs and videos)."),
        ActionParam("artist", "string", "New artist (for videos: author/channel)."),
        ActionParam("album", "string", "New album."), ActionParam("genre", "string", "New genre."),
        ActionParam("description", "string", "Videos only: description."),
        ActionParam("date", "string", "Videos only: date or year."),
        ActionParam("keywords", "string", "Videos only: comma separated keywords."),
        ActionParam("coverUrl", "string", "http(s) URL of a JPEG/PNG/WebP image to use as the cover or video thumbnail."),
        ActionParam("removeCover", "boolean", "true removes the current cover."), dryRunParam)

    /** Update many with one Android dialog at most; used by metadata_update and metadata_update_many. */
    private suspend fun updateItems(tool: String, items: List<BatchItem>, dryRun: Boolean): JSONObject {
        val edits = MetadataEdits(context())
        val locked = if (dryRun) emptySet() else edits.ensureWriteAccess(items.mapNotNull { it.song })
        return MetadataBatch.run(tool, items, dryRun, locked, edits::preview) { item, batch ->
            edits.apply(item.song!!, item.change, batch)
        }
    }

    private suspend fun lyricsItems(tool: String, items: List<BatchItem>, dryRun: Boolean): JSONObject {
        val edits = MetadataEdits(context())
        return MetadataBatch.run(tool, items, dryRun, preview = { song, change ->
            val now = edits.lyricsOf(song)
            JSONObject().put("id", song.id).put("title", song.title).put("hadLyrics", now.isNotBlank())
                .put("newLines", change.getString("lyrics").lines().count { it.isNotBlank() })
        }) { item, batch ->
            JSONObject().put("id", item.songId).put("backupId", edits.setLyrics(item.song!!, item.change.getString("lyrics"), batch))
        }
    }

    fun all(): List<StoneAction> = listOf(
        StoneAction("metadata.get", "Read the tags of a song or video: title, artist, album, genre, lyrics " +
            "(and description, date, keywords for videos), and whether the file can be written.", ActionRisk.READ,
            listOf(ActionParam("songId", "string", "Id returned by library_search (audio or video).", true))) { args ->
            val target = song(args.getString("songId"))
            val context = context()
            withContext(Dispatchers.IO) {
                MetadataEdits(context).current(target).put("id", target.id).put("isVideo", target.isVideo)
                    .put("path", MediaFileIo.displayPath(context, target.uri)).put("lyrics", lyrics(context, target))
                    .put("fileWritable", if (target.isVideo) VideoMetadataEditor(context).read(target.uri).nativeWriteAvailable
                        else AudioTagEditor(context).supports(target.uri))
                    .put("androidConsentNeeded", MediaFileIo.needsWriteConsent(context, target.uri))
            }
        },
        StoneAction("metadata.update", "Edit a song OR video: title, artist, album, genre, cover from an external URL; " +
            "videos also description, date, keywords. Audio is written into the file (MP3, FLAC, M4A), MP4 videos too; " +
            "other videos keep the change in Stone. Omitted fields keep their value. Backed up: metadata_undo reverts it.",
            ActionRisk.PERSISTENT, listOf(ActionParam("songId", "string", "Id returned by library_search.", true)) + editParams) { args ->
            val change = JSONObject(args.toString()).apply { remove("dryRun") }
            val item = BatchItem(args.getString("songId"), song(args.getString("songId")), change)
            val summary = updateItems("metadata_update", listOf(item), args.optBoolean("dryRun"))
            val result = summary.getJSONArray("results").getJSONObject(0)
            if (!result.getBoolean("ok")) result.getJSONObject("error").let {
                throw ActionException(it.getString("message"), it.getString("code"), it.optString("hint").ifBlank { null })
            }
            result.put("dryRun", args.optBoolean("dryRun")).apply { summary.optString("batchId").takeIf { it.isNotBlank() }?.let { put("batchId", it) } }
        },
        StoneAction("metadata.update_many", "Edit many songs/videos in one call (rename videos, fix an album or artist " +
            "everywhere…). Android asks at most once for the whole batch. 'changes' is a JSON array of objects with songId " +
            "plus any field of metadata_update. Returns a summary with progress per item and a batchId for metadata_undo.",
            ActionRisk.PERSISTENT, listOf(ActionParam("changes", "string",
                "JSON array, e.g. [{\"songId\":\"…\",\"title\":\"New name\"},{\"songId\":\"…\",\"album\":\"X\"}]. Max 500.", true),
                dryRunParam)) { args ->
            updateItems("metadata_update_many", MetadataBatch.items(args.getString("changes"), editFields), args.optBoolean("dryRun"))
        },
        StoneAction("metadata.set_lyrics", "Save (or delete with empty text) the lyrics of a song or video in Stone. " +
            "Plain text or LRC with [mm:ss.xx] timestamps for synced lyrics. Backed up: metadata_undo reverts it.",
            ActionRisk.PERSISTENT, listOf(
                ActionParam("songId", "string", "Id returned by library_search.", true),
                ActionParam("lyrics", "string", "Lyrics text; empty string deletes them.", true), dryRunParam)) { args ->
            val item = BatchItem(args.getString("songId"), song(args.getString("songId")), JSONObject().put("lyrics", args.getString("lyrics")))
            lyricsItems("metadata_set_lyrics", listOf(item), args.optBoolean("dryRun"))
        },
        StoneAction("metadata.set_lyrics_many", "Save lyrics for many songs at once. 'changes' is a JSON array of " +
            "{songId, lyrics} (empty lyrics deletes). Backed up as one batch.", ActionRisk.PERSISTENT, listOf(
            ActionParam("changes", "string", "JSON array, e.g. [{\"songId\":\"…\",\"lyrics\":\"[00:12.00] …\"}]. Max 500.", true),
            dryRunParam)) { args ->
            val items = MetadataBatch.items(args.getString("changes"), setOf("lyrics"))
            items.forEach { if (!it.change.has("lyrics")) throw ActionException("Item for ${it.songId} has no lyrics.", "INVALID_PARAM") }
            lyricsItems("metadata_set_lyrics_many", items, args.optBoolean("dryRun"))
        },
        StoneAction("metadata.history", "Recent edits made through the API, newest first, with their backupId and batchId.",
            ActionRisk.READ, listOf(ActionParam("limit", "integer", "Maximum entries (default 30, max 300)."))) { args ->
            val entries = MetadataEdits(context()).journal.list().take(args.optInt("limit", 30).coerceIn(1, 300))
            JSONObject().put("total", entries.size).put("edits", JSONArray(entries.map { entry ->
                JSONObject().put("backupId", entry.id).put("batchId", entry.batch).put("time", entry.timeMs)
                    .put("songId", entry.songId).put("title", entry.title).put("kind", entry.kind).put("before", entry.before)
            }))
        },
        StoneAction("metadata.undo", "Revert edits made through the API: one backupId, a whole batchId, or the last " +
            "batch when nothing is given.", ActionRisk.PERSISTENT, listOf(
            ActionParam("backupId", "string", "One edit from metadata_history."),
            ActionParam("batchId", "string", "Every edit of one tool call."))) { args ->
            val edits = MetadataEdits(context())
            val all = edits.journal.list()
            val targets = when {
                args.has("backupId") -> all.filter { it.id == args.getString("backupId") }
                args.has("batchId") -> all.filter { it.batch == args.getString("batchId") }
                else -> all.firstOrNull()?.let { last -> all.filter { it.batch == last.batch } }.orEmpty()
            }
            if (targets.isEmpty()) throw ActionException("Nothing to undo with those ids.", "NOT_FOUND", "See metadata_history.")
            val undone = withContext(Dispatchers.IO) {
                targets.map { entry ->
                    runCatching { edits.undo(entry) }.fold({ entry.id to null }, { entry.id to (it.message ?: "failed") })
                }
            }
            edits.journal.remove(undone.filter { it.second == null }.map { it.first }.toSet())
            MetadataBatch.host().refreshLibrary()
            JSONObject().put("reverted", undone.count { it.second == null }).put("failed", JSONArray(undone.filter { it.second != null }
                .map { JSONObject().put("backupId", it.first).put("error", it.second) }))
        },
    ) + StoneLibraryEditActions.all()
}
