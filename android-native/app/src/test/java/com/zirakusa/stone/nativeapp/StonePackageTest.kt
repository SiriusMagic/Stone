package com.zirakusa.stone.nativeapp

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.zirakusa.stone.core.Song
import com.zirakusa.stone.nativeapp.data.library.ImportedLibraryStore
import com.zirakusa.stone.nativeapp.data.transfer.StonePackage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StonePackageTest {
    @Test fun `musp round trip imports bytes and metadata without touching source`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val source = File(context.cacheDir, "package-source-${System.nanoTime()}.mp3").apply {
            writeBytes(byteArrayOf(1, 2, 3, 4, 5))
        }
        val packageFile = File(context.cacheDir, "package-${System.nanoTime()}.musp")
        val song = Song(source.toURI().toString(), "Prueba", "Stone", "Álbum", "Importada",
            1234, source.length(), source.lastModified(), listOf("Una línea"))
        StonePackage.exportToFile(context, packageFile, listOf(song))
        val result = StonePackage.importFile(context, packageFile)
        assertEquals(1, result.imported)
        assertTrue(ImportedLibraryStore(context).songs().any { it.title == "Prueba" })
        assertTrue(source.exists())
        source.delete(); packageFile.delete()
    }
}
