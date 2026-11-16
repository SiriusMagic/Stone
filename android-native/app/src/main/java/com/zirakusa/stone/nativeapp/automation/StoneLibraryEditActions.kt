package com.zirakusa.stone.nativeapp.automation

import com.zirakusa.stone.core.Song
import com.zirakusa.stone.nativeapp.automation.MetadataBatch.context
import com.zirakusa.stone.nativeapp.automation.MetadataBatch.dryRunParam
import com.zirakusa.stone.nativeapp.automation.MetadataBatch.host
import com.zirakusa.stone.nativeapp.data.library.LrcLibClient
import com.zirakusa.stone.nativeapp.data.library.LyricsCandidate
import com.zirakusa.stone.nativeapp.data.library.LyricsMatcher
import com.zirakusa.stone.nativeapp.data.library.MediaFileIo
import com.zirakusa.stone.nativeapp.data.library.RenameSource
import com.zirakusa.stone.nativeapp.data.library.RenameTemplate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
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
        StoneAction("metadata.search_lyrics", "Look up lyrics candidates on lrclib.net for one song, each with a confidence " +
            "(0-1) that it is the same song (title, artist and duration). Save one with metadata_fetch_lyrics 'choices'.",
            ActionRisk.READ, listOf(ActionParam("songId", "string", "Id returned by library_search.", true))) { args ->
            val song = MetadataBatch.song(args.getString("songId"))
            val query = LyricsMatcher.query(song.title, song.artist, song.album, song.durationMs)
            val found = withContext(Dispatchers.IO) { LrcLibClient.candidates(query) }
            JSONObject().put("songId", song.id).put("query", JSONObject().put("title", query.title).put("artist", query.artist))
                .put("candidates", JSONArray(found.map { it.brief().put("preview", it.text.lineSequence()
                    .map { line -> line.substringAfter(']').trim() }.filter { line -> line.isNotBlank() }.take(2).joinToString(" / ")) }))
        },
        StoneAction("metadata.fetch_lyrics", "Find lyrics on lrclib.net for many songs, match each result to the right song " +
            "(title, artist, duration → confidence) and save the confident ones in one batch. Results below minConfidence " +
            "are not saved: they come back as LOW_CONFIDENCE with candidates; save the right ones with 'choices'. " +
            "Without songIds it takes songs that have no lyrics yet. Backed up: metadata_undo reverts it.",
            ActionRisk.PERSISTENT, listOf(
                ActionParam("songIds", "string", "Comma separated ids. Omit to use kind/query."), kindParam, queryParam,
                ActionParam("choices", "string", "JSON array [{\"songId\":\"…\",\"lrclibId\":123}] from metadata_search_lyrics; saves exactly those."),
                ActionParam("minConfidence", "number", "0-1, default 0.75. Lower saves more but risks wrong lyrics."),
                ActionParam("overwrite", "boolean", "Replace lyrics that already exist (default false)."),
                ActionParam("limit", "integer", "Maximum songs per call (default 25, max 100; each takes about a second)."),
                dryRunParam)) { args ->
            val edits = MetadataEdits(context())
            val minimum = args.optDouble("minConfidence", LyricsMatcher.CONFIDENT).coerceIn(0.0, 1.0)
            val chosen = args.optString("choices").takeIf { it.isNotBlank() }?.let { raw ->
                MetadataBatch.items(raw, setOf("lrclibId")).onEach {
                    if (it.change.optLong("lrclibId", 0) <= 0) throw ActionException("Each choice needs lrclibId.", "INVALID_PARAM")
                }
            }
            val overwrite = args.optBoolean("overwrite", false) || chosen != null || args.has("songIds")
            val items = chosen ?: targets(args, "audio").filter { overwrite || edits.lyricsOf(it).isBlank() }
                .take(args.optInt("limit", 25).coerceIn(1, 100)).map { BatchItem(it.id, it, JSONObject()) }
            if (items.isEmpty()) throw ActionException("No songs need lyrics with those filters.", "NOT_FOUND")
            val picks = HashMap<String, LyricsCandidate>()
            fun match(song: Song, change: JSONObject): JSONObject {
                val pick = picks[song.id] ?: if (change.has("lrclibId")) {
                    LrcLibClient.byId(change.getLong("lrclibId")) ?: throw ActionException("lrclib id not found.", "NOT_FOUND")
                } else {
                    val all = LrcLibClient.candidates(LyricsMatcher.query(song.title, song.artist, song.album, song.durationMs))
                    all.firstOrNull { it.confidence >= minimum } ?: throw ActionException(
                        if (all.isEmpty()) "No lyrics found on lrclib.net." else "No result is clearly this song.",
                        if (all.isEmpty()) "LYRICS_NOT_FOUND" else "LOW_CONFIDENCE",
                        if (all.isEmpty()) null else "Candidates: " + all.take(3).joinToString { c ->
                            "lrclibId ${c.id} '${c.title}' by ${c.artist} (${"%.2f".format(c.confidence)})" })
                }
                picks[song.id] = pick
                return JSONObject().put("id", song.id).put("title", song.title).put("match", pick.brief())
            }
            MetadataBatch.run("metadata_fetch_lyrics", items, args.optBoolean("dryRun"), preview = ::match) { item, batch ->
                val song = item.song!!
                match(song, item.change).put("backupId", edits.setLyrics(song, picks.getValue(song.id).text, batch))
            }
        },
        StoneAction("metadata.suggest_tags", "Look a song up in the public iTunes catalogue and return possible tags " +
            "(title, artist, album, genre, year, coverUrl) with a confidence 0-1. Apply one with metadata_update.",
            ActionRisk.READ, listOf(ActionParam("songId", "string", "Id returned by library_search.", true))) { args ->
            val song = MetadataBatch.song(args.getString("songId"))
            val found = withContext(Dispatchers.IO) {
                com.zirakusa.stone.nativeapp.data.library.TagSuggestions.find(song.title, song.artist, song.album, song.durationMs)
            }
            JSONObject().put("songId", song.id).put("candidates", JSONArray(found.map {
                JSONObject().put("title", it.title).put("artist", it.artist).put("album", it.album).put("genre", it.genre)
                    .put("year", it.year).put("coverUrl", it.coverUrl).put("confidence", Math.round(it.confidence * 100) / 100.0)
            }))
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

    private fun LyricsCandidate.brief() = JSONObject().put("lrclibId", id).put("title", title).put("artist", artist)
        .put("album", album).put("durationSeconds", durationSeconds).put("synced", synced)
        .put("confidence", Math.round(confidence * 100) / 100.0)
}
