package com.zirakusa.stone.nativeapp.automation.actions

import android.content.Context
import android.media.MediaMetadataRetriever
import androidx.core.net.toUri
import com.zirakusa.stone.core.Song
import com.zirakusa.stone.nativeapp.data.library.tags.AudioTagEditor
import com.zirakusa.stone.nativeapp.data.library.lyrics.EmbeddedLyricsReader
import com.zirakusa.stone.nativeapp.data.library.tags.AudioTags
import com.zirakusa.stone.nativeapp.data.library.artwork.CoverDownloader
import com.zirakusa.stone.nativeapp.data.library.tags.EditBackup
import com.zirakusa.stone.nativeapp.data.library.tags.EditJournal
import com.zirakusa.stone.nativeapp.data.library.lyrics.LyricsOverrides
import com.zirakusa.stone.nativeapp.data.library.tags.MediaFileIo
import com.zirakusa.stone.nativeapp.data.library.tags.VideoMetadataEditor
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Tag, lyrics and cover edits made by agents. Every edit stores what it replaced in the
 * [EditJournal] first, so metadata_undo can put it back. Runs on a background thread.
 */
internal class MetadataEdits(private val context: Context) {
    val journal = EditJournal(context)
    private val lyrics = LyricsOverrides(context)

    /** Current values of the editable fields. */
    fun current(song: Song): JSONObject = if (song.isVideo) {
        val draft = VideoMetadataEditor(context).read(song.uri).draft
        JSONObject().put("title", draft.title).put("artist", draft.author).put("album", draft.album).put("genre", draft.genre)
            .put("description", draft.description).put("date", draft.date).put("keywords", draft.keywords)
    } else {
        val tags = AudioTagEditor(context).read(song.uri)
        JSONObject().put("title", tags.title).put("artist", tags.artist).put("album", tags.album).put("genre", tags.genre)
    }

    /** What [change] would do, without touching anything (dryRun). */
    fun preview(song: Song, change: JSONObject): JSONObject {
        val now = current(song)
        val changes = JSONObject()
        FIELDS.filter(change::has).forEach { field ->
            val target = change.getString(field)
            if (now.optString(field) != target) changes.put(field, JSONObject().put("from", now.optString(field)).put("to", target))
        }
        if (change.has("coverUrl")) changes.put("cover", JSONObject().put("to", change.getString("coverUrl")))
        if (change.optBoolean("removeCover")) changes.put("cover", JSONObject().put("to", "removed"))
        val problem = writeProblem(song)
        return JSONObject().put("id", song.id).put("title", song.title).put("isVideo", song.isVideo).put("changes", changes)
            .apply { problem?.let { put("warning", JSONObject().put("code", it.first).put("message", it.second)) } }
    }

    /** Asks Android once for every file of the batch that needs it; returns the ids that stay locked. */
    suspend fun ensureWriteAccess(songs: List<Song>): Set<String> {
        val locked = songs.filter { MediaFileIo.needsWriteConsent(context, it.uri) }
        if (locked.isEmpty()) return emptySet()
        val sender = MediaFileIo.writeConsentRequest(context, locked.map { it.uri }) ?: return locked.ids()
        return if (StoneAutomation.requestWriteAccess(locked.size, sender)) emptySet() else locked.ids()
    }

    /** Applies one change set, backing up what it replaces. */
    fun apply(song: Song, change: JSONObject, batch: String): JSONObject {
        val cover = change.optString("coverUrl").takeIf { it.isNotBlank() }?.let(CoverDownloader::download)
        val removeCover = change.optBoolean("removeCover", false) && cover == null
        val coverChanged = cover != null || removeCover
        val id = UUID.randomUUID().toString().take(12)
        val before = current(song)
        return if (song.isVideo) {
            val editor = VideoMetadataEditor(context)
            val draft = editor.read(song.uri).draft
            before.put("thumbnailUri", draft.thumbnailUri)
            val previousCover = if (coverChanged && draft.thumbnailUri.isNotBlank())
                runCatching { context.contentResolver.openInputStream(draft.thumbnailUri.toUri())?.use { it.readBytes() } }.getOrNull()
                else null
            val thumbnail = cover?.let { editor.saveVideoFrame(song.uri, CoverDownloader.decode(it)) }
            val saved = editor.save(song.uri, draft.copy(
                title = change.optString("title", draft.title), author = change.optString("artist", draft.author),
                album = change.optString("album", draft.album), genre = change.optString("genre", draft.genre),
                description = change.optString("description", draft.description), date = change.optString("date", draft.date),
                keywords = change.optString("keywords", draft.keywords),
                thumbnailUri = thumbnail ?: if (removeCover) "" else draft.thumbnailUri, thumbnailChanged = coverChanged))
            journal.add(EditBackup(id, System.currentTimeMillis(), batch, song.id, song.uri, song.title, "video", before,
                coverChanged, previousCover?.let { journal.saveCover(id, it) }))
            JSONObject().put("id", song.id).put("backupId", id).put("writtenToFile", saved.nativeFileWritten)
                .apply { saved.nativeFailureMessage?.let { put("note", "Kept in Stone only: $it") } }
        } else {
            writeProblem(song)?.let { (code, message) ->
                throw ActionException(message, code, if (code == "ANDROID_CONSENT") ANDROID_HINT else null)
            }
            if (FIELDS.drop(4).any(change::has))
                throw ActionException("description, date and keywords are only for videos.", "INVALID_PARAM")
            val editor = AudioTagEditor(context)
            val previousCover = if (coverChanged) embeddedCover(song.uri) else null
            editor.write(song.uri, AudioTags(
                title = change.optString("title", before.optString("title")), artist = change.optString("artist", before.optString("artist")),
                album = change.optString("album", before.optString("album")), genre = change.optString("genre", before.optString("genre")),
                cover = cover, removeCover = removeCover))
            journal.add(EditBackup(id, System.currentTimeMillis(), batch, song.id, song.uri, song.title, "audio", before,
                coverChanged, previousCover?.let { journal.saveCover(id, it) }))
            JSONObject().put("id", song.id).put("backupId", id).put("writtenToFile", true)
        }
    }

    fun lyricsOf(song: Song): String = lyrics.get(song.id)
        ?: song.lyrics.ifEmpty { song.syncedLyrics.map { it.text } }.joinToString("\n")

    /**
     * Saves lyrics in Stone and, when the file allows it (MP3, FLAC, M4A already writable), also
     * inside the file so they travel with it. Returns the backup id and whether the file got them.
     */
    fun setLyrics(song: Song, text: String, batch: String): Pair<String, Boolean> {
        val id = UUID.randomUUID().toString().take(12)
        val before = JSONObject().apply { lyrics.get(song.id)?.let { put("lyrics", it) } }
        if (text.isBlank()) lyrics.clear(song.id) else lyrics.put(song.id, text.trim())
        val canEmbed = !song.isVideo && AudioTagEditor(context).supports(song.uri) &&
            !MediaFileIo.needsWriteConsent(context, song.uri)
        val embedded = canEmbed && runCatching {
            before.put("fileLyrics", EmbeddedLyricsReader(context).read(song.uri.toUri()))
            AudioTagEditor(context).writeLyrics(song.uri, text.trim())
        }.isSuccess
        if (!embedded) before.remove("fileLyrics")
        journal.add(EditBackup(id, System.currentTimeMillis(), batch, song.id, song.uri, song.title, "lyrics", before))
        return id to embedded
    }

    /** Puts back what [backup] replaced. */
    fun undo(backup: EditBackup) {
        val before = backup.before
        val oldCover = backup.coverFile?.let { File(it) }?.takeIf { it.exists() }?.readBytes()
        when (backup.kind) {
            "lyrics" -> {
                if (before.has("lyrics")) lyrics.put(backup.songId, before.getString("lyrics")) else lyrics.delete(backup.songId)
                // The file gets back the lyrics it had (none = removed).
                if (before.has("fileLyrics")) AudioTagEditor(context).writeLyrics(backup.uri, before.getString("fileLyrics"))
            }
            "video" -> {
                val editor = VideoMetadataEditor(context)
                val draft = editor.read(backup.uri).draft
                val thumbnail = if (!backup.coverChanged) draft.thumbnailUri
                    else oldCover?.let { editor.saveVideoFrame(backup.uri, CoverDownloader.decode(it)) } ?: ""
                editor.save(backup.uri, draft.copy(title = before.optString("title"), author = before.optString("artist"),
                    album = before.optString("album"), genre = before.optString("genre"),
                    description = before.optString("description"), date = before.optString("date"),
                    keywords = before.optString("keywords"), thumbnailUri = thumbnail, thumbnailChanged = backup.coverChanged))
            }
            else -> {
                if (MediaFileIo.needsWriteConsent(context, backup.uri)) throw ActionException(
                    "Android has not allowed Stone to write '${backup.title}'.", "ANDROID_CONSENT", ANDROID_HINT)
                AudioTagEditor(context).write(backup.uri, AudioTags(before.optString("title"), before.optString("artist"),
                    before.optString("album"), before.optString("genre"),
                    cover = if (backup.coverChanged) oldCover else null, removeCover = backup.coverChanged && oldCover == null))
            }
        }
    }

    /** Code and message when an audio file cannot be written, else null. */
    private fun writeProblem(song: Song): Pair<String, String>? = when {
        song.isVideo -> null
        !AudioTagEditor(context).supports(song.uri) -> "UNSUPPORTED_FORMAT" to "'${song.title}' cannot be tagged (MP3, FLAC, M4A only)."
        MediaFileIo.needsWriteConsent(context, song.uri) -> "ANDROID_CONSENT" to "Android has not allowed Stone to write '${song.title}' yet."
        else -> null
    }

    private fun embeddedCover(uri: String): ByteArray? {
        val retriever = MediaMetadataRetriever()
        return try { retriever.setDataSource(context, uri.toUri()); retriever.embeddedPicture } catch (_: Exception) { null }
        finally { retriever.release() }
    }

    private fun List<Song>.ids() = map { it.id }.toSet()

    companion object {
        val FIELDS = listOf("title", "artist", "album", "genre", "description", "date", "keywords")
        const val ANDROID_HINT = "Turn on Android > Ajustes > Apps > Stone > 'Acceso a todos los archivos' so Android stops asking."
    }
}
