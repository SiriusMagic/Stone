package com.zirakusa.stone.nativeapp.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.onClick
import android.content.Intent
import com.zirakusa.stone.nativeapp.R
import com.zirakusa.stone.nativeapp.feature.player.sheets.SleepTimerContent
import com.zirakusa.stone.nativeapp.data.persistence.StoneSettingsStore
import com.zirakusa.stone.nativeapp.playback.control.AudioMode
import com.zirakusa.stone.nativeapp.playback.control.HardwareAcceleration
import com.zirakusa.stone.nativeapp.playback.control.PlaybackViewModel
import com.zirakusa.stone.nativeapp.ui.theme.StonePalette

@Composable internal fun AudioOutputDetail() {
    val context = LocalContext.current
    val stoneResources = androidx.compose.ui.platform.LocalResources.current
    val route = remember(context) {
        val manager = context.getSystemService(android.media.AudioManager::class.java)
        val devices = manager?.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS).orEmpty()
        val external = setOf(android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES, android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET,
            android.media.AudioDeviceInfo.TYPE_USB_DEVICE, android.media.AudioDeviceInfo.TYPE_USB_HEADSET)
        val device = devices.firstOrNull { it.type in external }
            ?: devices.firstOrNull { it.type == android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
            ?: devices.firstOrNull()
        device?.productName?.toString()?.takeIf { it.isNotBlank() } ?: when (device?.type) {
            android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth"
            android.media.AudioDeviceInfo.TYPE_USB_DEVICE,
            android.media.AudioDeviceInfo.TYPE_USB_HEADSET -> "USB"
            android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Altavoz del teléfono"
            else -> stoneResources.getString(R.string.not_available)
        }
    }
    Text(stringResource(R.string.audio_output_intro), color = StonePalette.Muted,
        modifier = Modifier.padding(bottom = 10.dp))
    SettingsGroup {
        SettingsRow(Icons.Default.Speaker, R.string.audio_output_current, route) {}
    }
    Button(onClick = {
        runCatching { context.startActivity(Intent(android.provider.Settings.ACTION_SOUND_SETTINGS)) }
    }, modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 12.dp)) {
        Icon(Icons.Default.Settings, null)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.audio_output_open_settings))
    }
}

@Composable internal fun PlaybackDetail(store: StoneSettingsStore?, playback: PlaybackViewModel?,
    openAudioMode: () -> Unit, openHardwareAcceleration: () -> Unit) {
    var config by remember { mutableStateOf(store?.read()) }
    val playbackState = playback?.state?.collectAsState()?.value
    Text(stringResource(R.string.playback_section_mode), color = StonePalette.Yellow,
        fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 4.dp, bottom = 6.dp))
    SettingsGroup {
        PlaybackModeRow(playbackState?.audioMode ?: AudioMode.BALANCED, openAudioMode)
        HardwareAccelerationRow(playbackState?.hardwareAcceleration ?: HardwareAcceleration.AUTOMATIC,
            openHardwareAcceleration)
    }
    Text(stringResource(R.string.playback_section_behavior), color = StonePalette.Yellow,
        fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 18.dp, bottom = 6.dp))
    SettingsGroup {
        ToggleSetting(R.string.play_with_others, R.string.play_with_others_hint, config?.playWithOthers == true) {
            playback?.setPlayWithOthers(it); config = store?.read()
        }
        TransitionSettings(store)
    }
}

@Composable internal fun HardwareAccelerationRow(mode: HardwareAcceleration, open: () -> Unit) {
    val label = stringResource(when (mode) {
        HardwareAcceleration.AUTOMATIC -> R.string.accel_automatic
        HardwareAcceleration.DISABLED -> R.string.accel_disabled
        HardwareAcceleration.DECODING -> R.string.accel_decoding
        HardwareAcceleration.FULL -> R.string.accel_full
        HardwareAcceleration.ADAPTIVE -> R.string.accel_adaptive
    })
    Row(Modifier.fillMaxWidth().heightIn(min = 82.dp).clickable(onClick = open),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Tune, null, tint = StonePalette.Yellow, modifier = Modifier.size(26.dp))
        Column(Modifier.weight(1f).padding(horizontal = 18.dp)) {
            Text(stringResource(R.string.hardware_acceleration), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(label, color = StonePalette.Muted, fontSize = 14.sp)
        }
        Icon(Icons.Default.ChevronRight, null, tint = StonePalette.Muted)
    }
}

@Composable internal fun PlaybackModeRow(mode: AudioMode, open: () -> Unit) {
    val label = stringResource(when (mode) {
        AudioMode.BATTERY -> R.string.audio_mode_battery
        AudioMode.BALANCED -> R.string.audio_mode_balanced
        AudioMode.HIFI -> R.string.audio_mode_hifi
        AudioMode.SLEEPY -> R.string.audio_mode_sleepy
        AudioMode.CUSTOM -> R.string.audio_mode_custom
    })
    Row(Modifier.fillMaxWidth().heightIn(min = 82.dp).clickable(onClick = open),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(if (mode == AudioMode.BATTERY) Icons.Default.BatteryFull else Icons.Default.Tune,
            null, tint = StonePalette.Yellow, modifier = Modifier.size(26.dp))
        Column(Modifier.weight(1f).padding(horizontal = 18.dp)) {
            Text(stringResource(R.string.audio_mode), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(label, color = StonePalette.Muted, fontSize = 14.sp)
        }
        Icon(Icons.Default.ChevronRight, null, tint = StonePalette.Muted)
    }
}

@Composable internal fun AudioModeDetail(playback: PlaybackViewModel?) {
    val current = playback?.state?.collectAsState()?.value?.audioMode ?: AudioMode.BALANCED
    Text(stringResource(R.string.audio_mode_intro), color = StonePalette.Muted,
        modifier = Modifier.padding(bottom = 10.dp))
    AudioMode.entries.forEach { mode ->
        val (title, detail, icon) = when (mode) {
            AudioMode.BATTERY -> Triple(R.string.audio_mode_battery, R.string.audio_mode_battery_info,
                Icons.Default.BatteryFull)
            AudioMode.BALANCED -> Triple(R.string.audio_mode_balanced, R.string.audio_mode_balanced_info,
                Icons.Default.Balance)
            AudioMode.HIFI -> Triple(R.string.audio_mode_hifi, R.string.audio_mode_hifi_info,
                Icons.Default.GraphicEq)
            AudioMode.SLEEPY -> Triple(R.string.audio_mode_sleepy, R.string.audio_mode_sleepy_info,
                Icons.Default.Bedtime)
            AudioMode.CUSTOM -> Triple(R.string.audio_mode_custom, R.string.audio_mode_custom_info,
                Icons.Default.Tune)
        }
            Row(Modifier.fillMaxWidth().clickable {
                if (mode == AudioMode.BATTERY || mode == AudioMode.SLEEPY) playback?.setPlaybackProfile(
                    mode, HardwareAcceleration.ADAPTIVE)
                else playback?.setAudioMode(mode)
            }
            .padding(vertical = 14.dp), verticalAlignment = Alignment.Top) {
            Icon(icon, null, tint = StonePalette.Yellow, modifier = Modifier.size(25.dp))
            Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                Text(stringResource(title), fontWeight = FontWeight.Bold)
                Text(stringResource(detail), color = StonePalette.Muted, fontSize = 13.sp)
            }
            Icon(if (current == mode) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                null, tint = if (current == mode) StonePalette.Yellow else StonePalette.Muted)
        }
    }
}

@Composable internal fun HardwareAccelerationDetail(playback: PlaybackViewModel?) {
    Text(stringResource(R.string.hardware_acceleration_intro), color = StonePalette.Muted,
        modifier = Modifier.padding(bottom = 10.dp))
    val current = playback?.state?.collectAsState()?.value?.hardwareAcceleration
        ?: HardwareAcceleration.AUTOMATIC
    HardwareAcceleration.entries.forEach { mode ->
        val (title, detail, icon) = when (mode) {
            HardwareAcceleration.AUTOMATIC -> Triple(R.string.accel_automatic, R.string.accel_automatic_info, Icons.Default.Settings)
            HardwareAcceleration.DISABLED -> Triple(R.string.accel_disabled, R.string.accel_disabled_info, Icons.Default.Block)
            HardwareAcceleration.DECODING -> Triple(R.string.accel_decoding, R.string.accel_decoding_info, Icons.Default.Memory)
            HardwareAcceleration.FULL -> Triple(R.string.accel_full, R.string.accel_full_info, Icons.Default.Speed)
            HardwareAcceleration.ADAPTIVE -> Triple(R.string.accel_adaptive, R.string.accel_adaptive_info, Icons.Default.BatteryFull)
        }
        Row(Modifier.fillMaxWidth().clickable { playback?.setHardwareAcceleration(mode) }
            .padding(vertical = 13.dp), verticalAlignment = Alignment.Top) {
            Icon(icon, null, tint = StonePalette.Yellow, modifier = Modifier.size(25.dp))
            Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                Text(stringResource(title), fontWeight = FontWeight.Bold)
                Text(stringResource(detail), color = StonePalette.Muted, fontSize = 13.sp)
            }
            Icon(if (current == mode) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                null, tint = if (current == mode) StonePalette.Yellow else StonePalette.Muted)
        }
    }
    Text(stringResource(R.string.hardware_acceleration_note), color = StonePalette.Muted,
        fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp, bottom = 20.dp))
}

@Composable internal fun SleepDetail(playback: PlaybackViewModel?, onClose: () -> Unit) {
    val playbackState = playback?.state?.collectAsState()?.value
    if (playback != null && playbackState != null) SleepTimerContent(playbackState, playback, onClose)
}
