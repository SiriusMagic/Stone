package com.zirakusa.stone.nativeapp.feature.player.sheets

import android.widget.Toast
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import com.zirakusa.stone.core.Song
import com.zirakusa.stone.nativeapp.R
import com.zirakusa.stone.nativeapp.data.library.tags.AudioTagEditor
import com.zirakusa.stone.nativeapp.data.library.tags.AudioTags
import com.zirakusa.stone.nativeapp.data.library.artwork.ArtworkLoader
import com.zirakusa.stone.nativeapp.data.library.user.SongActions
import com.zirakusa.stone.nativeapp.data.library.user.StonePlaylist
import com.zirakusa.stone.nativeapp.data.library.tags.VideoMetadataDraft
import com.zirakusa.stone.nativeapp.data.library.tags.VideoMetadataEditor
import com.zirakusa.stone.nativeapp.playback.control.PlaybackState
import com.zirakusa.stone.nativeapp.playback.control.PlaybackViewModel
import com.zirakusa.stone.nativeapp.ui.components.StoneArtwork
import com.zirakusa.stone.nativeapp.ui.theme.StonePalette
import com.zirakusa.stone.nativeapp.ui.system.ImmersiveWindowEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import com.zirakusa.stone.nativeapp.feature.player.duration
import com.zirakusa.stone.nativeapp.feature.player.tags.AudioTagEditorSheet
import com.zirakusa.stone.nativeapp.feature.player.tags.VideoTagEditorSheet

/** Song currently played as audio only; mirrored from StoneApp for the options menu. */
internal object AudioOnlySelection { var id by mutableStateOf<String?>(null) }

enum class PlayerTool { Tags, Equalizer, Playlist, Share, Speed, Ringtone, Sleep,
    FloatingWindow, Delete }

private data class ToolRow(val icon: androidx.compose.ui.graphics.vector.ImageVector, val label: Int,
    val tool: PlayerTool)

internal data class PlayerOptionExtra(val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val label: Int, val onClick: () -> Unit)

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun PlayerOptionsSheet(state: PlaybackState, song: Song?, onClose: () -> Unit,
    onSelect: (PlayerTool) -> Unit, extraActions: List<PlayerOptionExtra> = emptyList(),
    tint: Color = Color(0xFF62393B)) {
    val window = androidx.compose.ui.platform.LocalWindowInfo.current.containerSize
    if (window.width > window.height) {
        // Landscape: right side panel like the video menu, the player stays visible.
        androidx.compose.ui.window.Dialog(onDismissRequest = onClose,
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false)) {
            ImmersiveWindowEffect()
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxHeight().clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null, onClick = onClose))
                Surface(Modifier.fillMaxHeight().widthIn(min = 300.dp, max = 400.dp).fillMaxWidth(.40f), color = tint,
                    shape = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp)) {
                    PlayerOptionsContent(state, song, extraActions = extraActions, onSelect = onSelect,
                        modifier = Modifier.padding(top = 16.dp))
                }
            }
        }
        return
    }
    ModalBottomSheet(onDismissRequest = onClose, containerColor = tint) {
        ImmersiveWindowEffect()
        PlayerOptionsContent(state, song, extraActions = extraActions, onSelect = onSelect)
    }
}

/** Shared options content used by the normal bottom sheet and the horizontal mini-player panel. */
@Composable internal fun PlayerOptionsContent(state: PlaybackState, song: Song?,
    onSelect: (PlayerTool) -> Unit, modifier: Modifier = Modifier,
    extraActions: List<PlayerOptionExtra> = emptyList()) {
    val headerTitle = song?.title?.takeIf { it.isNotBlank() } ?: state.title
    val headerArtist = song?.artist?.takeIf { it.isNotBlank() } ?: state.artist
    val rows = buildList {
        add(ToolRow(Icons.Default.PlaylistAdd, R.string.add_to_playlist, PlayerTool.Playlist))
        add(ToolRow(Icons.Default.Share, R.string.share, PlayerTool.Share))
        add(ToolRow(Icons.Default.Speed, R.string.speed, PlayerTool.Speed))
        add(ToolRow(Icons.Default.Notifications, R.string.set_ringtone, PlayerTool.Ringtone))
        add(ToolRow(Icons.Default.Timer, R.string.sleep_timer, PlayerTool.Sleep))
        add(ToolRow(Icons.Default.Delete, R.string.delete, PlayerTool.Delete))
    }
    // A video playing in "audio only" mode has no picture, so a floating window makes no sense.
    val videoRows = if (song?.isVideo == true && song.id == state.currentId &&
        song.id != AudioOnlySelection.id) listOf(
        ToolRow(Icons.Default.PictureInPictureAlt, R.string.floating_window, PlayerTool.FloatingWindow)
    ) else emptyList()
    val visibleRows = rows.take(8) + videoRows + rows.drop(8)
    LazyColumn(modifier.padding(horizontal = 28.dp), contentPadding = PaddingValues(bottom = 32.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                StoneArtwork(song?.artworkSource, song?.modifiedMs ?: 0, Modifier.size(64.dp))
                Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                    Text(headerTitle, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                    Text(headerArtist, color = Color(0xFFD0BFC0), maxLines = 1)
                }
                IconButton(onClick = { onSelect(PlayerTool.Tags) }) {
                    Icon(Icons.Default.Edit, stringResource(R.string.tag_editor))
                }
            }
        }
        items(visibleRows) { row ->
            // Groups like the reference: playlist/share | tools | delete.
            if (row.tool == PlayerTool.Speed || row.tool == PlayerTool.Delete)
                HorizontalDivider(Modifier.padding(vertical = 6.dp), color = Color(0x22FFFFFF))
            Row(Modifier.fillMaxWidth().height(64.dp).clickable { onSelect(row.tool) },
                verticalAlignment = Alignment.CenterVertically) {
                Icon(row.icon, null, Modifier.size(28.dp))
                Text(stringResource(row.label), Modifier.padding(start = 24.dp), fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold)
            }
        }
        items(extraActions) { action ->
            Row(Modifier.fillMaxWidth().height(64.dp).clickable(onClick = action.onClick),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(action.icon, null, Modifier.size(28.dp))
                Text(stringResource(action.label), Modifier.padding(start = 24.dp), fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun SpeedSheet(state: PlaybackState, playback: PlaybackViewModel, close: () -> Unit) {
    val speeds = listOf(.5f, .75f, 1f, 1.25f, 1.5f, 2f)
    ModalBottomSheet(onDismissRequest = close, containerColor = Color(0xFF17151B)) {
        ImmersiveWindowEffect()
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
private val EqOutline = Color(0xFF405456)
private val EqText = Color(0xFFE3ECEB)
private val EqMuted = Color(0xFF91A4A4)

/** Preset and band values live in PlaybackViewModel/PlaybackDataStore, not per song. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun EqualizerSheet(state: PlaybackState, playback: PlaybackViewModel,
    progress: Pair<Long, Long>, song: Song? = null, close: () -> Unit) {
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
    ModalBottomSheet(onDismissRequest = close, containerColor = EqSurface,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = { BottomSheetDefaults.DragHandle(color = EqOutline) }) {
        ImmersiveWindowEffect()
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
            // Each new song gets the preset of its genre; songs without one keep the user's bands.
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.eq_by_genre), color = EqText, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.eq_by_genre_hint), color = EqMuted, fontSize = 12.sp, lineHeight = 15.sp)
                }
                Switch(state.equalizerByGenre, playback::setEqualizerByGenre)
            }
            if (state.equalizerEnabled) {
                TextButton(onClick = { playback.setEqualizerEnabled(false) }) {
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
            }
        }
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
@Composable fun TagEditorSheet(song: Song, close: () -> Unit, saved: () -> Unit) {
    if (song.isVideo) {
        VideoTagEditorSheet(song, close, saved)
        return
    }
    AudioTagEditorSheet(song, close, saved)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun RingtoneSheet(song: Song, close: () -> Unit) {
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    val stoneResources = androidx.compose.ui.platform.LocalResources.current
    var start by remember { mutableFloatStateOf(0f) }; var busy by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = close, containerColor = Color(0xFF17151B)) {
        ImmersiveWindowEffect()
        Column(Modifier.padding(28.dp).padding(bottom = 24.dp)) {
            Text(stringResource(R.string.set_ringtone), fontSize = 25.sp, fontWeight = FontWeight.Black)
            Text(stringResource(R.string.ringtone_start, duration(start.toLong())))
            Slider(start, { start = it }, valueRange = 0f..(song.durationMs - 30_000).coerceAtLeast(0).toFloat())
            Button(enabled = !busy, onClick = {
                if (!SongActions.canSetRingtone(context)) { SongActions.requestWriteSettings(context); return@Button }
                scope.launch { busy = true
                    val result = withContext(Dispatchers.IO) { runCatching { SongActions.setRingtone(context, song.uri, song.title, start.toInt()) } }
                    busy = false; Toast.makeText(context, if (result.isSuccess) stoneResources.getString(R.string.ringtone_ready)
                    else result.exceptionOrNull()?.message ?: stoneResources.getString(R.string.ringtone_failed), Toast.LENGTH_LONG).show()
                    if (result.isSuccess) close()
                }
            }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(if (SongActions.canSetRingtone(context)) R.string.set_ringtone else R.string.allow_and_continue)) }
        }
    }
}

@Composable fun ConfirmSongAction(confirm: () -> Unit, close: () -> Unit) {
    AlertDialog(onDismissRequest = close, title = { ImmersiveWindowEffect(); Text(stringResource(R.string.delete_song_title)) },
        text = { Text(stringResource(R.string.delete_song_message)) },
        confirmButton = { TextButton(onClick = confirm) { Text(stringResource(R.string.delete)) } },
        dismissButton = { TextButton(onClick = close) { Text(stringResource(R.string.cancel)) } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun PlaylistPickerSheet(song: Song, playlists: List<StonePlaylist>,
    create: (String) -> Unit, add: (String, Song) -> Unit, close: () -> Unit) {
    var creating by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = close, containerColor = Color(0xFF17151B)) {
        ImmersiveWindowEffect()
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
    if (creating) AlertDialog(onDismissRequest = { creating = false }, title = { ImmersiveWindowEffect(); Text(stringResource(R.string.new_playlist)) },
        text = { OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.playlist_name)) }) },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { create(name); creating = false; name = "" }) {
            Text(stringResource(R.string.create)) } }, dismissButton = { TextButton(onClick = { creating = false }) {
            Text(stringResource(R.string.cancel)) } })
}
