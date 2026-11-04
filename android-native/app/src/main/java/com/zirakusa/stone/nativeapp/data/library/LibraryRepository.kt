package com.zirakusa.stone.nativeapp.data.library

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import com.zirakusa.stone.core.RefreshPolicy
import com.zirakusa.stone.core.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class LibraryRepository(context: Context) {
    private val preferences = context.getSharedPreferences("stone-native-library", Context.MODE_PRIVATE)
    private val cache = LibraryCache(context)
    private val scanner = FolderScanner(context)
    private val disposition = SongDispositionStore(context)
    private val imported = ImportedLibraryStore(context)
    private val policy = RefreshPolicy()
    val folders: List<String> get() = preferences.getStringSet("folders", null)
        ?.toList().orEmpty().ifEmpty { listOfNotNull(preferences.getString("folder", null)) }
    val folder: String? get() = folders.firstOrNull()

    fun selectFolder(uri: Uri) {
        val selected = (folders + uri.toString()).distinct()
        preferences.edit().putStringSet("folders", selected.toSet())
            .putString("folder", selected.firstOrNull()).apply()
    }
    suspend fun cached(): List<Song> = withContext(Dispatchers.IO) {
        (folders.flatMap(cache::read) + imported.songs()).distinctBy { it.id }.let(::visible)
    }
    suspend fun allCached(): List<Song> = withContext(Dispatchers.IO) {
        (folders.flatMap(cache::read) + imported.songs()).distinctBy { it.id }
    }

    suspend fun refresh(manual: Boolean): List<Song>? {
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
                        scanner.scan(Uri.parse(tree), previous).also { cache.write(tree, it); scannedTrees++ }
                    } catch (error: Exception) {
                        if (previous.isEmpty()) throw error
                        previous
                    }
                }.distinctBy { it.id }.sortedBy { it.title.lowercase() }
                if (scannedTrees == 0) throw IllegalStateException("No se pudo actualizar ninguna carpeta")
                (merged + imported.songs()).distinctBy { it.id }
            }
            success = true
            visible(songs)
        } finally { policy.finish(success, SystemClock.elapsedRealtime()) }
    }

    fun hide(song: Song) = disposition.hide(song.id)
    fun delete(song: Song) = disposition.delete(song.id)
    fun restoreHidden(song: Song) = disposition.restoreHidden(song.id)
    fun restoreDeleted(song: Song) = disposition.restoreDeleted(song.id)
    fun hidden(songs: List<Song>) = songs.filter { it.id in disposition.hiddenIds() }
    fun deleted(songs: List<Song>) = songs.filter { it.id in disposition.deletedIds() }
    private fun visible(songs: List<Song>): List<Song> {
        val excluded = disposition.hiddenIds() + disposition.deletedIds()
        return songs.filterNot { it.id in excluded }
    }
}
