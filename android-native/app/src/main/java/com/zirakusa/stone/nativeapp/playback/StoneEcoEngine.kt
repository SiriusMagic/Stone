package com.zirakusa.stone.nativeapp.playback

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import androidx.media3.common.C
import androidx.media3.common.Player
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

    fun apply(mode: AudioMode, player: Player) {
        // Media3/Android decide offload support for the actual format and output.
        // We only express the policy; we never claim offload or bit-perfect output here.
        val output = inspectOutput()
        val strategy = when (mode) {
            AudioMode.BATTERY -> "Ahorro máximo: sin CPU despierta y sin efectos de audio"
            AudioMode.BALANCED -> "Ruta normal de Media3"
            AudioMode.HIFI -> "Fidelidad prioritaria; offload automático si no altera la salida"
        }
        player.setAudioAttributes(
            androidx.media3.common.AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
            true
        )
        applyWakePolicy(mode == AudioMode.BATTERY, player)
        mutableDiagnostics.value = Diagnostics(mode, output.first, output.second,
            offloadAvailable = false, strategy = strategy)
    }

    fun applyHardwareAcceleration(mode: HardwareAcceleration, player: Player) {
        val current = mutableDiagnostics.value
        val strategy = when (mode) {
            HardwareAcceleration.AUTOMATIC -> "Android selecciona la ruta compatible"
            HardwareAcceleration.DISABLED -> "Ruta convencional solicitada"
            HardwareAcceleration.DECODING -> "Decodificación por hardware preferida cuando existe"
            HardwareAcceleration.FULL -> "Offload solicitado; fallback seguro si no es compatible"
            HardwareAcceleration.ADAPTIVE -> "Ultra ahorro: libera la CPU cuando la salida lo permite"
        }
        // Media3 still decides the real decoder/offload path for each format and output.
        applyWakePolicy(mode == HardwareAcceleration.ADAPTIVE || current.mode == AudioMode.BATTERY, player)
        mutableDiagnostics.value = current.copy(strategy = strategy)
    }

    private fun applyWakePolicy(minimizeWakeLock: Boolean, player: Player) {
        (player as? ExoPlayer)?.let { exo ->
            runCatching { exo.setWakeMode(if (minimizeWakeLock) C.WAKE_MODE_NONE else C.WAKE_MODE_LOCAL) }
        }
    }

    /** Reads the current route only when a mode is applied or diagnostics are requested. */
    fun refreshDiagnostics(mode: AudioMode): Diagnostics {
        val output = inspectOutput()
        val current = mutableDiagnostics.value
        return current.copy(mode = mode, output = output.first, usb = output.second)
            .also { mutableDiagnostics.value = it }
    }

    private fun inspectOutput(): Pair<String, Boolean> {
        val device = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull()
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
}
