package com.zirakusa.stone.nativeapp

import android.content.Context
import com.zirakusa.stone.nativeapp.data.library.SongDispositionStore
import com.zirakusa.stone.nativeapp.data.library.LibraryUserStore
import com.zirakusa.stone.nativeapp.data.persistence.StoneDatabase
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class SongDispositionStoreTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Before fun clear() {
        context.getSharedPreferences("stone-song-disposition", 0).edit().clear().commit()
        context.getSharedPreferences("stone-library-user-data", 0).edit().clear().commit()
        StoneDatabase.get(context).clearAll()
    }

    @Test fun `playlists history and counts survive reopening`() {
        val store = LibraryUserStore(context)
        val playlist = store.createPlaylist("Viaje")
        store.add(playlist.id, "local:one")
        store.recordPlay("local:one")
        val reopened = LibraryUserStore(context)
        assertEquals(listOf("local:one"), reopened.playlists().single().songIds)
        assertEquals("local:one", reopened.recentIds().first())
        assertEquals(1, reopened.playCount("local:one"))
    }

}
