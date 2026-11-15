package com.zirakusa.stone.nativeapp.feature.library.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zirakusa.stone.nativeapp.R
import com.zirakusa.stone.nativeapp.data.persistence.StoneSettingsStore
import com.zirakusa.stone.nativeapp.playback.AudioLevels
import com.zirakusa.stone.nativeapp.playback.AudioMode
import com.zirakusa.stone.nativeapp.playback.PlaybackViewModel
import com.zirakusa.stone.nativeapp.ui.theme.StonePalette
import kotlin.math.roundToInt

/** Ajustes › Niveles de audio: volume ceiling and gain of each playback mode. */
@Composable internal fun AudioLevelSettings(store: StoneSettingsStore?, playback: PlaybackViewModel?) {
    val current = playback?.state?.collectAsState()?.value?.audioMode
    var revision by remember { mutableIntStateOf(0) }
    Text(stringResource(R.string.audio_levels_intro), color = StonePalette.Muted)
    key(revision) {
        AudioLevels.adjustable.forEach { mode ->
            var max by remember { mutableIntStateOf(mode.maxVolumePercent) }
            var gain by remember { mutableIntStateOf((mode.gain * 100).roundToInt()) }
            fun apply() {
                AudioLevels.set(store, mode, max, gain / 100f)
                // Re-applying the active mode pushes the new ceiling and gain to the player.
                if (current == mode) playback?.setAudioMode(mode)
            }
            Text(stringResource(modeLabel(mode)) + if (current == mode) " ●" else "", color = StonePalette.Yellow,
                fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 20.dp))
            Text(stringResource(R.string.custom_audio_max, max), Modifier.padding(top = 6.dp))
            Slider(max.toFloat(), { max = (it / 10).roundToInt() * 10 }, valueRange = 10f..300f, onValueChangeFinished = ::apply)
            Text(stringResource(R.string.custom_audio_gain, gain))
            Slider(gain.toFloat(), { gain = it.roundToInt().coerceAtLeast(1) }, valueRange = 1f..100f, onValueChangeFinished = ::apply)
        }
    }
    Text(stringResource(R.string.audio_levels_battery), color = StonePalette.Muted, fontSize = 12.sp,
        modifier = Modifier.padding(top = 12.dp))
    OutlinedButton(onClick = {
        AudioLevels.reset(store); revision++
        current?.let { playback?.setAudioMode(it) }
    }, Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 12.dp)) { Text(stringResource(R.string.audio_levels_reset)) }
}

private fun modeLabel(mode: AudioMode) = when (mode) {
    AudioMode.BATTERY -> R.string.audio_mode_battery
    AudioMode.BALANCED -> R.string.audio_mode_balanced
    AudioMode.HIFI -> R.string.audio_mode_hifi
    AudioMode.SLEEPY -> R.string.audio_mode_sleepy
    AudioMode.CUSTOM -> R.string.audio_mode_custom
}
