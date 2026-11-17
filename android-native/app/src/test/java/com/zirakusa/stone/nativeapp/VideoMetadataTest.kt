package com.zirakusa.stone.nativeapp

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.zirakusa.stone.core.Song
import com.zirakusa.stone.nativeapp.data.library.tags.Mp4MetadataWriter
import com.zirakusa.stone.nativeapp.data.library.tags.VideoContainer
import com.zirakusa.stone.nativeapp.data.library.tags.VideoMetadataDraft
import com.zirakusa.stone.nativeapp.data.library.tags.VideoMetadataStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.charset.StandardCharsets

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VideoMetadataTest {
    @Test fun videoMetadataRoundTripAppliesOnlyToVideoSongs() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val uri = "content://video/${System.nanoTime()}.mkv"
        val store = VideoMetadataStore(context)
        store.save(uri, VideoMetadataDraft(title = "Título interno", author = "Creador",
            description = "Comentario", thumbnailUri = "file:///private/cover.jpg"), false)

        val video = Song(uri, "archivo", "Stone", "", "Videos", 10, 1, 1, isVideo = true)
        val audio = video.copy(isVideo = false)
        assertEquals("Título interno", store.applyToSong(video).title)
        assertEquals("Creador", store.applyToSong(video).artist)
        assertEquals("file:///private/cover.jpg", store.applyToSong(video).artworkUri)
        assertEquals("archivo", store.applyToSong(audio).title)
        assertEquals("Comentario", store.read(uri)?.draft?.description)
    }

    @Test fun mp4WriterAddsMetadataWithoutReencodingMediaAtoms() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "stone-${System.nanoTime()}.mp4")
        val mediaPayload = byteArrayOf(1, 2, 3, 4, 5)
        val media = atom("mdat", mediaPayload)
        val moov = atom("moov", atom("trak", atom("tkhd", byteArrayOf(9, 8, 7))))
        file.writeBytes(atom("ftyp", byteArrayOf(0, 0, 0, 0)) + moov + media)
        val beforeMedia = media

        val uri = file.toURI().toString()
        assertTrue(Mp4MetadataWriter.canWrite(context, uri))
        Mp4MetadataWriter.write(context, uri, VideoMetadataDraft(title = "Nuevo título", author = "Stone"), null, false)

        val after = file.readBytes()
        assertTrue(after.toList().windowed(beforeMedia.size).any { it.toByteArray().contentEquals(beforeMedia) })
        assertTrue(String(after, StandardCharsets.ISO_8859_1).contains("©nam"))
        assertTrue(String(after, StandardCharsets.UTF_8).contains("Nuevo título"))
    }

    @Test fun mp4WriterShiftsChunkOffsetsWhenMoovPrecedesMedia() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "stone-faststart-${System.nanoTime()}.mp4")
        val ftyp = atom("ftyp", byteArrayOf(0, 0, 0, 0))
        val samples = byteArrayOf(42, 43, 44, 45)
        // Build once to learn the moov size, then point stco at the first sample byte.
        fun moovWith(offset: Int) = atom("moov", atom("trak", atom("mdia", atom("minf", atom("stbl",
            atom("stco", byteArrayOf(0, 0, 0, 0, 0, 0, 0, 1) + int(offset)))))))
        val provisional = moovWith(0)
        val sampleOffset = ftyp.size + provisional.size + 8
        file.writeBytes(ftyp + moovWith(sampleOffset) + atom("mdat", samples))

        val uri = file.toURI().toString()
        Mp4MetadataWriter.write(context, uri, VideoMetadataDraft(title = "Desplazado", keywords = "a, b"), null, false)

        val after = file.readBytes()
        val stco = String(after, StandardCharsets.ISO_8859_1).indexOf("stco")
        val newOffset = java.nio.ByteBuffer.wrap(after, stco + 12, 4).int
        assertTrue(newOffset > sampleOffset)
        assertEquals(42.toByte(), after[newOffset])
        assertEquals("Desplazado", Mp4MetadataWriter.readTags(context, uri)["©nam"])
        assertEquals("a, b", Mp4MetadataWriter.readTags(context, uri)["keyw"])
    }

    @Test fun mp4WriterEditsTrailingMoovInPlaceAndClearsBlankTags() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "stone-tail-${System.nanoTime()}.mp4")
        file.writeBytes(atom("ftyp", byteArrayOf(0, 0, 0, 0)) + atom("mdat", byteArrayOf(7, 7, 7)) +
            atom("moov", atom("trak", byteArrayOf(1))))
        val uri = file.toURI().toString()
        Mp4MetadataWriter.write(context, uri, VideoMetadataDraft(title = "Uno", genre = "Rock"), null, false)
        assertEquals("Rock", Mp4MetadataWriter.readTags(context, uri)["©gen"])
        Mp4MetadataWriter.write(context, uri, VideoMetadataDraft(title = "Dos"), null, false)
        val tags = Mp4MetadataWriter.readTags(context, uri)
        assertEquals("Dos", tags["©nam"])
        assertTrue("©gen" !in tags)
        assertTrue(file.readBytes().copyOfRange(20, 23).contentEquals(byteArrayOf(7, 7, 7)))
    }

    @Test fun videoContainerDetectionDoesNotPromiseUnsupportedWriters() {
        assertEquals(VideoContainer.MP4, VideoContainer.fromUri("content://x/movie.mp4"))
        assertEquals(VideoContainer.M4V, VideoContainer.fromUri("file:///movie.m4v"))
        assertEquals(VideoContainer.MKV, VideoContainer.fromUri("content://x/movie.mkv"))
        assertEquals(VideoContainer.WEBM, VideoContainer.fromUri("content://x/movie.webm"))
        assertEquals(VideoContainer.OTHER, VideoContainer.fromUri("content://x/movie.avi"))
    }

    private fun int(value: Int) = java.nio.ByteBuffer.allocate(4).putInt(value).array()

    private fun atom(type: String, payload: ByteArray): ByteArray {
        val size = payload.size + 8
        return byteArrayOf(
            (size shr 24).toByte(), (size shr 16).toByte(), (size shr 8).toByte(), size.toByte(),
            *type.toByteArray(StandardCharsets.ISO_8859_1), *payload
        )
    }
}
