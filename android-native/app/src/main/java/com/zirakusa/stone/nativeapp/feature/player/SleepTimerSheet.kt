package com.zirakusa.stone.nativeapp.feature.player

import android.widget.NumberPicker
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.zirakusa.stone.nativeapp.R
import com.zirakusa.stone.nativeapp.playback.PlaybackState
import com.zirakusa.stone.nativeapp.playback.PlaybackViewModel
import com.zirakusa.stone.nativeapp.ui.ImmersiveWindowEffect
import com.zirakusa.stone.nativeapp.ui.components.SleepTimerIndicator
import com.zirakusa.stone.nativeapp.ui.theme.StonePalette

/** Preset durations offered in the sheet (0 = off). */
internal val SLEEP_PRESETS = listOf(0, 10, 15, 20, 30, 45, 60, 90)

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun SleepSheet(state: PlaybackState, playback: PlaybackViewModel, close: () -> Unit) {
    ModalBottomSheet(onDismissRequest = close, containerColor = Color(0xFF17151B)) {
        ImmersiveWindowEffect()
        SleepTimerContent(state, playback)
    }
}

/** Sleep timer: presets, custom time, end-of-song, extend buttons and the two behaviour switches. */
@Composable fun SleepTimerContent(state: PlaybackState, playback: PlaybackViewModel, close: () -> Unit = {}) {
    var customVisible by remember { mutableStateOf(false) }
    if (customVisible) {
        CustomSleepPicker(state.sleepCustomMinutes, back = { customVisible = false }) { minutes ->
            playback.setCustomSleepTimer(minutes); customVisible = false; close()
        }
        return
    }
    val active = state.sleepMinutes > 0
    Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp).padding(bottom = 24.dp)) {
        Text(stringResource(R.string.sleep_timer), fontSize = 27.sp, fontWeight = FontWeight.Black)
        if (active) {
            SleepTimerIndicator(state.sleepMinutes, Modifier.padding(top = 10.dp), endAtMillis = state.sleepEndAtMs)
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(5, 15).forEach { extra ->
                    FilledTonalButton(onClick = { playback.extendSleepTimer(extra) }) {
                        Text(stringResource(R.string.sleep_add_minutes, extra))
                    }
                }
                OutlinedButton(onClick = { playback.setSleepTimer(0) }) { Text(stringResource(R.string.off)) }
            }
            HorizontalDivider(Modifier.padding(vertical = 14.dp), color = MaterialTheme.colorScheme.outlineVariant)
        }
        SLEEP_PRESETS.filter { active || it > 0 }.forEach { value ->
            SleepRow(if (value == 0) stringResource(R.string.off) else pluralStringResource(R.plurals.minutes_later, value, value),
                selected = state.sleepMinutes == value) { playback.setSleepTimer(value) }
        }
        SleepRow(stringResource(R.string.sleep_end_of_track), selected = false) { playback.sleepAtEndOfTrack(); close() }
        Row(Modifier.fillMaxWidth().height(58.dp).clickable { customVisible = true }, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.custom), Modifier.weight(1f))
            if (active && state.sleepMinutes !in SLEEP_PRESETS)
                Text(pluralStringResource(R.plurals.minutes_later, state.sleepMinutes, state.sleepMinutes), color = MaterialTheme.colorScheme.primary)
            else Text(stringResource(R.string.set_custom), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Icon(Icons.Default.ChevronRight, null)
        }
        HorizontalDivider(Modifier.padding(vertical = 14.dp), color = MaterialTheme.colorScheme.outlineVariant)
        SleepSwitch(R.string.finish_current_track, null, state.sleepFinishTrack, playback::setSleepFinishTrack)
        SleepSwitch(R.string.sleep_fade_out, R.string.sleep_fade_out_hint, state.sleepFadeOut, playback::setSleepFadeOut)
    }
}

@Composable private fun SleepRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(58.dp).clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f),
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        if (selected) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable private fun SleepSwitch(title: Int, hint: Int?, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(title))
            if (hint != null) Text(stringResource(hint), color = StonePalette.Muted, fontSize = 12.sp)
        }
        Switch(value, onChange)
    }
}

@Composable private fun CustomSleepPicker(initialMinutes: Int, back: () -> Unit, onSet: (Int) -> Unit) {
    var hours by remember { mutableIntStateOf(initialMinutes / 60) }
    var minutes by remember { mutableIntStateOf(initialMinutes % 60) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = back) { Icon(Icons.Default.ArrowBack, stringResource(R.string.back)) }
            Text(stringResource(R.string.custom_sleep_time), fontSize = 24.sp, fontWeight = FontWeight.Black)
        }
        Row(Modifier.fillMaxWidth().height(240.dp), horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically) {
            SleepNumberPicker(hours, 0, 23, { hours = it }, R.string.hours)
            SleepNumberPicker(minutes, 0, 59, { minutes = it }, R.string.minutes_short)
        }
        Button(enabled = hours + minutes > 0, onClick = { onSet(hours * 60 + minutes) },
            modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text(stringResource(R.string.set_sleep_timer), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable private fun SleepNumberPicker(value: Int, minimum: Int, maximum: Int, onValue: (Int) -> Unit, label: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        AndroidView(factory = { context -> NumberPicker(context).apply {
            minValue = minimum
            maxValue = maximum
            this.value = value.coerceIn(minimum, maximum)
            wrapSelectorWheel = true
            descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS
            setOnValueChangedListener { _, _, next -> onValue(next) }
        } }, modifier = Modifier.width(112.dp).height(190.dp), update = { picker ->
            if (picker.value != value) picker.value = value.coerceIn(minimum, maximum)
        })
        Text(stringResource(label), color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
    }
}
