package com.zirakusa.stone.nativeapp.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.common.util.UnstableApi
import com.zirakusa.stone.nativeapp.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt

/** Sole owner of playback; the UI only connects with a MediaController. */
class StonePlaybackService : MediaSessionService() {
    data class VolumeUiState(val percent: Int = 100, val revision: Long = 0, val visible: Boolean = false)
    companion object {
        @Volatile var instance: StonePlaybackService? = null
        @Volatile var playbackAudioSessionId: Int = 0; private set
        @Volatile private var activePlayer: ExoPlayer? = null
        @Volatile private var volumePercent: Int = 100
        @Volatile private var revision: Long = 0
        private var volumeBoost: PlaybackVolumeBoost? = null
        private val mutableVolumeUi = MutableStateFlow(VolumeUiState())
        val volumeUi = mutableVolumeUi.asStateFlow()

        fun setVolumePercent(percent: Int, showOverlay: Boolean = true) {
            volumePercent = percent.coerceIn(0, 300)
            instance?.let { service ->
                val audioManager = service.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                val nativeLevel = if (volumePercent <= 100) {
                    (max * volumePercent / 100f).roundToInt()
                } else max
                // Do not request the system UI: Stone owns the visible overlay.
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, nativeLevel,
                    AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE)
            }
            activePlayer?.volume = 1f
            volumeBoost?.setPercent(volumePercent)
            revision++
            mutableVolumeUi.value = VolumeUiState(volumePercent, revision, showOverlay)
        }

        fun handleVolumeKey(delta: Int): Boolean {
            setVolumePercent(volumePercent + delta)
            return true
        }

        fun hasActivePlayback(): Boolean = activePlayer?.mediaItemCount?.let { it > 0 } == true

        fun hideVolumeOverlay(forRevision: Long) {
            if (mutableVolumeUi.value.revision == forRevision)
                mutableVolumeUi.value = mutableVolumeUi.value.copy(visible = false)
        }
    }
    private var session: MediaSession? = null
    private var ecoEngine: StoneEcoEngine? = null
    @androidx.annotation.OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        instance = this
        val player = ExoPlayer.Builder(this).build().apply {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            playbackAudioSessionId = audioManager.generateAudioSessionId()
            if (playbackAudioSessionId > 0) setAudioSessionId(playbackAudioSessionId)
            setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
            setHandleAudioBecomingNoisy(true)
            setWakeMode(C.WAKE_MODE_LOCAL)
        }
        activePlayer = player
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (volumePercent <= 100) {
            val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
            volumePercent = (audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) * 100f / max)
                .roundToInt().coerceIn(0, 100)
        }
        ecoEngine = StoneEcoEngine(applicationContext).also {
            it.apply(AudioMode.BALANCED, player)
        }
        volumeBoost = PlaybackVolumeBoost().apply { attach(playbackAudioSessionId) }
        setVolumePercent(volumePercent, showOverlay = false)
        val openApp = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        session = MediaSession.Builder(this, player).setSessionActivity(openApp).build()
    }
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = session
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }
    override fun onDestroy() {
        session?.run { player.release(); release() }
        session = null
        activePlayer = null
        volumeBoost?.release()
        volumeBoost = null
        ecoEngine = null
        instance = null
        playbackAudioSessionId = 0
        super.onDestroy()
    }

    fun applyAudioMode(mode: AudioMode) {
        session?.player?.let { ecoEngine?.apply(mode, it) }
    }

    fun applyHardwareAcceleration(mode: HardwareAcceleration) {
        session?.player?.let { ecoEngine?.applyHardwareAcceleration(mode, it) }
    }
}
