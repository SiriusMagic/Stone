package com.zirakusa.stone.nativeapp

import android.net.Uri
import android.os.Looper
import androidx.media3.common.MediaItem
import com.zirakusa.stone.nativeapp.playback.StonePlaybackService
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Starts the real playback service and plays a short WAV: catches crashes at "play". */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlaybackServiceSmokeTest {
    private fun wav(file: File) {
        val samples = 44_100 / 2
        val data = ByteBuffer.allocate(samples * 2).order(ByteOrder.LITTLE_ENDIAN)
        repeat(samples) { data.putShort((Math.sin(it / 10.0) * 8000).toInt().toShort()) }
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + samples * 2); put("WAVE".toByteArray()); put("fmt ".toByteArray())
            putInt(16); putShort(1); putShort(1); putInt(44_100); putInt(88_200); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(samples * 2)
        }
        file.writeBytes(header.array() + data.array())
    }

    @Test fun serviceStartsAndPlays() {
        val controller = Robolectric.buildService(StonePlaybackService::class.java).create()
        val player = StonePlaybackService.currentPlayer()
        assertNotNull(player)
        val file = File.createTempFile("tone", ".wav").also(::wav)
        player!!.setMediaItem(MediaItem.Builder().setMediaId("local:test").setUri(Uri.fromFile(file)).build())
        player.prepare(); player.play()
        repeat(40) { shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(50)) }
        // The media notification (foreground service) is what a real phone shows on play.
        val service = controller.get()
        val field = StonePlaybackService::class.java.getDeclaredField("session").apply { isAccessible = true }
        val session = field.get(service) as androidx.media3.session.MediaSession
        service.onUpdateNotification(session, true)
        repeat(10) { shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(50)) }
        player.seekToNext(); player.pause()
        shadowOf(Looper.getMainLooper()).idle()
        controller.destroy()
    }
}
