package com.zirakusa.stone.nativeapp.automation.actions

import com.zirakusa.stone.core.Song
import com.zirakusa.stone.nativeapp.data.library.user.StonePlaylist
import com.zirakusa.stone.nativeapp.playback.control.AudioMode
import com.zirakusa.stone.nativeapp.playback.control.PlaybackState

/**
 * What the running app offers to automation. Implemented by the UI with its existing view
 * models, so agents use exactly the same code paths as the buttons on screen.
 */
interface AutomationHost {
    val playback: PlaybackState
    val songs: List<Song>
    val playlists: List<StonePlaylist>
    val theme: String
    val language: String

    fun canPlayForIntent(): Boolean = playback.ready
    fun applyIntentVolume(percent: Int): Int? = null
    fun applyIntentEqualizer(enabled: Boolean): Boolean = false
    fun isIntentEqualizerActive(): Boolean = false
    fun audibleIntentTrack(): String? = playback.currentId.takeIf { playback.playing }
    fun intentPlaybackFailed(): Boolean = playback.failed
    fun progress(): Pair<Long, Long>
    fun toggle()
    fun next()
    fun previous()
    fun seek(positionMs: Long)
    fun setSpeed(speed: Float)
    fun setVolume(percent: Int)
    fun play(song: Song, queue: List<Song>)
    fun playNext(songs: List<Song>)
    fun setAudioMode(mode: AudioMode)
    fun createPlaylist(name: String)
    fun addToPlaylist(playlistId: String, song: Song)
    fun setTheme(key: String)
    fun setLanguage(code: String)
    fun refreshLibrary()
    /** Sleep timer: [minutes] 0 turns it off. */
    fun setSleepTimer(minutes: Int, finishTrack: Boolean, fadeOut: Boolean)
    fun extendSleepTimer(minutes: Int)
    fun setLauncherIcon(key: String)

    // Added in 1.6.7. Defaults keep older hosts (tests) compiling.
    val favoriteIds: Set<String> get() = emptySet()
    val playCounts: Map<String, Int> get() = emptyMap()
    /** Sort key (LibrarySort name in lowercase) and whether it is descending. */
    val librarySort: Pair<String, Boolean> get() = "title" to false
    val videoScale: String get() = "auto"
    fun setShuffle(enabled: Boolean) {}
    /** 0 off, 1 one, 2 all (Media3 repeat modes). */
    fun setRepeat(mode: Int) {}
    fun playQueueIndex(index: Int) {}
    fun moveQueue(from: Int, to: Int) {}
    fun removeFromQueue(index: Int) {}
    fun clearQueue() {}
    fun toggleFavorite() {}
    fun setEqualizerEnabled(enabled: Boolean) {}
    fun setEqualizerBand(index: Int, value: Int) {}
    fun setEqualizerPreset(name: String, values: List<Int>) {}
    fun setEqualizerByGenre(enabled: Boolean) {}
    fun removeFromPlaylist(playlistId: String, song: Song) {}
    fun sortLibrary(key: String, descending: Boolean) {}
    fun setVideoScale(key: String) {}
    /** Window brightness 2-100, or null to follow the system again. False when no screen is available. */
    fun setScreenBrightness(percent: Int?): Boolean = false
    /** portrait, landscape or auto. */
    fun setOrientation(key: String): Boolean = false

    // Added in 1.6.8.
    fun setFavorite(songId: String, favorite: Boolean) {}
    fun renamePlaylist(playlistId: String, name: String) {}
    fun deletePlaylist(playlistId: String) {}
    fun setHardwareAcceleration(key: String) {}
    fun setPlayWithOthers(enabled: Boolean) {}
}
