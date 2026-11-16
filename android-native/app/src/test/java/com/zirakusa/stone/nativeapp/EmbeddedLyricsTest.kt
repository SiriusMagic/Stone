package com.zirakusa.stone.nativeapp

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.zirakusa.stone.core.LyricsParser
import com.zirakusa.stone.nativeapp.data.library.AudioTagEditor
import com.zirakusa.stone.nativeapp.data.library.AudioTags
import com.zirakusa.stone.nativeapp.data.library.EmbeddedLyricsReader
import com.zirakusa.stone.nativeapp.data.library.FlacTag
import com.zirakusa.stone.nativeapp.data.library.Id3Tag
import com.zirakusa.stone.nativeapp.data.library.Mp4MetadataWriter
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Lyrics saved in Stone go inside the song file and are read back from it (what a shared copy carries). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EmbeddedLyricsTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val audio = ByteArray(64) { (it * 7).toByte() }
    private val lrc = "[00:05.00]Primera frase\n[00:08.50]Segunda — ñandú"

    private fun file(ext: String, bytes: ByteArray) = File(context.cacheDir, "l-${System.nanoTime()}.$ext").apply { writeBytes(bytes) }
    private fun read(file: File) = EmbeddedLyricsReader(context).read(Uri.fromFile(file))

    @Test fun mp3LyricsAreWrittenReadBackAndKeepTitleAndAudio() {
        for (version in listOf(3, 4)) {
            val title = if (version == 4) byteArrayOf(3) + "Mi canción".toByteArray(Charsets.UTF_8)
                else byteArrayOf(0) + "Mi cancion".toByteArray(Charsets.ISO_8859_1)
            val frame = "TIT2".toByteArray(Charsets.ISO_8859_1) +
                (if (version == 4) Id3Tag.syncSafeBytes(title.size) else java.nio.ByteBuffer.allocate(4).putInt(title.size).array()) +
                byteArrayOf(0, 0) + title
            val head = "ID3".toByteArray(Charsets.ISO_8859_1) + byteArrayOf(version.toByte(), 0, 0) +
                Id3Tag.syncSafeBytes(frame.size) + frame
            val song = file("mp3", head + audio)
            AudioTagEditor(context).writeLyrics(song.toURI().toString(), lrc)
            val bytes = song.readBytes()
            assertArrayEquals(audio, bytes.copyOfRange(Id3Tag.size(bytes), bytes.size))
            assertTrue("title kept (v$version)", String(bytes, Charsets.ISO_8859_1).contains("TIT2"))
            val back = read(song)
            assertEquals(lrc, back)
            assertEquals(5_000L, LyricsParser.parse(back).synced.first().timeMs)
            // Saving again replaces (never duplicates) and blank removes them.
            AudioTagEditor(context).writeLyrics(song.toURI().toString(), "Otra")
            assertEquals("Otra", read(song))
            AudioTagEditor(context).writeLyrics(song.toURI().toString(), "")
            assertEquals("", read(song))
            assertTrue(String(song.readBytes(), Charsets.ISO_8859_1).contains("TIT2"))
        }
    }

    @Test fun mp3WithoutTagGetsOne() {
        val song = file("mp3", audio)
        AudioTagEditor(context).writeLyrics(song.toURI().toString(), lrc)
        assertEquals(lrc, read(song))
    }

    @Test fun flacLyricsKeepOtherComments() {
        val streamInfo = byteArrayOf(0, 0, 0, 34) + ByteArray(34)
        val padding = byteArrayOf(0x81.toByte(), 0, 1, 0) + ByteArray(256)
        val head = "fLaC".toByteArray(Charsets.ISO_8859_1) + streamInfo + padding
        val start = FlacTag.start(head)
        val titled = FlacTag.rebuild(head, start, FlacTag.metadataEnd(head, start), AudioTags(title = "Título"))
        val song = file("flac", titled + audio)
        AudioTagEditor(context).writeLyrics(song.toURI().toString(), lrc)
        assertEquals(lrc, read(song))
        assertEquals("Título", FlacTag.readComments(song.readBytes())["TITLE"])
    }

    @Test fun m4aLyricsUseTheLyrTagAndKeepTheTitle() {
        val moov = atom("moov", atom("trak", atom("tkhd", byteArrayOf(9, 8, 7))))
        val song = file("m4a", atom("ftyp", "M4A ".toByteArray(Charsets.ISO_8859_1) + ByteArray(4)) + moov + atom("mdat", audio))
        AudioTagEditor(context).write(song.toURI().toString(), AudioTags(title = "Tema"))
        AudioTagEditor(context).writeLyrics(song.toURI().toString(), lrc)
        assertEquals(lrc, read(song))
        assertEquals("Tema", Mp4MetadataWriter.readTags(context, song.toURI().toString())["©nam"])
    }

    @Test fun normalTagEditsKeepEmbeddedLyrics() {
        val song = file("mp3", audio)
        AudioTagEditor(context).writeLyrics(song.toURI().toString(), lrc)
        AudioTagEditor(context).write(song.toURI().toString(), AudioTags(title = "Nuevo"))
        assertEquals(lrc, read(song))
    }

    private fun atom(type: String, payload: ByteArray) =
        java.nio.ByteBuffer.allocate(4).putInt(8 + payload.size).array() + type.toByteArray(Charsets.ISO_8859_1) + payload
}
