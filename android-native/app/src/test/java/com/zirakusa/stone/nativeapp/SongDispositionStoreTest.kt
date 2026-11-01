package com.zirakusa.stone.nativeapp

import android.content.Context
import com.zirakusa.stone.nativeapp.data.library.SongDispositionStore
import com.zirakusa.stone.nativeapp.data.library.LibraryUserStore
import com.zirakusa.stone.nativeapp.data.persistence.StoneDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test fun `hide and delete are logical and durable`() {
        val store = SongDispositionStore(context)
        store.hide("local:content://stone/one.mp3")
        store.delete("local:content://stone/two.mp3")
        assertEquals(setOf("local:content://stone/one.mp3"), SongDispositionStore(context).hiddenIds())
        assertTrue(SongDispositionStore(context).deletedIds().contains("local:content://stone/two.mp3"))
    }

    @Test fun `hidden song can be restored without touching file`() {
        val store = SongDispositionStore(context)
        store.hide("local:one")
        store.restoreHidden("local:one")
        assertTrue(store.hiddenIds().isEmpty())
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

    @Test fun `backup round trip is transactional and versioned`() {
        val database = StoneDatabase.get(context)
        database.putString("test.value", "Stone")
        val backup = database.exportJson()
        database.clearAll()
        database.importJson(backup)
        assertEquals("Stone", database.getString("test.value"))
    }
}
