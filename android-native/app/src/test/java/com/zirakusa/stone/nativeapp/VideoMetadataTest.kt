package com.zirakusa.stone.nativeapp

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.zirakusa.stone.core.Song
import com.zirakusa.stone.nativeapp.data.library.Mp4MetadataWriter
import com.zirakusa.stone.nativeapp.data.library.VideoContainer
import com.zirakusa.stone.nativeapp.data.library.VideoMetadataDraft
import com.zirakusa.stone.nativeapp.data.library.VideoMetadataStore
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
        val moov = atom("moov", atom("trak", byteArrayOf(9, 8, 7)))
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

    @Test fun videoContainerDetectionDoesNotPromiseUnsupportedWriters() {
        assertEquals(VideoContainer.MP4, VideoContainer.fromUri("content://x/movie.mp4"))
        assertEquals(VideoContainer.M4V, VideoContainer.fromUri("file:///movie.m4v"))
        assertEquals(VideoContainer.MKV, VideoContainer.fromUri("content://x/movie.mkv"))
        assertEquals(VideoContainer.WEBM, VideoContainer.fromUri("content://x/movie.webm"))
        assertEquals(VideoContainer.OTHER, VideoContainer.fromUri("content://x/movie.avi"))
    }

    private fun atom(type: String, payload: ByteArray): ByteArray {
        val size = payload.size + 8
        return byteArrayOf(
            (size shr 24).toByte(), (size shr 16).toByte(), (size shr 8).toByte(), size.toByte(),
            *type.toByteArray(StandardCharsets.ISO_8859_1), *payload
        )
    }
}
