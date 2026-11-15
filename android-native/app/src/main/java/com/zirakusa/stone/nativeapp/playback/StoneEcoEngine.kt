package com.zirakusa.stone.nativeapp.playback

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Lightweight playback policy. It owns no thread, service, timer, decoder or second player. */
class StoneEcoEngine(context: Context) {
    data class Diagnostics(
        val mode: AudioMode = AudioMode.BALANCED,
        val output: String = "Desconocida",
        val usb: Boolean = false,
        val offloadAvailable: Boolean = false,
        val strategy: String = "Ruta normal de Media3"
    )

    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val mutableDiagnostics = MutableStateFlow(Diagnostics())
    val diagnostics = mutableDiagnostics.asStateFlow()

    private var mode = AudioMode.BALANCED
    private var acceleration = HardwareAcceleration.AUTOMATIC

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    fun apply(mode: AudioMode, player: Player) {
        this.mode = mode
        player.setAudioAttributes(
            androidx.media3.common.AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
            true
        )
        // Output always follows Android's active route (headphones, Bluetooth, USB DAC
        // or speaker). Pinning the speaker leaked audio while headphones were connected.
        (player as? ExoPlayer)?.let { exo -> runCatching { exo.setPreferredAudioDevice(null) } }
        applyPowerPolicy(player)
    }

    fun applyHardwareAcceleration(mode: HardwareAcceleration, player: Player) {
        acceleration = mode
        applyPowerPolicy(player)
    }

    /**
     * Offload hands decoding to the audio DSP so the CPU can sleep between buffers. It is
     * requested only when no Stone effect needs the PCM stream (Ahorro has no EQ and no
     * gain above 100 %). Media3 falls back to the normal path when a format, route or
     * video track does not support it, so this never interrupts playback.
     */
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private fun applyPowerPolicy(player: Player) {
        val offload = mode == AudioMode.BATTERY || mode == AudioMode.SLEEPY || (mode != AudioMode.HIFI &&
            (acceleration == HardwareAcceleration.FULL || acceleration == HardwareAcceleration.ADAPTIVE))
        val preferences = TrackSelectionParameters.AudioOffloadPreferences.Builder()
            .setAudioOffloadMode(
                if (offload) TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED
                else TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED
            )
            .setIsGaplessSupportRequired(false)
            .setIsSpeedChangeSupportRequired(false)
            .build()
        runCatching {
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .setAudioOffloadPreferences(preferences).build()
        }
        // Media3 already drops its wake lock while sleeping for offload; keeping LOCAL
        // protects the non-offload fallback from being stopped with the screen off.
        (player as? ExoPlayer)?.let { exo -> runCatching { exo.setWakeMode(C.WAKE_MODE_LOCAL) } }
        val strategy = when {
            mode == AudioMode.BATTERY -> "Ahorro: offload al DSP cuando el formato lo admite, sin efectos"
            mode == AudioMode.SLEEPY -> "Dormilón: ganancia -20 dB (máximo 10 %) y bajo consumo"
            offload -> "Offload solicitado; Media3 vuelve a la ruta normal si no es compatible"
            mode == AudioMode.HIFI -> "Alta fidelidad: PCM de alta resolución en coma flotante y ruta activa del sistema"
            else -> "Equilibrado: ruta activa del sistema con efectos de Stone"
        }
        val output = inspectOutput()
        mutableDiagnostics.value = Diagnostics(mode, output.first, output.second,
            offloadAvailable = offload, strategy = strategy)
    }

    /** Reads the current route only when a mode is applied or diagnostics are requested. */
    fun refreshDiagnostics(mode: AudioMode): Diagnostics {
        val output = inspectOutput()
        val current = mutableDiagnostics.value
        return current.copy(mode = mode, output = output.first, usb = output.second)
            .also { mutableDiagnostics.value = it }
    }

    private fun inspectOutput(): Pair<String, Boolean> {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val device = devices.firstOrNull { it.type in EXTERNAL_OUTPUTS }
            ?: devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
            ?: devices.firstOrNull()
            ?: return "Sin salida detectada" to false
        val usb = device.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
            device.type == AudioDeviceInfo.TYPE_USB_HEADSET
        val name = device.productName?.toString()?.takeIf { it.isNotBlank() }
            ?: when (device.type) {
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth"
                AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Altavoz del teléfono"
                else -> "Salida de audio"
            }
        return name to usb
    }

    private companion object {
        val EXTERNAL_OUTPUTS = setOf(
            AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        )
    }
}
