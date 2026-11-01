package com.zirakusa.stone.nativeapp.data.library

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import com.zirakusa.stone.core.Song
import com.zirakusa.stone.core.LyricsParser
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.IOException

/** Uses Stone's selected SAF folder; never broadens access to the whole phone. */
class FolderScanner(private val context: Context) {
    private val reader = AudioMetadataReader(context)
    private val extensions = setOf("mp3", "m4a", "aac", "wav", "flac", "ogg")
    private val videoExtensions = setOf("mp4", "mkv", "mov", "webm")

    suspend fun scan(tree: Uri, previous: List<Song>): List<Song> {
        val known = previous.associateBy { it.uri }
        val result = linkedMapOf<String, Song>()
        val pending = ArrayDeque<Pair<String, String>>()
        pending.add(DocumentsContract.getTreeDocumentId(tree) to "Stone")
        val visited = mutableSetOf<String>()
        val projection = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE, Document.COLUMN_SIZE, Document.COLUMN_LAST_MODIFIED)
        while (pending.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val (parent, folder) = pending.removeFirst()
            if (!visited.add(parent)) continue
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parent)
            val cursor = context.contentResolver.query(children, projection, null, null, null)
                ?: throw IOException("Folder provider returned no cursor")
            cursor.use {
                data class Entry(val id: String, val name: String, val mime: String, val size: Long,
                    val modified: Long)
                val entries = mutableListOf<Entry>()
                while (it.moveToNext()) {
                    currentCoroutineContext().ensureActive()
                    entries += Entry(it.getString(0), it.getString(1).orEmpty(), it.getString(2),
                        it.getLong(3), it.getLong(4))
                }
                val sidecars = entries.filter { entry ->
                    entry.name.substringAfterLast('.', "").lowercase() in setOf("lrc", "txt")
                }.associateBy { entry -> entry.name.substringBeforeLast('.').lowercase() }
                entries.forEach { entry ->
                    currentCoroutineContext().ensureActive()
                    if (entry.mime == Document.MIME_TYPE_DIR) {
                        pending.add(entry.id to entry.name)
                        return@forEach
                    }
                    val extension = entry.name.substringAfterLast('.', "").lowercase()
                    if (extension !in extensions && extension !in videoExtensions) return@forEach
                    val uri = DocumentsContract.buildDocumentUriUsingTree(tree, entry.id)
                    val size = entry.size
                    val modified = entry.modified
                    val cached = known[uri.toString()]
                    var song = if (cached?.canReuse(size, modified) == true) cached
                        else reader.read(uri, entry.name, folder, size, modified)
                    song = song.copy(isVideo = extension in videoExtensions)
                    sidecars[entry.name.substringBeforeLast('.').lowercase()]?.takeIf { !song.isVideo }?.let { lyric ->
                        val lyricUri = DocumentsContract.buildDocumentUriUsingTree(tree, lyric.id)
                        val value = context.contentResolver.openInputStream(lyricUri)?.bufferedReader()
                            ?.use { source -> source.readText() }.orEmpty()
                        val parsed = LyricsParser.parse(value)
                        if (parsed.plain.isNotEmpty()) song = song.copy(lyrics = parsed.plain,
                            syncedLyrics = parsed.synced)
                    }
                    result[uri.toString()] = song
                }
            }
        }
        return result.values.sortedBy { it.title.lowercase() }
    }
}
