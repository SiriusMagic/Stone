package com.zirakusa.stone.nativeapp.feature.library

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zirakusa.stone.core.Song
import com.zirakusa.stone.nativeapp.data.library.LibraryRepository
import com.zirakusa.stone.nativeapp.data.library.LibraryUserStore
import com.zirakusa.stone.nativeapp.data.library.StonePlaylist
import com.zirakusa.stone.nativeapp.data.persistence.StoneSettingsStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LibraryState(val songs: List<Song> = emptyList(), val scanning: Boolean = false,
    val hasFolder: Boolean = false, val failed: Boolean = false,
    val playlists: List<StonePlaylist> = emptyList(), val favoriteIds: Set<String> = emptySet(),
    val recentIds: List<String> = emptyList(), val playCounts: Map<String, Int> = emptyMap(),
    val deletedSongs: List<Song> = emptyList())

class LibraryViewModel(application: Application) : AndroidViewModel(application) {
    private data class UserSnapshot(val playlists: List<StonePlaylist>, val favorites: Set<String>,
        val recent: List<String>, val counts: Map<String, Int>)
    private val repository = LibraryRepository(application)
    private val userStore = LibraryUserStore(application)
    private val settings = StoneSettingsStore(application)
    private val mutable = MutableStateFlow(LibraryState(hasFolder = repository.folder != null))
    val state = mutable.asStateFlow()
    private var active = false
    private var scanJob: Job? = null
    private val loadJob = viewModelScope.launch {
        val cached = repository.cached()
        val songs = cached.songs
        val user = readUserSnapshot(songs)
        mutable.update { it.copy(songs = songs, playlists = user.playlists,
            favoriteIds = user.favorites, recentIds = user.recent,
            playCounts = user.counts,
            deletedSongs = cached.deletedSongs) }
    }

    fun onForeground() { active = true; if (settings.read().autoRescan) refresh(false) }
    fun onBackground() { active = false; scanJob?.cancel() }

    fun selectFolder(uri: Uri) {
        try {
            val resolver = getApplication<Application>().contentResolver
            runCatching { resolver.takePersistableUriPermission(uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
                .getOrElse { resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        } catch (_: SecurityException) { mutable.update { it.copy(failed = true) }; return }
        val previous = scanJob
        previous?.cancel()
        scanJob = viewModelScope.launch {
            previous?.join()
            loadJob.join()
            repository.selectFolder(uri)
            mutable.update { it.copy(hasFolder = true, failed = false) }
            if (active) scan(true)
        }
    }

    fun refresh(manual: Boolean = true) {
        if (!active || scanJob?.isActive == true) return
        val previous = scanJob
        scanJob = viewModelScope.launch { previous?.join(); loadJob.join(); scan(manual) }
    }

    fun delete(song: Song) {
        repository.delete(song)
        mutable.update { it.copy(songs = it.songs.filterNot { item -> item.id == song.id },
            deletedSongs = listOf(song) + it.deletedSongs) }
    }

    fun restoreDeleted(song: Song) { repository.restoreDeleted(song); mutable.update {
        it.copy(songs = (it.songs + song).distinctBy { item -> item.id }, deletedSongs = it.deletedSongs - song) } }
    fun recordPlay(song: Song) { userStore.recordPlay(song.id); mutable.update { it.copy(
        recentIds = userStore.recentIds(), playCounts = it.playCounts + (song.id to userStore.playCount(song.id))) } }
    fun syncFavorites() { mutable.update { it.copy(favoriteIds = userStore.favoriteIds()) } }
    fun reloadUserData() { viewModelScope.launch {
        val cached = repository.cached(); val songs = cached.songs
        val user = readUserSnapshot(songs)
        mutable.update { it.copy(songs = songs, playlists = user.playlists,
            favoriteIds = user.favorites, recentIds = user.recent,
            playCounts = user.counts,
            deletedSongs = cached.deletedSongs) }
    } }
    fun createPlaylist(name: String) { if (name.isNotBlank()) { userStore.createPlaylist(name)
        mutable.update { it.copy(playlists = userStore.playlists()) } } }
    fun addToPlaylist(playlistId: String, song: Song) { userStore.add(playlistId, song.id)
        mutable.update { it.copy(playlists = userStore.playlists()) } }
    fun removeFromPlaylist(playlistId: String, song: Song) { userStore.remove(playlistId, song.id)
        mutable.update { it.copy(playlists = userStore.playlists()) } }

    private suspend fun readUserSnapshot(songs: List<Song>): UserSnapshot = withContext(Dispatchers.IO) {
        UserSnapshot(userStore.playlists(), userStore.favoriteIds(), userStore.recentIds(),
            userStore.playCounts(songs))
    }

    private suspend fun scan(manual: Boolean) {
        mutable.update { it.copy(scanning = true, failed = false) }
        try {
            repository.refresh(manual)?.let { refreshed ->
                val songs = refreshed.songs
                val (favorites, counts) = withContext(Dispatchers.IO) {
                    userStore.favoriteIds() to userStore.playCounts(songs)
                }
                mutable.update { it.copy(songs = songs, deletedSongs = refreshed.deletedSongs,
                    favoriteIds = favorites, playCounts = counts) }
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { mutable.update { it.copy(failed = true) } }
        finally { mutable.update { it.copy(scanning = false) } }
    }
}
