package com.zirakusa.stone.nativeapp

import android.os.Looper
import com.zirakusa.stone.core.Song
import com.zirakusa.stone.nativeapp.automation.AutomationHost
import com.zirakusa.stone.nativeapp.automation.McpServer
import com.zirakusa.stone.nativeapp.automation.StoneAutomation
import com.zirakusa.stone.nativeapp.data.library.StonePlaylist
import com.zirakusa.stone.nativeapp.playback.AudioMode
import com.zirakusa.stone.nativeapp.playback.PlaybackState
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Manual compatibility check with real MCP clients: keeps Stone's server up for 90 s.
 * Skipped unless STONE_MCP_LIVE=1, so normal test runs are not slowed down.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class McpLiveServerTest {
    @Test fun serveForRealClients() {
        assumeTrue(System.getenv("STONE_MCP_LIVE") == "1")
        val songs = listOf(Song("content://fixture/1.mp3", "After Dark", "Mr.Kitty", "Time", "Music", 258_000, 1, 1))
        var playing = false
        StoneAutomation.host = object : AutomationHost {
            override val playback get() = PlaybackState(ready = true, title = "After Dark", playing = playing, currentId = songs[0].id)
            override val songs get() = songs
            override val playlists = emptyList<StonePlaylist>()
            override val theme = "dark"
            override val language = "es"
            override fun progress() = 1000L to 258_000L
            override fun toggle() { playing = !playing }
            override fun next() = Unit
            override fun previous() = Unit
            override fun seek(positionMs: Long) = Unit
            override fun setSpeed(speed: Float) = Unit
            override fun setVolume(percent: Int) = Unit
            override fun play(song: Song, queue: List<Song>) = Unit
            override fun playNext(songs: List<Song>) = Unit
            override fun setAudioMode(mode: AudioMode) = Unit
            override fun createPlaylist(name: String) = Unit
            override fun addToPlaylist(playlistId: String, song: Song) = Unit
            override fun setTheme(key: String) = Unit
            override fun setLanguage(code: String) = Unit
            override fun refreshLibrary() = Unit
            override fun setSleepTimer(minutes: Int, finishTrack: Boolean, fadeOut: Boolean) = Unit
            override fun extendSleepTimer(minutes: Int) = Unit
            override fun setLauncherIcon(key: String) = Unit
        }
        check(McpServer.start("live-token", localNetwork = false))
        println("STONE_MCP_READY")
        val end = System.currentTimeMillis() + 90_000
        while (System.currentTimeMillis() < end) { shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(5) }
        McpServer.stop()
    }
}
