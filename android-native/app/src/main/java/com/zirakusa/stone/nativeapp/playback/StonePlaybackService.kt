package com.zirakusa.stone.nativeapp.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.provider.Settings
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.common.util.UnstableApi
import com.zirakusa.stone.nativeapp.BuildConfig
import com.zirakusa.stone.nativeapp.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Sole owner of playback; the UI only connects with a MediaController. */
class StonePlaybackService : MediaSessionService() {
    data class VolumeUiState(val percent: Int = 100, val revision: Long = 0,
        val visible: Boolean = false, val maxPercent: Int = AudioMode.BALANCED.maxVolumePercent)
    data class AudioDiagnosticUiState(val loudnessEnhancerDisabled: Boolean = false, val revision: Long = 0)
    companion object {
        @Volatile var instance: StonePlaybackService? = null
        @Volatile var playbackAudioSessionId: Int = 0; private set
        @Volatile var reportedAudioSessionId: Int = 0; private set
        @Volatile private var activePlayer: ExoPlayer? = null
        @Volatile private var volumePercent: Int = 100
        @Volatile private var maxVolumePercent: Int = AudioMode.BALANCED.maxVolumePercent
        @Volatile private var lastAppliedNativeLevel: Int = -1
        @Volatile private var revision: Long = 0
        @Volatile private var diagnosticBoostDisabled = false
        private var volumeBoost: PlaybackVolumeBoost? = null
        private val mutableVolumeUi = MutableStateFlow(VolumeUiState())
        private val mutableAudioDiagnosticUi = MutableStateFlow(AudioDiagnosticUiState())
        val volumeUi = mutableVolumeUi.asStateFlow()
        val audioDiagnosticUi = mutableAudioDiagnosticUi.asStateFlow()

        val currentVolumePercent: Int get() = volumePercent
        val currentLastAppliedNativeLevel: Int get() = lastAppliedNativeLevel

        private fun effectSessionId(): Int =
            reportedAudioSessionId.takeIf { it > 0 } ?: playbackAudioSessionId

        fun diagnosticState(
            player: Player? = activePlayer,
            audioFocusPolicy: String = "Media3: handleAudioFocus=true (servicio)",
        ) = AudioDiagnostics.State(
            player = player,
            requestedAudioSessionId = playbackAudioSessionId,
            reportedAudioSessionId = reportedAudioSessionId,
            stoneVolumePercent = volumePercent,
            lastAppliedNativeLevel = lastAppliedNativeLevel,
            audioFocusPolicy = audioFocusPolicy,
            effects = if (diagnosticBoostDisabled) {
                "LoudnessEnhancer:disabled_for_diagnostic"
            } else volumeBoost?.diagnostics() ?: "LoudnessEnhancer:none",
        )

        /** Diagnostic-only A/B switch. It is intentionally not persisted. */
        fun setDiagnosticBoostDisabled(disabled: Boolean) {
            if (!BuildConfig.AUDIO_DIAGNOSTICS_ENABLED) return
            val service = instance
            val before = service?.let {
                AudioDiagnostics.capture(it, diagnosticState(), includeSystemEffects = true)
            }
            diagnosticBoostDisabled = disabled
            if (disabled) {
                volumeBoost?.disableForDiagnostics()
            } else if (service != null) {
                if (volumeBoost == null) volumeBoost = PlaybackVolumeBoost()
                volumeBoost?.attach(effectSessionId())
                volumeBoost?.setPercent(volumePercent, maxVolumePercent > 100)
            }
            val nextRevision = mutableAudioDiagnosticUi.value.revision + 1
            mutableAudioDiagnosticUi.value = AudioDiagnosticUiState(disabled, nextRevision)
            service?.let {
                AudioDiagnostics.log(it, "DiagnosticAB.loudnessEnhancerDisabled=$disabled", before,
                    diagnosticState(), includeSystemEffects = true)
            }
        }

        fun setVolumePercent(percent: Int, showOverlay: Boolean = true) {
            val service = instance
            val before = service?.let {
                AudioDiagnostics.capture(it, diagnosticState(), includeSystemEffects = false)
            }
            val requestedPercent = percent.coerceIn(0, maxVolumePercent)
            service?.let {
                val audioManager = service.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val range = audioManager.streamMusicRange()
                val targetNativeLevel = if (requestedPercent <= 100) {
                    range.percentToLevel(requestedPercent)
                } else range.maxLevel

                // On the way down, remove the extra gain before changing the
                // Android level. This prevents a stale boost from surviving a
                // 300% -> native-volume transition.
                if (requestedPercent <= 100) {
                    volumeBoost?.setPercent(requestedPercent, allowBoost = false)
                }

                // Do not request the system UI: Android owns the actual stream
                // volume while Stone only presents its own optional overlay.
                lastAppliedNativeLevel = targetNativeLevel
                if (audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) != targetNativeLevel) {
                    runCatching {
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetNativeLevel,
                            AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE)
                    }.onFailure {
                        Log.w(AudioDiagnostics.TAG, "No se pudo aplicar STREAM_MUSIC=$targetNativeLevel", it)
                    }
                }

                val actualNativeLevel = runCatching {
                    audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                }.getOrDefault(targetNativeLevel).coerceIn(range.minLevel, range.maxLevel)
                lastAppliedNativeLevel = actualNativeLevel
                val actualNativePercent = range.levelToPercent(actualNativeLevel)
                // Above 100% is valid only while Android is actually at its
                // maximum. If the device rejected the write, do not amplify a
                // lower hardware volume unexpectedly.
                volumePercent = if (requestedPercent > 100 && actualNativeLevel >= range.maxLevel) {
                    requestedPercent
                } else actualNativePercent
                if (requestedPercent > 100 && actualNativeLevel >= range.maxLevel) {
                    volumeBoost?.setPercent(volumePercent, maxVolumePercent > 100)
                } else {
                    volumeBoost?.setPercent(volumePercent, allowBoost = false)
                }
            } ?: run {
                // The service is normally alive before the UI can edit volume.
                // Keep this only as a short-lived request until Android can be
                // read; it is never persisted as a second volume source.
                volumePercent = requestedPercent
            }
            if (activePlayer?.volume != 1f) activePlayer?.volume = 1f
            revision++
            mutableVolumeUi.value = VolumeUiState(volumePercent, revision, showOverlay, maxVolumePercent)
            service?.let {
                AudioDiagnostics.log(it, "Stone.setVolumePercent(requested=$percent,overlay=$showOverlay)",
                    before = before, state = diagnosticState(), includeSystemEffects = false)
            }
        }

        /** Reconciles Stone with Android after the user changed media volume outside Stone. */
        fun syncWithAndroidVolume(reason: String = "external") {
            val service = instance ?: return
            val before = AudioDiagnostics.capture(service, diagnosticState(), includeSystemEffects = false)
            val audioManager = service.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val range = audioManager.streamMusicRange()
            val forceRouteReconcile = reason.startsWith("audio-devices")
            if (!forceRouteReconcile && lastAppliedNativeLevel >= 0 && current == lastAppliedNativeLevel) {
                // A route/session callback can arrive without a level change.
                // Reapply only the already selected policy; no stream write is made.
                if (volumePercent > 100 && current >= range.maxLevel) {
                    volumeBoost?.setPercent(volumePercent, maxVolumePercent > 100)
                }
                AudioDiagnostics.log(service, "Stone.syncWithAndroidVolume(no-change,reason=$reason)", before,
                    diagnosticState(), includeSystemEffects = false)
                return
            }
            // If the route changed but still reports the Android maximum, keep
            // an intentional Stone boost. A real external reduction will be
            // below the maximum and will collapse Stone to the native range.
            if (current >= range.maxLevel && volumePercent > 100) {
                lastAppliedNativeLevel = current
                volumeBoost?.setPercent(volumePercent, maxVolumePercent > 100)
                AudioDiagnostics.log(service,
                    "Stone.syncWithAndroidVolume(route-max-preserved,reason=$reason)", before,
                    diagnosticState(), includeSystemEffects = false)
                return
            }
            val percent = range.levelToPercent(current)
            setVolumePercent(percent, showOverlay = false)
            AudioDiagnostics.log(service, "Stone.syncWithAndroidVolume(changed=$current,reason=$reason)", before,
                diagnosticState(), includeSystemEffects = false)
        }

        fun setMaxVolumePercent(maxPercent: Int) {
            val service = instance
            val before = service?.let {
                AudioDiagnostics.capture(it, diagnosticState(), includeSystemEffects = false)
            }
            maxVolumePercent = maxPercent.coerceIn(100, 300)
            if (volumePercent > maxVolumePercent) {
                setVolumePercent(maxVolumePercent, showOverlay = false)
            } else {
                // A mode change can cross the boost policy without changing the volume.
                volumeBoost?.setPercent(volumePercent, maxVolumePercent > 100)
                mutableVolumeUi.value = mutableVolumeUi.value.copy(maxPercent = maxVolumePercent)
            }
            service?.let {
                AudioDiagnostics.log(it, "Stone.setMaxVolumePercent(requested=$maxPercent)", before,
                    diagnosticState(), includeSystemEffects = false)
            }
        }

        fun hasActivePlayback(): Boolean = activePlayer?.mediaItemCount?.let { it > 0 } == true

        fun hideVolumeOverlay(forRevision: Long) {
            if (mutableVolumeUi.value.revision == forRevision)
                mutableVolumeUi.value = mutableVolumeUi.value.copy(visible = false)
        }
    }
    private var session: MediaSession? = null
    private var ecoEngine: StoneEcoEngine? = null
    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
            logAudio("AudioManager.onAudioDevicesAdded(count=${addedDevices.size})", includeSystemEffects = true)
            syncWithAndroidVolume("audio-devices-added")
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
            logAudio("AudioManager.onAudioDevicesRemoved(count=${removedDevices.size})", includeSystemEffects = true)
            syncWithAndroidVolume("audio-devices-removed")
        }
    }
    private val audioPlaybackCallback = object : AudioManager.AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(
            configs: MutableList<android.media.AudioPlaybackConfiguration>,
        ) {
            logAudio("AudioManager.onPlaybackConfigChanged(count=${configs.size})")
        }
    }
    private val volumeObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean, uri: android.net.Uri?) {
            val event = "Settings.volume_music.onChange(uri=${uri?.lastPathSegment ?: "unknown"})"
            logAudio(event)
            // This callback is the bridge for physical buttons and the Android
            // volume panel. The guard in syncWithAndroidVolume prevents a
            // write-back loop after Stone changes the stream itself.
            syncWithAndroidVolume(event)
        }
    }

    private fun logAudio(
        event: String,
        before: AudioDiagnosticSnapshot? = null,
        includeSystemEffects: Boolean = false,
        player: Player? = session?.player,
        audioFocusPolicy: String = "Media3: handleAudioFocus=true (servicio)",
    ) {
        AudioDiagnostics.log(this, event, before,
            diagnosticState(player, audioFocusPolicy), includeSystemEffects)
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        instance = this
        reportedAudioSessionId = 0
        AudioDiagnostics.reset()
        val before = AudioDiagnostics.capture(this, diagnosticState(player = null), includeSystemEffects = true)
        if (activePlayer != null) {
            Log.w(AudioDiagnostics.TAG, "service.onCreate found an existing active player=" +
                System.identityHashCode(activePlayer))
        }
        val player = ExoPlayer.Builder(this).build()
        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                reportedAudioSessionId = audioSessionId
                if (audioSessionId > 0) {
                    volumeBoost?.attach(audioSessionId)
                    if (!diagnosticBoostDisabled) {
                        volumeBoost?.setPercent(volumePercent, maxVolumePercent > 100)
                    }
                }
                logAudio("ExoPlayer.onAudioSessionIdChanged($audioSessionId)",
                    includeSystemEffects = true, player = player)
            }

            override fun onAudioAttributesChanged(audioAttributes: AudioAttributes) {
                logAudio("ExoPlayer.onAudioAttributesChanged(usage=${audioAttributes.usage}," +
                    "content=${audioAttributes.contentType},flags=${audioAttributes.flags})",
                    player = player)
            }

            override fun onVolumeChanged(volume: Float) {
                logAudio("ExoPlayer.onVolumeChanged($volume)", player = player)
            }

            override fun onDeviceInfoChanged(deviceInfo: androidx.media3.common.DeviceInfo) {
                logAudio("ExoPlayer.onDeviceInfoChanged", includeSystemEffects = true, player = player)
            }

            override fun onDeviceVolumeChanged(volume: Int, muted: Boolean) {
                logAudio("ExoPlayer.onDeviceVolumeChanged(volume=$volume,muted=$muted)", player = player)
                syncWithAndroidVolume("ExoPlayer.onDeviceVolumeChanged")
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                logAudio("ExoPlayer.onPlaybackStateChanged($playbackState)", player = player)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                logAudio("ExoPlayer.onIsPlayingChanged($isPlaying)", player = player)
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                logAudio("ExoPlayer.onPlayWhenReadyChanged(ready=$playWhenReady,reason=$reason)",
                    player = player)
            }

            override fun onPlaybackSuppressionReasonChanged(playbackSuppressionReason: Int) {
                logAudio("ExoPlayer.onPlaybackSuppressionReasonChanged($playbackSuppressionReason)",
                    player = player)
            }
        })
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        playbackAudioSessionId = audioManager.generateAudioSessionId()
        if (playbackAudioSessionId > 0) player.setAudioSessionId(playbackAudioSessionId)
        player.setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
        player.setHandleAudioBecomingNoisy(true)
        player.setWakeMode(C.WAKE_MODE_LOCAL)
        activePlayer = player
        AudioDiagnostics.log(this, "service.player-created", before,
            diagnosticState(player), includeSystemEffects = true)
        // Android is the source of truth at service start. Never restore a saved Stone volume.
        val volumeRange = audioManager.streamMusicRange()
        val initialNativeLevel = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        lastAppliedNativeLevel = initialNativeLevel
        volumePercent = volumeRange.levelToPercent(initialNativeLevel)
        ecoEngine = StoneEcoEngine(applicationContext).also {
            it.apply(AudioMode.BALANCED, player)
        }
        logAudio("service.eco-engine-applied", player = player)
        volumeBoost = PlaybackVolumeBoost().apply { attach(playbackAudioSessionId) }
        setVolumePercent(volumePercent, showOverlay = false)
        if (diagnosticBoostDisabled) volumeBoost?.disableForDiagnostics()
        val openApp = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        session = MediaSession.Builder(this, player).setSessionActivity(openApp).build()
        registerAudioDiagnostics()
        logAudio("service.media-session-created", includeSystemEffects = true, player = player)
    }
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        logAudio("MediaSession.onGetSession(package=${controllerInfo.packageName})", player = session?.player)
        return session
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        logAudio("MediaSessionService.onStartCommand(action=${intent?.action ?: "null"},startId=$startId)")
        syncWithAndroidVolume("MediaSessionService.onStartCommand")
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        logAudio("service.onTaskRemoved(playing=${player?.isPlaying},items=${player?.mediaItemCount})", player = player)
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        val before = AudioDiagnostics.capture(this, diagnosticState(), includeSystemEffects = true)
        unregisterAudioDiagnostics()
        session?.run { player.release(); release() }
        session = null
        activePlayer = null
        volumeBoost?.release()
        volumeBoost = null
        ecoEngine = null
        instance = null
        playbackAudioSessionId = 0
        reportedAudioSessionId = 0
        AudioDiagnostics.log(this, "service.onDestroy", before,
            diagnosticState(player = null), includeSystemEffects = false)
        super.onDestroy()
    }

    fun applyAudioMode(mode: AudioMode) {
        val before = AudioDiagnostics.capture(this, diagnosticState(), includeSystemEffects = false)
        setMaxVolumePercent(mode.maxVolumePercent)
        session?.player?.let { ecoEngine?.apply(mode, it) }
        logAudio("service.applyAudioMode(${mode.key})", before, player = session?.player)
    }

    fun applyHardwareAcceleration(mode: HardwareAcceleration) {
        val before = AudioDiagnostics.capture(this, diagnosticState(), includeSystemEffects = false)
        session?.player?.let { ecoEngine?.applyHardwareAcceleration(mode, it) }
        logAudio("service.applyHardwareAcceleration(${mode.key})", before, player = session?.player)
    }

    private fun registerAudioDiagnostics() {
        val audioManager = getSystemService(AudioManager::class.java) ?: return
        runCatching {
            audioManager.registerAudioDeviceCallback(audioDeviceCallback, Handler(Looper.getMainLooper()))
        }.onFailure { Log.w(AudioDiagnostics.TAG, "No se pudo registrar AudioDeviceCallback", it) }
        runCatching {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                audioManager.registerAudioPlaybackCallback(audioPlaybackCallback,
                    Handler(Looper.getMainLooper()))
            }
        }.onFailure { Log.w(AudioDiagnostics.TAG, "No se pudo registrar AudioPlaybackCallback", it) }
        runCatching {
            contentResolver.registerContentObserver(
                Settings.System.getUriFor("volume_music"), false, volumeObserver)
        }.onFailure { Log.w(AudioDiagnostics.TAG, "No se pudo registrar el observador de volumen", it) }
    }

    private fun unregisterAudioDiagnostics() {
        val audioManager = getSystemService(AudioManager::class.java)
        runCatching { audioManager?.unregisterAudioDeviceCallback(audioDeviceCallback) }
        runCatching {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                audioManager?.unregisterAudioPlaybackCallback(audioPlaybackCallback)
            }
        }
        runCatching { contentResolver.unregisterContentObserver(volumeObserver) }
    }
}
