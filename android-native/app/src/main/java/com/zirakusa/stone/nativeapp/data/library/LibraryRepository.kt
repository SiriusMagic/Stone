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
    private val appContext = context.applicationContext
    private val videoMetadata = VideoMetadataStore(context)
    private val preferences = context.getSharedPreferences("stone-native-library", Context.MODE_PRIVATE)
    private val cache = LibraryCache(context)
    private val scanner = FolderScanner(context)
    private val disposition = SongDispositionStore(context)
    private val imported = ImportedLibraryStore(context)
    private val policy = RefreshPolicy()
    private val device = DeviceMediaScanner(appContext)
    private val settings = com.zirakusa.stone.nativeapp.data.persistence.StoneSettingsStore(appContext)

    /** Whole-device scanning is on and Android granted the media permission. */
    fun deviceScanActive(): Boolean = settings.read().deviceScan && DeviceMediaAccess.audioGranted(appContext)
    val hasSource: Boolean get() = folders.isNotEmpty() || deviceScanActive()

    /** Folder songs win over MediaStore copies of the same file (they keep SAF write access). */
    private fun merge(folderSongs: List<Song>, deviceSongs: List<Song>): List<Song> {
        val known = folderSongs.mapTo(HashSet()) { it.size to it.durationMs / 1000 }
        return folderSongs + deviceSongs.filterNot { (it.size to it.durationMs / 1000) in known }
    }
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
        val deviceSongs = if (deviceScanActive()) cache.read(DEVICE_KEY) else emptyList()
        entries((merge(folders.flatMap(cache::read), deviceSongs) + imported.songs()).distinctBy { it.id })
    }

    suspend fun refresh(manual: Boolean): CachedLibrary? {
        val trees = folders
        val scanDevice = deviceScanActive()
        if (trees.isEmpty() && !scanDevice) return null
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
                if (trees.isNotEmpty() && scannedTrees == 0 && !scanDevice)
                    throw IllegalStateException("No se pudo actualizar ninguna carpeta")
                val deviceSongs = if (scanDevice) device.scan().also { cache.write(DEVICE_KEY, it) } else emptyList()
                (merge(merged, deviceSongs).sortedBy { it.title.lowercase() } + imported.songs()).distinctBy { it.id }
            }
            success = true
            entries(songs)
        } finally { policy.finish(success, SystemClock.elapsedRealtime()) }
    }

    fun delete(song: Song) = disposition.delete(song.id)
    fun restoreDeleted(song: Song) = disposition.restoreDeleted(song.id)

    /** Deletes the file from the device. Blocking I/O: call from a background dispatcher. */
    fun deletePermanently(song: Song): Boolean {
        if (!SongFileDeleter.delete(appContext, song.uri)) return false
        disposition.purge(song.id)
        imported.remove(song.uri)
        return true
    }

    fun markPurged(song: Song) { disposition.purge(song.id); imported.remove(song.uri) }

    private fun entries(songs: List<Song>): CachedLibrary {
        val deleted = disposition.deletedIds()
        val purged = disposition.purgedIds()
        val visible = songs.filterNot { it.id in purged }.map(videoMetadata::applyToSong)
        return CachedLibrary(visible.filterNot { it.id in deleted }, visible.filter { it.id in deleted })
    }

    private companion object { const val DEVICE_KEY = "device://mediastore" }
}
