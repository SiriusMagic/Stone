package com.zirakusa.stone.nativeapp.automation

import com.zirakusa.stone.core.Song
import com.zirakusa.stone.nativeapp.data.library.StonePlaylist
import com.zirakusa.stone.nativeapp.playback.AudioMode
import com.zirakusa.stone.nativeapp.playback.PlaybackState

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
}
