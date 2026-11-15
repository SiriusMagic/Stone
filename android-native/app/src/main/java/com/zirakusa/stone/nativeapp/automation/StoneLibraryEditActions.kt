package com.zirakusa.stone.nativeapp.automation

import com.zirakusa.stone.core.Song
import com.zirakusa.stone.nativeapp.automation.MetadataBatch.context
import com.zirakusa.stone.nativeapp.automation.MetadataBatch.dryRunParam
import com.zirakusa.stone.nativeapp.automation.MetadataBatch.host
import com.zirakusa.stone.nativeapp.data.library.LrcLibClient
import com.zirakusa.stone.nativeapp.data.library.MediaFileIo
import com.zirakusa.stone.nativeapp.data.library.RenameSource
import com.zirakusa.stone.nativeapp.data.library.RenameTemplate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Bulk tools: import lyrics from lrclib.net and rename many songs/videos with a template. */
internal object StoneLibraryEditActions {
    private fun targets(args: JSONObject, defaultKind: String): List<Song> {
        if (args.has("songIds")) return MetadataBatch.ids(args.getString("songIds"))
        val query = args.optString("query").lowercase()
        return host().songs.filter { song ->
            when (args.optString("kind", defaultKind)) { "video" -> song.isVideo; "audio" -> !song.isVideo; else -> true } &&
                (query.isEmpty() || query in song.title.lowercase() || query in song.artist.lowercase() || query in song.folder.lowercase())
        }
    }

    private val kindParam = ActionParam("kind", "string", "Which items when songIds is not given.", options = listOf("audio", "video", "all"))
    private val queryParam = ActionParam("query", "string", "Only items whose title, artist or folder contains this text.")

    fun all(): List<StoneAction> = listOf(
        StoneAction("metadata.fetch_lyrics", "Download lyrics from lrclib.net (free, synced LRC when available) and save them " +
            "in Stone. Without songIds it takes songs that have no lyrics yet. Backed up: metadata_undo reverts it.",
            ActionRisk.PERSISTENT, listOf(
                ActionParam("songIds", "string", "Comma separated ids. Omit to use kind/query."), kindParam, queryParam,
                ActionParam("overwrite", "boolean", "Replace lyrics that already exist (default false)."),
                ActionParam("limit", "integer", "Maximum songs per call (default 25, max 100; each takes about a second)."),
                dryRunParam)) { args ->
            val context = context()
            val edits = MetadataEdits(context)
            val overwrite = args.optBoolean("overwrite", false)
            val songs = targets(args, "audio").filter { overwrite || args.has("songIds") || edits.lyricsOf(it).isBlank() }
                .take(args.optInt("limit", 25).coerceIn(1, 100))
            if (songs.isEmpty()) throw ActionException("No songs need lyrics with those filters.", "NOT_FOUND")
            val found = HashMap<String, String>()
            val items = songs.map { BatchItem(it.id, it, JSONObject()) }
            MetadataBatch.run("metadata_fetch_lyrics", items, args.optBoolean("dryRun"), preview = { song, _ ->
                lookup(song, found).put("id", song.id).put("title", song.title)
            }) { item, batch ->
                val song = item.song!!
                if (!overwrite && !args.has("songIds") && edits.lyricsOf(song).isNotBlank())
                    throw ActionException("Already has lyrics.", "SKIPPED")
                val result = lookup(song, found)
                val text = found[song.id] ?: throw ActionException("No lyrics found on lrclib.net.", "LYRICS_NOT_FOUND")
                result.put("id", song.id).put("title", song.title).put("backupId", edits.setLyrics(song, text, batch))
            }
        },
        StoneAction("metadata.rename", "Rename many songs or videos at once with a template and/or clean-up. Template " +
            "placeholders: {title} {artist} {album} {file} (file name) {date} {n} {n:2} (counter). 'clean' removes technical " +
            "noise (underscores, extensions, [1080p], x264, YouTube ids…). Optional regex find/replace. Use dryRun first.",
            ActionRisk.PERSISTENT, listOf(
                ActionParam("songIds", "string", "Comma separated ids. Omit to use kind/query."), kindParam, queryParam,
                ActionParam("field", "string", "Field to rename (default title).", options = listOf("title", "artist", "album")),
                ActionParam("template", "string", "Default \"{title}\". Example: \"{artist} - {title}\" or \"Episodio {n:2}\"."),
                ActionParam("clean", "boolean", "Remove technical noise from the result (default true)."),
                ActionParam("find", "string", "Regular expression to replace in the result."),
                ActionParam("replace", "string", "Replacement for 'find' ($1 for groups). Default empty."),
                ActionParam("startIndex", "integer", "First value of {n} (default 1)."),
                ActionParam("limit", "integer", "Maximum items (default 200, max 500)."), dryRunParam)) { args ->
            val context = context()
            val edits = MetadataEdits(context)
            val field = args.optString("field", "title")
            val template = args.optString("template", "{title}")
            val find = args.optString("find").takeIf { it.isNotEmpty() }?.let {
                runCatching { Regex(it) }.getOrElse { throw ActionException("'find' is not a valid regular expression.", "INVALID_PARAM") }
            }
            val songs = targets(args, "video").take(args.optInt("limit", 200).coerceIn(1, 500))
            if (songs.isEmpty()) throw ActionException("No items match those filters.", "NOT_FOUND")
            val start = args.optInt("startIndex", 1)
            val items = withContext(Dispatchers.IO) {
                songs.mapIndexed { index, song ->
                    val now = edits.current(song)
                    val file = MediaFileIo.displayPath(context, song.uri).substringAfterLast('/').substringBeforeLast('.')
                    var name = RenameTemplate.render(template, RenameSource(now.optString("title").ifBlank { song.title },
                        now.optString("artist").ifBlank { song.artist }, now.optString("album").ifBlank { song.album },
                        file, now.optString("date"), start + index))
                    if (args.optBoolean("clean", true)) name = RenameTemplate.clean(name)
                    find?.let { name = it.replace(name, args.optString("replace")).trim() }
                    BatchItem(song.id, song, JSONObject().put(field, name.ifBlank { song.title }))
                }
            }
            val locked = if (args.optBoolean("dryRun")) emptySet() else edits.ensureWriteAccess(songs)
            MetadataBatch.run("metadata_rename", items, args.optBoolean("dryRun"), locked, edits::preview) { item, batch ->
                edits.apply(item.song!!, item.change, batch)
            }
        },
    )

    /** Looks lyrics up once per song (dry run and real run share the cache). */
    private fun lookup(song: Song, cache: MutableMap<String, String>): JSONObject {
        val found = LrcLibClient.find(song.title, song.artist, song.album, song.durationMs)
            ?: return JSONObject().put("found", false)
        cache[song.id] = found.text
        return JSONObject().put("found", true).put("synced", found.synced).put("source", found.source)
            .put("preview", found.text.lineSequence().filter { it.isNotBlank() }.take(2).joinToString(" / "))
    }
}
