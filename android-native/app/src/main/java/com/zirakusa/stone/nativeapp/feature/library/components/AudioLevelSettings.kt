package com.zirakusa.stone.nativeapp.feature.library.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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

private class ModeCard(val mode: AudioMode, val label: Int, val hint: Int, val icon: ImageVector)

private val cards = listOf(
    ModeCard(AudioMode.SLEEPY, R.string.audio_mode_sleepy, R.string.audio_levels_sleepy_hint, Icons.Default.Bedtime),
    ModeCard(AudioMode.BALANCED, R.string.audio_mode_balanced, R.string.audio_levels_balanced_hint, Icons.Default.Tune),
    ModeCard(AudioMode.HIFI, R.string.audio_mode_hifi, R.string.audio_levels_hifi_hint, Icons.Default.Movie),
    ModeCard(AudioMode.CUSTOM, R.string.audio_mode_custom, R.string.audio_levels_custom_hint, Icons.Default.Equalizer))

/**
 * Ajustes › Niveles de audio: pick a mode (it becomes the active one) and tune its own volume
 * ceiling and gain; each mode keeps its values. The reset icon restores Stone's defaults.
 */
@Composable internal fun AudioLevelSettings(store: StoneSettingsStore?, playback: PlaybackViewModel?) {
    val active = playback?.state?.collectAsState()?.value?.audioMode
    var selected by remember { mutableStateOf(active?.takeIf { it in AudioLevels.adjustable } ?: AudioMode.BALANCED) }
    var revision by remember { mutableIntStateOf(0) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.audio_levels_pick), Modifier.weight(1f), color = StonePalette.Secondary)
        IconButton(onClick = { AudioLevels.reset(store); revision++; active?.let { playback?.setAudioMode(it) } }) {
            Icon(Icons.Default.Restore, stringResource(R.string.audio_levels_reset))
        }
    }
    cards.chunked(2).forEach { row ->
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            row.forEach { card ->
                val chosen = card.mode == selected
                val shape = RoundedCornerShape(22.dp)
                Column(Modifier.weight(1f).clip(shape)
                    .background(if (chosen) StonePalette.Yellow.copy(alpha = .16f) else StonePalette.Chip)
                    .border(if (chosen) 2.dp else 1.dp, if (chosen) StonePalette.Yellow else Color.White.copy(alpha = .08f), shape)
                    .clickable { selected = card.mode; playback?.setAudioMode(card.mode) }.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(card.icon, null, Modifier.weight(1f, fill = false), tint = StonePalette.Yellow)
                        Spacer(Modifier.weight(1f))
                        if (chosen) Icon(Icons.Default.CheckCircle, null, tint = StonePalette.Yellow)
                    }
                    Text(stringResource(card.label), Modifier.padding(top = 14.dp), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(card.hint), Modifier.padding(top = 6.dp), color = StonePalette.Secondary, fontSize = 13.sp)
                }
            }
        }
    }
    HorizontalDivider(Modifier.padding(vertical = 22.dp), color = Color.White.copy(alpha = .08f))
    key(selected, revision) {
        var max by remember { mutableIntStateOf(selected.maxVolumePercent) }
        var gain by remember { mutableIntStateOf((selected.gain * 100).roundToInt()) }
        fun apply() {
            AudioLevels.set(store, selected, max, gain / 100f)
            // Re-applying the active mode pushes the new ceiling and gain to the player.
            if (active == selected) playback?.setAudioMode(selected)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(cards.first { it.mode == selected }.label), Modifier.weight(1f), fontSize = 24.sp, fontWeight = FontWeight.Black)
            if (active == selected) Text(stringResource(R.string.audio_levels_active), Modifier.clip(RoundedCornerShape(14.dp))
                .background(ApiGreen.copy(alpha = .15f)).padding(horizontal = 12.dp, vertical = 4.dp), color = ApiGreen, fontSize = 14.sp)
        }
        Text(stringResource(R.string.audio_levels_independent), Modifier.padding(top = 8.dp), color = StonePalette.Secondary)
        LevelSlider(R.string.audio_levels_max, max, 10f..300f, { max = (it / 10).roundToInt() * 10 }, ::apply)
        LevelSlider(R.string.audio_levels_gain, gain, 1f..100f, { gain = it.roundToInt().coerceAtLeast(1) }, ::apply)
        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.audio_levels_saved), Modifier.weight(1f), color = StonePalette.Secondary)
            Icon(Icons.Default.Check, null, tint = StonePalette.Secondary)
        }
    }
    Text(stringResource(R.string.audio_levels_intro), color = StonePalette.Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 16.dp))
    Text(stringResource(R.string.audio_levels_battery), color = StonePalette.Muted, fontSize = 12.sp,
        modifier = Modifier.padding(top = 6.dp, bottom = 16.dp))
}

@Composable private fun LevelSlider(label: Int, value: Int, range: ClosedFloatingPointRange<Float>,
    change: (Float) -> Unit, done: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(label), Modifier.weight(1f), fontSize = 17.sp)
        Text("$value %", fontWeight = FontWeight.Black, fontSize = 20.sp)
    }
    Slider(value.toFloat(), change, valueRange = range, onValueChangeFinished = done)
}
