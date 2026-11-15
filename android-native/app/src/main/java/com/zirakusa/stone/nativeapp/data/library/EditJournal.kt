package com.zirakusa.stone.nativeapp.data.library

import android.content.Context
import com.zirakusa.stone.nativeapp.data.persistence.StoneDatabase
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** What a tag, lyrics or cover edit replaced, so it can be undone. */
data class EditBackup(
    val id: String,
    val timeMs: Long,
    /** Edits made by one tool call share a batch id: undoing the batch reverts all of them. */
    val batch: String,
    val songId: String,
    val uri: String,
    val title: String,
    /** audio, video or lyrics. */
    val kind: String,
    val before: JSONObject,
    /** True when the edit changed the cover; [coverFile] then holds the previous one (null = there was none). */
    val coverChanged: Boolean = false,
    val coverFile: String? = null,
) {
    fun json(): JSONObject = JSONObject().put("id", id).put("time", timeMs).put("batch", batch).put("songId", songId)
        .put("uri", uri).put("title", title).put("kind", kind).put("before", before).put("coverChanged", coverChanged)
        .apply { coverFile?.let { put("coverFile", it) } }

    companion object {
        fun from(json: JSONObject) = EditBackup(json.getString("id"), json.optLong("time"), json.optString("batch"),
            json.optString("songId"), json.optString("uri"), json.optString("title"), json.optString("kind"),
            json.optJSONObject("before") ?: JSONObject(), json.optBoolean("coverChanged"),
            json.optString("coverFile").takeIf { it.isNotBlank() })
    }
}

/**
 * Backups of agent edits, newest first, kept in Stone's database (previous covers as files in
 * private storage). Only the last [LIMIT] edits are kept.
 */
class EditJournal(context: Context) {
    private val database = StoneDatabase.get(context)
    private val folder = File(context.filesDir, "mcp-backups").also { it.mkdirs() }

    fun list(): List<EditBackup> = runCatching {
        val array = JSONArray(database.getString(KEY) ?: "[]")
        (0 until array.length()).map { EditBackup.from(array.getJSONObject(it)) }
    }.getOrDefault(emptyList())

    @Synchronized fun add(backup: EditBackup) {
        val all = listOf(backup) + list()
        all.drop(LIMIT).forEach { old -> old.coverFile?.let { File(it).delete() } }
        save(all.take(LIMIT))
    }

    /** Forgets entries once undone; their cover copies are deleted too. */
    @Synchronized fun remove(ids: Set<String>) {
        val (gone, kept) = list().partition { it.id in ids }
        gone.forEach { it.coverFile?.let { path -> File(path).delete() } }
        save(kept)
    }

    fun saveCover(id: String, bytes: ByteArray): String =
        File(folder, "$id.img").also { it.writeBytes(bytes) }.absolutePath

    private fun save(entries: List<EditBackup>) = database.putString(KEY, JSONArray(entries.map { it.json() }).toString())

    private companion object {
        const val KEY = "mcp.journal"
        const val LIMIT = 300
    }
}
