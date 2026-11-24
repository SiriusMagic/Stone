package com.zirakusa.stone.nativeapp

import com.zirakusa.stone.core.Song
import com.zirakusa.stone.nativeapp.data.library.scan.NotMusic
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Other apps' automatic backups never show up as songs; real songs with similar words still do. */
class NotMusicTest {
    private fun song(title: String, album: String = "", artist: String = "") = Song("content://x/$title", title, artist, album, "Music", 1000, 1, 1)

    @Test fun backupsAreLeftOut() {
        assertTrue(NotMusic.matches(song("AutoBackup_20261115_205503821", "All data backup")))
        assertTrue(NotMusic.matches(song("AutoPlaylist_20261115_205503821", "Playlist backup")))
        assertTrue(NotMusic.matches(song("AutoBackup_20270101_000000000")))          // even without the album tag
        assertTrue(NotMusic.matches(song("Something", "All data backup")))
    }

    @Test fun realMusicStays() {
        listOf(song("Backup Plan", "Music"), song("Auto", "Backup Songs"), song("Playlist", "My playlist"),
            song("Skyfall", "Skyfall", "Adele"), song("Backup", "Backup Album 2"), song("AutoBackup", "Live")).forEach {
            assertFalse(it.title, NotMusic.matches(it))
        }
    }
}
