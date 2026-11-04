package com.zirakusa.stone.nativeapp.feature.player

import android.widget.Toast
import android.widget.NumberPicker
import android.media.audiofx.Visualizer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import com.zirakusa.stone.core.Song
import com.zirakusa.stone.nativeapp.R
import com.zirakusa.stone.nativeapp.data.library.AudioTagEditor
import com.zirakusa.stone.nativeapp.data.library.AudioTags
import com.zirakusa.stone.nativeapp.data.library.SongActions
import com.zirakusa.stone.nativeapp.data.library.StonePlaylist
import com.zirakusa.stone.nativeapp.playback.PlaybackState
import com.zirakusa.stone.nativeapp.playback.PlaybackViewModel
import com.zirakusa.stone.nativeapp.ui.components.StoneArtwork
import com.zirakusa.stone.nativeapp.ui.theme.StonePalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

enum class PlayerTool { Tags, Equalizer, Playlist, Share, Speed, Ringtone, Sleep, Energy, Visualizer,
    FloatingWindow, Hide, Delete }

private data class ToolRow(val icon: androidx.compose.ui.graphics.vector.ImageVector, val label: Int,
    val tool: PlayerTool)

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun PlayerOptionsSheet(state: PlaybackState, song: Song?, onClose: () -> Unit,
    onSelect: (PlayerTool) -> Unit) {
    val rows = listOf(ToolRow(Icons.Default.Sell, R.string.tag_editor, PlayerTool.Tags),
        ToolRow(Icons.Default.Tune, R.string.equalizer, PlayerTool.Equalizer),
        ToolRow(Icons.Default.PlaylistAdd, R.string.add_to_playlist, PlayerTool.Playlist),
        ToolRow(Icons.Default.Share, R.string.share, PlayerTool.Share),
        ToolRow(Icons.Default.Speed, R.string.speed, PlayerTool.Speed),
        ToolRow(Icons.Default.Notifications, R.string.set_ringtone, PlayerTool.Ringtone),
        ToolRow(Icons.Default.Timer, R.string.sleep_timer, PlayerTool.Sleep),
        ToolRow(Icons.Default.GraphicEq, R.string.music_visualizer, PlayerTool.Visualizer),
        ToolRow(Icons.Default.BatterySaver, R.string.energy_saver, PlayerTool.Energy),
        ToolRow(Icons.Default.VisibilityOff, R.string.hide, PlayerTool.Hide),
        ToolRow(Icons.Default.Delete, R.string.delete, PlayerTool.Delete))
    val videoRows = if (song?.isVideo == true) listOf(
        ToolRow(Icons.Default.PictureInPictureAlt, R.string.floating_window, PlayerTool.FloatingWindow)
    ) else emptyList()
    val visibleRows = rows.take(8) + videoRows + rows.drop(8)
    ModalBottomSheet(onDismissRequest = onClose, containerColor = Color(0xFF62393B)) {
        LazyColumn(Modifier.padding(horizontal = 28.dp), contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    StoneArtwork(song?.artworkSource, song?.modifiedMs ?: 0, Modifier.size(64.dp))
                    Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                        Text(state.title, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1,
                            overflow = TextOverflow.Ellipsis)
                        Text(state.artist, color = Color(0xFFD0BFC0), maxLines = 1)
                    }
                    IconButton(onClick = { onClose(); onSelect(PlayerTool.Tags) }) {
                        Icon(Icons.Default.Edit, stringResource(R.string.tag_editor))
                    }
                }
            }
            items(visibleRows) { row ->
                Row(Modifier.fillMaxWidth().height(64.dp).clickable { onClose(); onSelect(row.tool) },
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(row.icon, null, Modifier.size(28.dp))
                    Text(stringResource(row.label), Modifier.padding(start = 24.dp), fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

private enum class VisualizerStyle(val label: String) { Harmony("Armonía"), Flow("Flujo"), Bounce("Rebote"), Ascent("Ascenso") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun MusicVisualizerSheet(audioSessionId: Int, close: () -> Unit) {
    var enabled by remember { mutableStateOf(true) }
    var style by remember { mutableStateOf(VisualizerStyle.Harmony) }
    var values by remember { mutableStateOf(FloatArray(32) { 0f }) }
    var tick by remember { mutableIntStateOf(0) }
    val visualizer = remember(audioSessionId) {
        if (audioSessionId > 0) runCatching { Visualizer(audioSessionId) }.getOrNull() else null
    }
    DisposableEffect(visualizer) {
        visualizer?.let { effect ->
            runCatching {
                effect.captureSize = Visualizer.getCaptureSizeRange()[1]
                effect.enabled = true
            }
        }
        onDispose { runCatching { visualizer?.enabled = false; visualizer?.release() } }
    }
    LaunchedEffect(visualizer, enabled) {
        while (true) {
            if (enabled) {
                val wave = ByteArray(128)
                val fft = ByteArray(128)
                val captured = runCatching {
                    visualizer?.getWaveForm(wave); visualizer?.getFft(fft); true
                }.getOrDefault(false)
                values = FloatArray(32) { index ->
                    if (captured && visualizer != null) {
                        val offset = index * 4
                        ((kotlin.math.abs(wave[offset].toInt()) + kotlin.math.abs(fft[offset].toInt())) / 255f).coerceIn(.04f, 1f)
                    } else (.12f + ((kotlin.math.sin((index + tick) * .45) + 1.0) * .22)).toFloat()
                }
                tick++
            }
            delay(70)
        }
    }
    ModalBottomSheet(onDismissRequest = close, containerColor = Color(0xFF203C3D),
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.GraphicEq, null, tint = StonePalette.Yellow, modifier = Modifier.size(32.dp))
                Text(stringResource(R.string.music_visualizer), Modifier.weight(1f).padding(start = 14.dp),
                    fontSize = 24.sp, fontWeight = FontWeight.Black)
                Switch(enabled, { enabled = it })
            }
            Canvas(Modifier.fillMaxWidth().height(130.dp).padding(vertical = 18.dp)) {
                val gap = size.width / values.size
                values.forEachIndexed { index, value ->
                    val x = index * gap + gap / 2
                    val height = value * size.height
                    when (style) {
                        VisualizerStyle.Harmony, VisualizerStyle.Bounce, VisualizerStyle.Ascent -> drawRoundRect(
                            StonePalette.Yellow, Offset(x - gap * .28f, size.height - height),
                            androidx.compose.ui.geometry.Size(gap * .56f, height.coerceAtLeast(4f)), cornerRadius = androidx.compose.ui.geometry.CornerRadius(gap * .25f))
                        VisualizerStyle.Flow -> {
                            val y = size.height / 2 + kotlin.math.sin(index * .38 + tick * .08).toFloat() * height / 2
                            drawCircle(StonePalette.Yellow, gap * .22f, Offset(x, y))
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VisualizerStyle.entries.forEach { item ->
                    FilterChip(selected = style == item, onClick = { style = item }, label = { Text(item.label) })
                }
            }
            Spacer(Modifier.height(18.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun SpeedSheet(state: PlaybackState, playback: PlaybackViewModel, close: () -> Unit) {
    val speeds = listOf(.5f, .75f, 1f, 1.25f, 1.5f, 2f)
    ModalBottomSheet(onDismissRequest = close, containerColor = Color(0xFF17151B)) {
        Column(Modifier.padding(28.dp).padding(bottom = 24.dp)) {
            Text(stringResource(R.string.speed), fontSize = 28.sp, fontWeight = FontWeight.Black)
            speeds.forEach { speed ->
                Row(Modifier.fillMaxWidth().height(58.dp).clickable { playback.setSpeed(speed); close() },
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(if (speed == 1f) "1.0X (${stringResource(R.string.default_value)})" else "${speed}X",
                        Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    if (state.speed == speed) Icon(Icons.Default.Check, null)
                }
            }
        }
    }
}

private data class EqPreset(val name: Int, val values: List<Int>)
private val EqSurface = Color(0xFF203436)
private val EqCard = Color(0xFF243A3C)
private val EqOutline = Color(0xFF405456)
private val EqText = Color(0xFFE3ECEB)
private val EqMuted = Color(0xFF91A4A4)

/** Preset and band values live in PlaybackViewModel/PlaybackDataStore, not per song. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun EqualizerSheet(state: PlaybackState, playback: PlaybackViewModel,
    song: Song? = null, close: () -> Unit) {
    val frequencies = listOf("31", "63", "125", "250", "500", "1K", "2K", "4K", "8K", "16K")
    val presets = listOf(
        EqPreset(R.string.eq_custom, state.equalizerValues),
        EqPreset(R.string.eq_bass, listOf(5,5,3,1,0,0,0,0,0,0)),
        EqPreset(R.string.eq_voice, listOf(0,0,0,0,1,4,3,4,0,0)),
        EqPreset(R.string.eq_treble, listOf(0,0,0,0,0,1,2,2,3,5)),
        EqPreset(R.string.eq_normal, List(10) { 0 }),
        EqPreset(R.string.eq_pop, listOf(-1,-1,0,1,1,2,2,1,0,-1)),
        EqPreset(R.string.eq_hiphop, listOf(5,5,3,0,-2,-2,2,3,4,4)),
        EqPreset(R.string.eq_rock, listOf(5,3,2,0,0,1,0,2,2,4)),
        EqPreset(R.string.eq_electronic, listOf(4,7,6,-1,-2,0,5,8,3,5)),
        EqPreset(R.string.eq_heavy_metal, listOf(2,4,3,1,2,3,3,2,2,0)),
        EqPreset(R.string.eq_rnb, listOf(5,5,4,0,-2,-1,2,2,2,3)),
        EqPreset(R.string.eq_folk, listOf(0,2,3,0,0,0,6,3,2,-1)),
        EqPreset(R.string.eq_dance, listOf(5,5,4,2,1,3,4,3,2,0)),
        EqPreset(R.string.eq_jazz, listOf(3,3,2,0,-1,-1,0,2,3,4)),
        EqPreset(R.string.eq_classical, listOf(0,1,-2,0,-1,0,2,-2,-2,3)),
        EqPreset(R.string.eq_latin, listOf(1,3,2,0,0,-1,0,-1,0,3)),
    )
    val context = LocalContext.current
    var profileDialog by remember { mutableStateOf(false) }
    var profileName by remember { mutableStateOf("") }
    val progress = rememberPlayerProgress(state, playback)
    ModalBottomSheet(onDismissRequest = close, containerColor = EqSurface,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = { BottomSheetDefaults.DragHandle(color = EqOutline) }) {
        Column(Modifier.fillMaxHeight(.94f).padding(horizontal = 22.dp)) {
            Row(Modifier.fillMaxWidth().height(68.dp), verticalAlignment = Alignment.CenterVertically) {
                StoneArtwork(song?.artworkSource, song?.modifiedMs ?: 0,
                    Modifier.size(54.dp).clip(RoundedCornerShape(12.dp)))
                Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                    Text(state.title.ifBlank { stringResource(R.string.now_playing) }, color = EqText,
                        fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(state.artist, color = EqMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = playback::toggle) {
                    Icon(if (state.playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                        stringResource(if (state.playing) R.string.pause else R.string.play),
                        tint = EqText, modifier = Modifier.size(30.dp))
                }
                if (song != null) IconButton(onClick = {
                    playback.analyzeAndApply(song) { result ->
                        Toast.makeText(context, result.genre?.let { "Perfil detectado: $it" }
                            ?: "No se encontró un género en los metadatos", Toast.LENGTH_SHORT).show()
                    }
                }) { Icon(Icons.Default.AutoAwesome, stringResource(R.string.analyze_audio), tint = EqText) }
            }
            LinearProgressIndicator(
                progress = { if (progress.second > 0) progress.first.toFloat() / progress.second else 0f },
                Modifier.fillMaxWidth().padding(bottom = 14.dp), color = EqMuted,
                trackColor = EqOutline)
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.equalizer), Modifier.weight(1f), color = EqText,
                    fontSize = 26.sp, fontWeight = FontWeight.Black)
                Switch(state.equalizerEnabled, playback::setEqualizerEnabled)
            }
            Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { profileName = ""; profileDialog = true }) {
                    Icon(Icons.Default.Save, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.save_eq_profile))
                }
                if (state.equalizerEnabled) TextButton(onClick = { playback.setEqualizerEnabled(false) }) {
                    Text(stringResource(R.string.compare_original))
                }
            }
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(top = 14.dp, bottom = 24.dp)) {
                item {
                    Row(Modifier.fillMaxWidth().height(220.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        frequencies.forEachIndexed { index, frequency ->
                            EqualizerBand(index, frequency, state.equalizerValues.getOrElse(index) { 0 },
                                state.equalizerEnabled, Modifier.weight(1f), playback::setEqualizerBand)
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                }
                items(presets.chunked(2)) { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { preset ->
                            val name = stringResource(preset.name)
                            val active = state.equalizerPreset == name
                            Column(Modifier.weight(1f).padding(bottom = 12.dp)
                                .heightIn(min = 100.dp).clip(RoundedCornerShape(22.dp))
                                .background(if (active) Color(0xFF2D484A) else EqSurface)
                                .border(1.dp, if (active) EqMuted else EqOutline, RoundedCornerShape(22.dp))
                                .clickable { playback.setEqualizerPreset(name, preset.values) }
                                .padding(horizontal = 18.dp, vertical = 14.dp),
                                verticalArrangement = Arrangement.SpaceBetween) {
                                Icon(if (preset.name == R.string.eq_bass) Icons.Default.Speaker
                                    else if (preset.name == R.string.eq_voice) Icons.Default.Mic
                                    else if (preset.name == R.string.eq_rock) Icons.Default.MusicNote
                        else Icons.Default.GraphicEq, null, tint = EqMuted)
                                Text(name, color = EqText, fontSize = 16.sp, lineHeight = 20.sp,
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium)
                            }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                if (state.equalizerProfiles.isNotEmpty()) item {
                    Text(stringResource(R.string.saved_eq_profiles), color = EqText,
                        fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp, bottom = 8.dp))
                    state.equalizerProfiles.toList().chunked(2).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            row.forEach { (name, values) ->
                                Column(Modifier.weight(1f).padding(bottom = 12.dp)
                                    .heightIn(min = 88.dp).clip(RoundedCornerShape(22.dp))
                                    .background(if (state.equalizerPreset == name) Color(0xFF2D484A) else EqSurface)
                                    .border(1.dp, if (state.equalizerPreset == name) EqMuted else EqOutline,
                                        RoundedCornerShape(22.dp))
                                    .clickable { playback.applyEqualizerProfile(name) }
                                    .padding(horizontal = 18.dp, vertical = 14.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Person, null, tint = EqMuted)
                                        Spacer(Modifier.width(8.dp)); Text(name, color = EqText,
                                            fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                                        IconButton(onClick = { playback.deleteEqualizerProfile(name) }) {
                                            Icon(Icons.Default.Delete, stringResource(R.string.delete), tint = EqMuted)
                                        }
                                    }
                                }
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        if (profileDialog) AlertDialog(onDismissRequest = { profileDialog = false },
            title = { Text(stringResource(R.string.save_eq_profile)) },
            text = { OutlinedTextField(profileName, { profileName = it.take(40) },
                label = { Text(stringResource(R.string.profile_name)) }, singleLine = true) },
            confirmButton = { TextButton(enabled = profileName.isNotBlank(), onClick = {
                playback.saveEqualizerProfile(profileName); profileDialog = false
            }) { Text(stringResource(R.string.save)) } },
            dismissButton = { TextButton(onClick = { profileDialog = false }) {
                Text(stringResource(R.string.cancel))
            } })
    }
}

@Composable private fun EqualizerBand(index: Int, frequency: String, value: Int, enabled: Boolean,
    modifier: Modifier, change: (Int, Int) -> Unit) {
    Column(modifier.fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value.toString(), color = EqMuted, fontSize = 12.sp)
        Canvas(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 5.dp)
            .pointerInput(enabled, index) {
                if (enabled) detectTapGestures { point ->
                    change(index, verticalBandValue(point.y, size.height.toFloat()))
                }
            }.pointerInput(enabled, index) {
                if (enabled) detectVerticalDragGestures(onDragStart = { y ->
                    change(index, verticalBandValue(y.y, size.height.toFloat()))
                }) { changeEvent, _ ->
                    changeEvent.consume()
                    change(index, verticalBandValue(changeEvent.position.y, size.height.toFloat()))
                }
            }.semantics {
                contentDescription = "$frequency Hz"
                progressBarRangeInfo = ProgressBarRangeInfo(value + 10f, 0f..20f)
            }) {
            val x = size.width / 2f
            val top = 6.dp.toPx()
            val bottom = size.height - top
            val ratio = (value.coerceIn(-10, 10) + 10) / 20f
            val y = bottom - (bottom - top) * ratio
            drawLine(EqOutline, Offset(x, top), Offset(x, bottom), strokeWidth = 2.dp.toPx())
            drawCircle(if (enabled) EqText else EqMuted, 6.dp.toPx(), Offset(x, y))
        }
        Text(frequency, color = EqMuted, fontSize = 11.sp, textAlign = TextAlign.Center,
            maxLines = 1, modifier = Modifier.padding(top = 8.dp))
    }
}

private fun verticalBandValue(y: Float, height: Float): Int =
    ((1f - y / height.coerceAtLeast(1f)) * 20f - 10f).roundToInt().coerceIn(-10, 10)

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun SleepSheet(state: PlaybackState, playback: PlaybackViewModel, close: () -> Unit) {
    ModalBottomSheet(onDismissRequest = close, containerColor = Color(0xFF17151B)) {
        SleepTimerContent(state, playback)
    }
}

@Composable fun SleepTimerContent(state: PlaybackState, playback: PlaybackViewModel,
    close: () -> Unit = {}) {
    val options = listOf(0, 10, 20, 30, 60)
    var customVisible by remember { mutableStateOf(false) }
    var hours by remember(state.sleepMinutes) { mutableIntStateOf(
        if (state.sleepMinutes !in options) state.sleepMinutes / 60 else 0) }
    var minutes by remember(state.sleepMinutes) { mutableIntStateOf(
        if (state.sleepMinutes !in options && state.sleepMinutes > 0) state.sleepMinutes % 60 else 30) }

    if (customVisible) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { customVisible = false }) {
                    Icon(Icons.Default.ArrowBack, stringResource(R.string.back))
                }
                Text(stringResource(R.string.custom_sleep_time), fontSize = 24.sp, fontWeight = FontWeight.Black)
            }
            Row(Modifier.fillMaxWidth().height(240.dp), horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically) {
                SleepNumberPicker(hours, 0, 23, { hours = it }, R.string.hours)
                SleepNumberPicker(minutes, 0, 59, { minutes = it }, R.string.minutes_short)
            }
            Button(enabled = hours + minutes > 0, onClick = {
                playback.setSleepTimer(hours * 60 + minutes)
                customVisible = false
                close()
            }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text(stringResource(R.string.set_sleep_timer), fontWeight = FontWeight.Bold)
            }
        }
    } else {
        Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp).padding(bottom = 24.dp)) {
            Text(stringResource(R.string.sleep_timer), fontSize = 27.sp, fontWeight = FontWeight.Black)
            options.forEach { value ->
                Row(Modifier.fillMaxWidth().height(58.dp).clickable { playback.setSleepTimer(value) },
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(if (value == 0) stringResource(R.string.off) else
                        stringResource(R.string.minutes_later, value), Modifier.weight(1f),
                        color = if (state.sleepMinutes == value) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    if (state.sleepMinutes == value) Icon(Icons.Default.Check, null,
                        tint = MaterialTheme.colorScheme.primary)
                }
            }
            Row(Modifier.fillMaxWidth().height(58.dp).clickable { customVisible = true },
                verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.custom), Modifier.weight(1f))
                if (state.sleepMinutes !in options && state.sleepMinutes > 0)
                    Text(stringResource(R.string.minutes_later, state.sleepMinutes), color = MaterialTheme.colorScheme.primary)
                else Text(stringResource(R.string.set_custom), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Icon(Icons.Default.ChevronRight, null)
            }
            HorizontalDivider(Modifier.padding(vertical = 14.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.finish_current_track), Modifier.weight(1f))
                Switch(state.sleepFinishTrack, playback::setSleepFinishTrack)
            }
        }
    }
}

@Composable private fun SleepNumberPicker(value: Int, minimum: Int, maximum: Int,
    onValue: (Int) -> Unit, label: Int) {
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
        Text(stringResource(label), color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun TagEditorSheet(song: Song, close: () -> Unit, saved: () -> Unit) {
    val context = LocalContext.current; val scope = rememberCoroutineScope(); val editor = remember { AudioTagEditor(context) }
    var tags by remember { mutableStateOf(AudioTags(song.title, song.artist, song.album)) }
    var tab by remember { mutableIntStateOf(1) }
    var technical by remember { mutableStateOf<com.zirakusa.stone.nativeapp.data.library.AudioTechnicalInfo?>(null) }
    var busy by remember { mutableStateOf(true) }; var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(song.id) { tags = withContext(Dispatchers.IO) { runCatching { editor.read(song.uri) }.getOrDefault(tags) }; busy = false }
    ModalBottomSheet(onDismissRequest = close, containerColor = Color(0xFF17151B),
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxHeight(.92f).padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.tag_editor), Modifier.weight(1f), fontSize = 25.sp, fontWeight = FontWeight.Black)
                Button(enabled = !busy, onClick = { scope.launch {
                    busy = true; error = withContext(Dispatchers.IO) { runCatching { editor.write(song.uri, tags) }.exceptionOrNull()?.message }
                    busy = false; if (error == null) saved()
                } }) { Text(stringResource(R.string.save)) }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            TabRow(selectedTabIndex = tab, containerColor = Color.Transparent) {
                listOf(R.string.cover, R.string.tags, R.string.information, R.string.extras).forEachIndexed { index, label ->
                    Tab(selected = tab == index, onClick = { tab = index }, text = { Text(stringResource(label)) })
                }
            }
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
                when (tab) {
                    0 -> item { Text(stringResource(R.string.cover_editor_unavailable), color = StonePalette.Muted); StoneArtwork(song.artworkSource, song.modifiedMs, Modifier.fillMaxWidth().height(220.dp)) }
                    1 -> {
                        item { TagField(R.string.title, tags.title) { tags = tags.copy(title = it) } }
                        item { TagField(R.string.artist, tags.artist) { tags = tags.copy(artist = it) } }
                        item { TagField(R.string.album, tags.album) { tags = tags.copy(album = it) } }
                        item { TagField(R.string.genre, tags.genre) { tags = tags.copy(genre = it) } }
                        item { TagField(R.string.year, tags.year) { tags = tags.copy(year = it) } }
                        item { TagField(R.string.track_number, tags.trackNumber) { tags = tags.copy(trackNumber = it) } }
                        item { TagField(R.string.album_artist, tags.albumArtist) { tags = tags.copy(albumArtist = it) } }
                        item { TagField(R.string.composer, tags.composer) { tags = tags.copy(composer = it) } }
                        item { TagField(R.string.comment, tags.comment, false) { tags = tags.copy(comment = it) } }
                        item { TagField(R.string.description, tags.description, false) { tags = tags.copy(description = it) } }
                        item { TagField(R.string.disc_number, tags.discNumber) { tags = tags.copy(discNumber = it) } }
                        item { Text(stringResource(R.string.mp3_tags_only), color = StonePalette.Muted, fontSize = 12.sp) }
                    }
                    2 -> item {
                        LaunchedEffect(song.uri) { technical = withContext(Dispatchers.IO) { runCatching { com.zirakusa.stone.nativeapp.data.library.AudioTechnicalReader.read(context, song.uri) }.getOrNull() } }
                        technical?.let { info -> listOf(
                            R.string.file_name to info.name, R.string.format to info.format, R.string.file_size to info.size,
                            R.string.file_bytes to info.bytes, R.string.duration to info.duration, R.string.bitrate to info.bitrate,
                            R.string.sample_rate to info.sampleRate, R.string.channels to info.channels, R.string.mime to info.mime,
                            R.string.location to info.location).forEach { (label, value) ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) { Text(stringResource(label), Modifier.weight(1f), fontWeight = FontWeight.Bold); Text(value, color = StonePalette.Muted) }
                        } } ?: LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                    else -> item { Text(stringResource(R.string.extras_unavailable), color = StonePalette.Muted) }
                }
            }
        }
    }
}

@Composable private fun TagField(label: Int, value: String, multiline: Boolean = false, change: (String) -> Unit) {
    OutlinedTextField(value, change, Modifier.fillMaxWidth(), label = { Text(stringResource(label)) }, singleLine = !multiline, minLines = if (multiline) 4 else 1)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun RingtoneSheet(song: Song, close: () -> Unit) {
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    var start by remember { mutableFloatStateOf(0f) }; var busy by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = close, containerColor = Color(0xFF17151B)) {
        Column(Modifier.padding(28.dp).padding(bottom = 24.dp)) {
            Text(stringResource(R.string.set_ringtone), fontSize = 25.sp, fontWeight = FontWeight.Black)
            Text(stringResource(R.string.ringtone_start, duration(start.toLong())))
            Slider(start, { start = it }, valueRange = 0f..(song.durationMs - 30_000).coerceAtLeast(0).toFloat())
            Button(enabled = !busy, onClick = {
                if (!SongActions.canSetRingtone(context)) { SongActions.requestWriteSettings(context); return@Button }
                scope.launch { busy = true
                    val result = withContext(Dispatchers.IO) { runCatching { SongActions.setRingtone(context, song.uri, song.title, start.toInt()) } }
                    busy = false; Toast.makeText(context, if (result.isSuccess) context.getString(R.string.ringtone_ready)
                    else result.exceptionOrNull()?.message ?: context.getString(R.string.ringtone_failed), Toast.LENGTH_LONG).show()
                    if (result.isSuccess) close()
                }
            }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(if (SongActions.canSetRingtone(context)) R.string.set_ringtone else R.string.allow_and_continue)) }
        }
    }
}

@Composable fun ConfirmSongAction(delete: Boolean, confirm: () -> Unit, close: () -> Unit) {
    AlertDialog(onDismissRequest = close, title = { Text(stringResource(if (delete) R.string.delete_song_title else R.string.hide_song_title)) },
        text = { Text(stringResource(if (delete) R.string.delete_song_message else R.string.hide_song_message)) },
        confirmButton = { TextButton(onClick = confirm) { Text(stringResource(if (delete) R.string.delete else R.string.hide)) } },
        dismissButton = { TextButton(onClick = close) { Text(stringResource(R.string.cancel)) } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun PlaylistPickerSheet(song: Song, playlists: List<StonePlaylist>,
    create: (String) -> Unit, add: (String, Song) -> Unit, close: () -> Unit) {
    var creating by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = close, containerColor = Color(0xFF17151B)) {
        Column(Modifier.padding(24.dp).padding(bottom = 28.dp)) {
            Text(stringResource(R.string.add_to_playlist_title), fontSize = 26.sp, fontWeight = FontWeight.Black)
            OutlinedButton(onClick = { creating = true }, Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                Icon(Icons.Default.Add, null); Text(stringResource(R.string.new_playlist))
            }
            if (playlists.isEmpty()) Text(stringResource(R.string.no_playlists), color = MaterialTheme.colorScheme.onSurfaceVariant)
            playlists.forEach { playlist -> Row(Modifier.fillMaxWidth().height(58.dp).clickable {
                add(playlist.id, song); close()
            }, verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.QueueMusic, null); Text(playlist.name, Modifier.padding(start = 16.dp).weight(1f))
                if (song.id in playlist.songIds) Icon(Icons.Default.Check, null)
            } }
        }
    }
    if (creating) AlertDialog(onDismissRequest = { creating = false }, title = { Text(stringResource(R.string.new_playlist)) },
        text = { OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.playlist_name)) }) },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { create(name); creating = false; name = "" }) {
            Text(stringResource(R.string.create)) } }, dismissButton = { TextButton(onClick = { creating = false }) {
            Text(stringResource(R.string.cancel)) } })
}
