package com.zirakusa.stone.nativeapp.playback

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.audiofx.AudioEffect
import android.media.AudioManager
import android.os.Build
import android.util.Log
import androidx.media3.common.Player
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Read-only audio tracing used while investigating volume changes.
 *
 * This class deliberately does not request audio focus, change a stream volume,
 * create an effect, or touch the player. It only takes a point-in-time snapshot
 * and writes a before/after pair to logcat. The tag is intentionally stable so
 * a device capture can be filtered with `adb logcat -s StoneAudio`.
 */
data class AudioDiagnosticSnapshot(
    val timestampMs: Long,
    val streamVolume: Int,
    val streamMaxVolume: Int,
    val streamMinVolume: Int,
    val streamPercent: Int,
    val musicActive: Boolean,
    val audioManagerMode: Int,
    val outputDevices: String,
    val routedMediaDevices: String,
    val activePlaybackConfigurations: String,
    val stoneVolumePercent: Int,
    val lastAppliedNativeLevel: Int,
    val playerVolume: Float?,
    val requestedAudioSessionId: Int,
    val reportedAudioSessionId: Int,
    val playerIdentity: Int,
    val playerType: String,
    val audioAttributes: String,
    val audioFocusPolicy: String,
    val playbackState: Int,
    val isPlaying: Boolean,
    val playWhenReady: Boolean,
    val mediaItemCount: Int,
    val effects: String,
    val availableSystemEffects: String,
)

object AudioDiagnostics {
    const val TAG = "StoneAudio"

    private val lock = Any()
    private var lastSnapshot: AudioDiagnosticSnapshot? = null

    data class State(
        val player: Player? = null,
        val requestedAudioSessionId: Int = 0,
        val reportedAudioSessionId: Int = 0,
        val stoneVolumePercent: Int = -1,
        val lastAppliedNativeLevel: Int = -1,
        val audioFocusPolicy: String = "no determinado",
        val effects: String = "no inspeccionados",
    )

    fun capture(context: Context, state: State = State(), includeSystemEffects: Boolean = false): AudioDiagnosticSnapshot {
        val audio = context.getSystemService(AudioManager::class.java)
        val streamVolume = runCatching { audio?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: -1 }.getOrDefault(-1)
        val streamMax = runCatching { audio?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: -1 }.getOrDefault(-1)
        val streamMin = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching { audio?.getStreamMinVolume(AudioManager.STREAM_MUSIC) ?: 0 }.getOrDefault(0)
        } else 0
        val percent = if (streamMax > streamMin && streamVolume >= streamMin) {
            ((streamVolume - streamMin) * 100f / (streamMax - streamMin)).roundToInt().coerceIn(0, 100)
        } else -1
        val player = state.player
        val attributes = player?.audioAttributes?.let {
            "usage=${it.usage},content=${it.contentType},flags=0x${it.flags.toString(16)}"
        } ?: "ninguno"
        return AudioDiagnosticSnapshot(
            timestampMs = System.currentTimeMillis(),
            streamVolume = streamVolume,
            streamMaxVolume = streamMax,
            streamMinVolume = streamMin,
            streamPercent = percent,
            musicActive = runCatching { audio?.isMusicActive == true }.getOrDefault(false),
            audioManagerMode = runCatching { audio?.mode ?: -1 }.getOrDefault(-1),
            outputDevices = outputDevices(audio),
            routedMediaDevices = routedMediaDevices(audio),
            activePlaybackConfigurations = activePlaybackConfigurations(audio),
            stoneVolumePercent = state.stoneVolumePercent,
            lastAppliedNativeLevel = state.lastAppliedNativeLevel,
            playerVolume = player?.volume,
            requestedAudioSessionId = state.requestedAudioSessionId,
            reportedAudioSessionId = state.reportedAudioSessionId,
            playerIdentity = player?.let { System.identityHashCode(it) } ?: 0,
            playerType = player?.javaClass?.simpleName ?: "ninguno",
            audioAttributes = attributes,
            audioFocusPolicy = state.audioFocusPolicy,
            playbackState = player?.playbackState ?: -1,
            isPlaying = player?.isPlaying == true,
            playWhenReady = player?.playWhenReady == true,
            mediaItemCount = player?.mediaItemCount ?: 0,
            effects = state.effects,
            availableSystemEffects = if (includeSystemEffects) availableSystemEffects() else "no consultados",
        )
    }

    /** Logs a complete before/after comparison. No audio API with side effects is called here. */
    fun log(
        context: Context,
        event: String,
        before: AudioDiagnosticSnapshot? = null,
        state: State = State(),
        includeSystemEffects: Boolean = false,
    ): AudioDiagnosticSnapshot {
        val after = capture(context, state, includeSystemEffects)
        val previous = before ?: synchronized(lock) { lastSnapshot }
        Log.i(TAG, "event=$event\n  before=${format(previous)}\n  after=${format(after)}")
        synchronized(lock) { lastSnapshot = after }
        return after
    }

    fun reset() {
        synchronized(lock) { lastSnapshot = null }
    }

    private fun format(snapshot: AudioDiagnosticSnapshot?): String = snapshot?.let {
        "time=${it.timestampMs}, stream=${it.streamVolume}/${it.streamMaxVolume} " +
            "(${it.streamPercent}%), stone=${it.stoneVolumePercent}%, " +
            "nativeTarget=${it.lastAppliedNativeLevel}, playerVol=${it.playerVolume?.let { value ->
                String.format(Locale.US, "%.3f", value)
            } ?: "null"}, session=${it.requestedAudioSessionId}/reported=${it.reportedAudioSessionId}, " +
            "player=${it.playerType}#${it.playerIdentity}, attrs=${it.audioAttributes}, " +
            "focus=${it.audioFocusPolicy}, state=${it.playbackState}, playing=${it.isPlaying}, " +
            "ready=${it.playWhenReady}, items=${it.mediaItemCount}, musicActive=${it.musicActive}, " +
            "audioMode=${it.audioManagerMode}, route=[${it.outputDevices}], effects=[${it.effects}], " +
            "mediaRoute=[${it.routedMediaDevices}], activePlayback=[${it.activePlaybackConfigurations}], " +
            "availableEffects=[${it.availableSystemEffects}]"
    } ?: "none"

    private fun outputDevices(audio: AudioManager?): String {
        val devices = runCatching {
            audio?.getDevices(AudioManager.GET_DEVICES_OUTPUTS).orEmpty().toList()
        }.getOrDefault(emptyList())
        if (devices.isEmpty()) return "ninguno"
        return devices.joinToString("|") { device ->
            val type = when (device.type) {
                AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "builtin_speaker"
                AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "builtin_earpiece"
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "bluetooth_a2dp"
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "bluetooth_sco"
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "wired_headphones"
                AudioDeviceInfo.TYPE_WIRED_HEADSET -> "wired_headset"
                AudioDeviceInfo.TYPE_USB_DEVICE -> "usb_device"
                AudioDeviceInfo.TYPE_USB_HEADSET -> "usb_headset"
                else -> "type_${device.type}"
            }
            val name = device.productName?.toString()?.takeIf { it.isNotBlank() } ?: "sin_nombre"
            "$type:$name"
        }
    }

    private fun routedMediaDevices(audio: AudioManager?): String {
        val devices = runCatching {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || audio == null) emptyList()
            else audio.getAudioDevicesForAttributes(
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build())
        }.getOrDefault(emptyList())
        return devices.takeIf { it.isNotEmpty() }?.let { deviceSummary(it) } ?: "ninguna"
    }

    private fun activePlaybackConfigurations(audio: AudioManager?): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return "no_disponible"
        val configurations = runCatching { audio?.getActivePlaybackConfigurations().orEmpty() }
            .getOrDefault(emptyList())
        return configurations.takeIf { it.isNotEmpty() }?.mapIndexed { index, configuration ->
            runCatching {
                "player#$index,usage=${configuration.audioAttributes.usage}," +
                    "content=${configuration.audioAttributes.contentType}"
            }.getOrElse { "configuracion_error" }
        }?.joinToString("|") ?: "ninguna"
    }

    private fun deviceSummary(devices: Collection<AudioDeviceInfo>): String =
        devices.joinToString("|") { device ->
            val type = when (device.type) {
                AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "builtin_speaker"
                AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "builtin_earpiece"
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "bluetooth_a2dp"
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "bluetooth_sco"
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "wired_headphones"
                AudioDeviceInfo.TYPE_WIRED_HEADSET -> "wired_headset"
                AudioDeviceInfo.TYPE_USB_DEVICE -> "usb_device"
                AudioDeviceInfo.TYPE_USB_HEADSET -> "usb_headset"
                else -> "type_${device.type}"
            }
            val name = device.productName?.toString()?.takeIf { it.isNotBlank() } ?: "sin_nombre"
            "$type:$name"
        }

    private fun availableSystemEffects(): String = runCatching {
        AudioEffect.queryEffects()?.joinToString("|") { descriptor ->
            "${descriptor.name}:${descriptor.implementor}"
        }?.takeIf { it.isNotBlank() } ?: "ninguno"
    }.getOrElse { "error:${it.javaClass.simpleName}" }
}
