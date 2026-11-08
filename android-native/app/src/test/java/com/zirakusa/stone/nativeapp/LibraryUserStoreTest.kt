package com.zirakusa.stone.nativeapp

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.zirakusa.stone.core.Song
import com.zirakusa.stone.nativeapp.data.library.LibraryUserStore
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LibraryUserStoreTest {
    @Test fun bulkCountsIncludeOnlyPlayedVisibleSongs() {
        val store = LibraryUserStore(ApplicationProvider.getApplicationContext<Context>())
        val playedUri = "content://bulk/${System.nanoTime()}"
        val songs = listOf(playedUri, "$playedUri/unplayed").map { uri ->
            Song(uri, uri, "Artist", "Album", "Music", 1000, 1, 1)
        }
        store.recordPlay(songs.first().id)
        assertEquals(mapOf(songs.first().id to 1), store.playCounts(songs))
    }
}
