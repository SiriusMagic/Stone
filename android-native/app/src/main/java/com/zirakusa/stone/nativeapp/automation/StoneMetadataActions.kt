package com.zirakusa.stone.nativeapp.automation

import android.content.Context
import com.zirakusa.stone.core.Song
import com.zirakusa.stone.nativeapp.data.library.AudioTagEditor
import com.zirakusa.stone.nativeapp.data.library.AudioTags
import com.zirakusa.stone.nativeapp.data.library.CoverDownloader
import com.zirakusa.stone.nativeapp.data.library.LyricsOverrides
import com.zirakusa.stone.nativeapp.data.library.MediaFileIo
import com.zirakusa.stone.nativeapp.data.library.VideoMetadataEditor
import com.zirakusa.stone.nativeapp.playback.StonePlaybackService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Tags, lyrics and covers of songs and videos. Every write is approved on the phone (PERSISTENT). */
internal object StoneMetadataActions {
    private val audioFields = listOf("title", "artist", "album", "genre")
    private val videoFields = listOf("description", "date", "keywords")
    private val editFields = audioFields + videoFields + listOf("coverUrl", "removeCover")

    private fun host() = StoneAutomation.requireHost()
    private fun context(): Context = StonePlaybackService.instance?.applicationContext
        ?: throw ActionException("Playback service not running; open Stone.")
    private fun song(id: String): Song = host().songs.firstOrNull { it.id == id }
        ?: throw ActionException("No song or video with id '$id'. Use library_search to get valid ids.")

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
        ActionParam("removeCover", "boolean", "true removes the current cover."))

    private fun describe(args: JSONObject) = editFields.filter(args::has).joinToString { field ->
        if (field == "removeCover") "quitar portada" else "$field → ${args.get(field).toString().take(60)}"
    }

    /** Applies one change set to one file; returns a short result for the agent. */
    private suspend fun apply(context: Context, target: Song, args: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        val cover = args.optString("coverUrl").takeIf { it.isNotBlank() }?.let(CoverDownloader::download)
        val removeCover = args.optBoolean("removeCover", false) && cover == null
        val result = JSONObject().put("id", target.id).put("isVideo", target.isVideo)
        if (target.isVideo) {
            val editor = VideoMetadataEditor(context)
            val current = editor.read(target.uri).draft
            val thumbnail = cover?.let { editor.saveVideoFrame(target.uri, CoverDownloader.decode(it)) }
            val draft = current.copy(
                title = args.optString("title", current.title), author = args.optString("artist", current.author),
                album = args.optString("album", current.album), genre = args.optString("genre", current.genre),
                description = args.optString("description", current.description), date = args.optString("date", current.date),
                keywords = args.optString("keywords", current.keywords),
                thumbnailUri = thumbnail ?: if (removeCover) "" else current.thumbnailUri,
                thumbnailChanged = thumbnail != null || removeCover)
            val saved = editor.save(target.uri, draft)
            result.put("writtenToFile", saved.nativeFileWritten).put("savedInStone", saved.internalMetadataSaved)
            saved.nativeFailureMessage?.let { result.put("note", "Kept in Stone only: $it") }
        } else {
            if (videoFields.any(args::has)) throw ActionException("description, date and keywords are only for videos.")
            if (MediaFileIo.needsWriteConsent(context, target.uri))
                throw ActionException("Android requires the user to edit '${target.title}' once from Stone's tag editor (system permission).")
            val editor = AudioTagEditor(context)
            if (!editor.supports(target.uri)) throw ActionException("'${target.title}' cannot be tagged (MP3, FLAC, M4A only).")
            val current = editor.read(target.uri)
            editor.write(target.uri, AudioTags(
                title = args.optString("title", current.title), artist = args.optString("artist", current.artist),
                album = args.optString("album", current.album), genre = args.optString("genre", current.genre),
                cover = cover, removeCover = removeCover))
            result.put("writtenToFile", true)
        }
        result
    }

    private fun changesOf(args: JSONObject): List<JSONObject> {
        val list = runCatching { JSONArray(args.getString("changes")) }.getOrNull()
            ?: throw ActionException("'changes' must be a JSON array like [{\"songId\":\"…\",\"title\":\"…\"}]")
        if (list.length() !in 1..200) throw ActionException("'changes' must contain between 1 and 200 items.")
        return (0 until list.length()).map { index ->
            val change = list.optJSONObject(index) ?: throw ActionException("Item $index of 'changes' is not an object.")
            if (!change.has("songId")) throw ActionException("Item $index of 'changes' has no songId.")
            change.keys().forEach { key ->
                if (key != "songId" && key !in editFields) throw ActionException("Unknown field '$key' in item $index.")
            }
            change
        }
    }

    fun all(): List<StoneAction> = listOf(
        StoneAction("metadata.get", "Read the tags of a song or video: title, artist, album, genre, lyrics " +
            "(and description, date, keywords for videos).", ActionRisk.READ,
            listOf(ActionParam("songId", "string", "Id returned by library_search (audio or video).", true))) { args ->
            val target = song(args.getString("songId"))
            val context = context()
            withContext(Dispatchers.IO) {
                val base = JSONObject().put("id", target.id).put("isVideo", target.isVideo)
                    .put("path", MediaFileIo.displayPath(context, target.uri)).put("lyrics", lyrics(context, target))
                if (target.isVideo) {
                    val document = VideoMetadataEditor(context).read(target.uri)
                    val draft = document.draft
                    base.put("title", draft.title).put("artist", draft.author).put("album", draft.album).put("genre", draft.genre)
                        .put("description", draft.description).put("date", draft.date).put("keywords", draft.keywords)
                        .put("hasCustomThumbnail", draft.thumbnailUri.isNotBlank())
                        .put("fileWritable", document.nativeWriteAvailable)
                } else {
                    val editor = AudioTagEditor(context)
                    val tags = editor.read(target.uri)
                    base.put("title", tags.title).put("artist", tags.artist).put("album", tags.album).put("genre", tags.genre)
                        .put("fileWritable", editor.supports(target.uri))
                }
            }
        },
        StoneAction("metadata.update", "Edit a song OR video: title, artist, album, genre, cover from an external URL; " +
            "videos also description, date, keywords. Audio is written into the original file (MP3, FLAC, M4A); " +
            "MP4 videos too, other videos keep the change in Stone. Omitted fields keep their value. Needs approval on the phone.",
            ActionRisk.PERSISTENT, listOf(ActionParam("songId", "string", "Id returned by library_search.", true)) + editParams,
            summary = { args -> "Editar «${runCatching { song(args.getString("songId")).title }.getOrDefault("?")}»: ${describe(args)}" }) { args ->
            val target = song(args.getString("songId"))
            val result = apply(context(), target, args)
            host().refreshLibrary()
            result.put("ok", true)
        },
        StoneAction("metadata.update_many", "Edit many songs/videos with ONE approval on the phone (rename videos, fix an " +
            "album or artist name everywhere…). 'changes' is a JSON array of objects with songId plus any field of " +
            "metadata_update (title, artist, album, genre, description, date, keywords, coverUrl, removeCover).",
            ActionRisk.PERSISTENT, listOf(ActionParam("changes", "string",
                "JSON array, e.g. [{\"songId\":\"…\",\"title\":\"New name\"},{\"songId\":\"…\",\"album\":\"X\"}]. Max 200.", true)),
            summary = { args ->
                val changes = runCatching { changesOf(args) }.getOrDefault(emptyList())
                val songs = host().songs.associateBy { it.id }
                "Editar ${changes.size} elemento(s):\n" + changes.take(8).joinToString("\n") { change ->
                    "• ${songs[change.optString("songId")]?.title ?: "?"}: ${describe(change)}"
                } + if (changes.size > 8) "\n…" else ""
            }) { args ->
            val context = context()
            val results = changesOf(args).map { change ->
                try {
                    apply(context, song(change.getString("songId")), change).put("ok", true)
                } catch (failure: Exception) {
                    JSONObject().put("id", change.optString("songId")).put("ok", false).put("error", failure.message ?: "failed")
                }
            }
            host().refreshLibrary()
            JSONObject().put("updated", results.count { it.getBoolean("ok") }).put("failed", results.count { !it.getBoolean("ok") })
                .put("results", JSONArray(results))
        },
        StoneAction("metadata.set_lyrics", "Save (or delete with empty text) the lyrics of a song or video in Stone. " +
            "Plain text or LRC with [mm:ss.xx] timestamps for synced lyrics. Needs approval on the phone.",
            ActionRisk.PERSISTENT, listOf(
                ActionParam("songId", "string", "Id returned by library_search.", true),
                ActionParam("lyrics", "string", "Lyrics text; empty string deletes them.", true)),
            summary = { args ->
                val text = args.optString("lyrics")
                val name = runCatching { song(args.getString("songId")).title }.getOrDefault("?")
                if (text.isBlank()) "Borrar la letra de «$name»" else "Guardar letra de «$name»: ${text.lineSequence().first().take(60)}…"
            }) { args ->
            val target = song(args.getString("songId"))
            val overrides = LyricsOverrides(context())
            val text = args.getString("lyrics")
            withContext(Dispatchers.IO) { if (text.isBlank()) overrides.clear(target.id) else overrides.put(target.id, text.trim()) }
            JSONObject().put("ok", true).put("message", if (text.isBlank()) "Lyrics deleted" else "Lyrics saved")
        },
    )
}
