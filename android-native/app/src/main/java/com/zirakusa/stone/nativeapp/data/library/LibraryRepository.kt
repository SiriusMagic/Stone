package com.zirakusa.stone.nativeapp.data.library

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import androidx.core.content.edit
import androidx.core.net.toUri
import com.zirakusa.stone.core.RefreshPolicy
import com.zirakusa.stone.core.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

data class CachedLibrary(val songs: List<Song>, val deletedSongs: List<Song>)

class LibraryRepository(context: Context) {
    private val videoMetadata = VideoMetadataStore(context)
    private val preferences = context.getSharedPreferences("stone-native-library", Context.MODE_PRIVATE)
    private val cache = LibraryCache(context)
    private val scanner = FolderScanner(context)
    private val disposition = SongDispositionStore(context)
    private val imported = ImportedLibraryStore(context)
    private val policy = RefreshPolicy()
    init { disposition.clearAudioOnly(); disposition.restoreRetiredHidden() }
    val folders: List<String> get() = preferences.getStringSet("folders", null)
        ?.toList().orEmpty().ifEmpty { listOfNotNull(preferences.getString("folder", null)) }
    val folder: String? get() = folders.firstOrNull()

    fun selectFolder(uri: Uri) {
        val selected = (folders + uri.toString()).distinct()
        preferences.edit {
            putStringSet("folders", selected.toSet())
            putString("folder", selected.firstOrNull())
        }
    }
    suspend fun cached(): CachedLibrary = withContext(Dispatchers.IO) {
        entries((folders.flatMap(cache::read) + imported.songs()).distinctBy { it.id })
    }

    suspend fun refresh(manual: Boolean): CachedLibrary? {
        val trees = folders
        if (trees.isEmpty()) return null
        if (!policy.tryStart(manual, SystemClock.elapsedRealtime())) return null
        var success = false
        return try {
            val songs = withContext(Dispatchers.IO) {
                var scannedTrees = 0
                val merged = trees.flatMap { tree ->
                    currentCoroutineContext().ensureActive()
                    val previous = cache.read(tree)
                    try {
                        scanner.scan(tree.toUri(), previous).also { cache.write(tree, it); scannedTrees++ }
                    } catch (error: Exception) {
                        if (previous.isEmpty()) throw error
                        previous
                    }
                }.distinctBy { it.id }.sortedBy { it.title.lowercase() }
                if (scannedTrees == 0) throw IllegalStateException("No se pudo actualizar ninguna carpeta")
                (merged + imported.songs()).distinctBy { it.id }
            }
            success = true
            entries(songs)
        } finally { policy.finish(success, SystemClock.elapsedRealtime()) }
    }

    fun delete(song: Song) = disposition.delete(song.id)
    fun restoreDeleted(song: Song) = disposition.restoreDeleted(song.id)
    private fun entries(songs: List<Song>): CachedLibrary {
        // Backups from older versions can reintroduce hidden IDs. Restore their
        // visibility before building the library and keep the deleted list intact.
        disposition.restoreRetiredHidden()
        val deleted = disposition.deletedIds()
        val visible = songs.map(videoMetadata::applyToSong)
        return CachedLibrary(visible.filterNot { it.id in deleted }, visible.filter { it.id in deleted })
    }
}
