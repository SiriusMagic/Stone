package com.zirakusa.stone.nativeapp

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.zirakusa.stone.nativeapp.data.library.AudioTagEditor
import com.zirakusa.stone.nativeapp.data.library.AudioTags
import com.zirakusa.stone.nativeapp.data.library.FlacTag
import com.zirakusa.stone.nativeapp.data.library.Id3Tag
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AudioTagWritersTest {
    private val audio = ByteArray(64) { (it * 7).toByte() }

    @Test fun mp3EditWritesOriginalFileAndKeepsAudioBytes() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "song-${System.nanoTime()}.mp3").apply { writeBytes(audio) }
        val editor = AudioTagEditor(context)
        editor.write(file.toURI().toString(), AudioTags(title = "Nuevo", artist = "Stone"))
        val first = file.readBytes()
        val tagSize = Id3Tag.size(first)
        assertTrue(tagSize > 0)
        assertArrayEquals(audio, first.copyOfRange(tagSize, first.size))
        assertTrue(String(first, Charsets.ISO_8859_1).contains("TIT2"))

        // Second edit fits in the padding: same size, only the tag area changes.
        editor.write(file.toURI().toString(), AudioTags(title = "Otro título", artist = "Stone"))
        val second = file.readBytes()
        assertEquals(first.size, second.size)
        assertArrayEquals(audio, second.copyOfRange(tagSize, second.size))
    }

    @Test fun id3RebuildKeepsUnknownFrames() {
        val year = "TYER".toByteArray(Charsets.ISO_8859_1) + byteArrayOf(0, 0, 0, 5, 0, 0, 0) + "2024".toByteArray()
        val head = "ID3".toByteArray(Charsets.ISO_8859_1) + byteArrayOf(3, 0, 0) + Id3Tag.syncSafeBytes(year.size) + year
        val rebuilt = Id3Tag.rebuild(head, AudioTags(title = "X"))
        val text = String(rebuilt, Charsets.ISO_8859_1)
        assertTrue(text.contains("TYER") && text.contains("2024") && text.contains("TIT2"))
    }

    @Test fun flacEditReplacesCommentsAndReusesPadding() {
        val streamInfo = byteArrayOf(0, 0, 0, 34) + ByteArray(34)
        val padding = byteArrayOf(0x81.toByte(), 0, 1, 0) + ByteArray(256)
        val head = "fLaC".toByteArray(Charsets.ISO_8859_1) + streamInfo + padding
        val start = FlacTag.start(head)
        val end = FlacTag.metadataEnd(head, start)
        assertEquals(head.size, end)
        val rebuilt = FlacTag.rebuild(head, start, end, AudioTags(title = "Título", genre = "Rock"))
        assertEquals(head.size, rebuilt.size)
        val comments = FlacTag.readComments(rebuilt)
        assertEquals("Título", comments["TITLE"])
        assertEquals("Rock", comments["GENRE"])
    }
}
