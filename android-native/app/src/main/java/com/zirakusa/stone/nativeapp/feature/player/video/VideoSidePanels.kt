package com.zirakusa.stone.nativeapp.feature.player.video

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zirakusa.stone.core.Song
import com.zirakusa.stone.nativeapp.R
import com.zirakusa.stone.nativeapp.data.library.artwork.ArtworkLoader
import com.zirakusa.stone.nativeapp.data.library.tags.MediaFileIo
import com.zirakusa.stone.nativeapp.playback.control.PlaybackState
import com.zirakusa.stone.nativeapp.playback.control.PlaybackViewModel
import com.zirakusa.stone.nativeapp.playback.StonePlaybackService
import com.zirakusa.stone.nativeapp.ui.theme.StonePalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt
import com.zirakusa.stone.nativeapp.feature.player.sheets.PlayerTool

private val PanelColor = Color(0xF0232328)

@Composable private fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxHeight().width(340.dp).background(PanelColor, RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp))
        .windowInsetsPadding(WindowInsets.displayCutout).padding(vertical = 12.dp), content = content)
}

/** ⋮ menu of the landscape video: timer, night mode, brightness, volume, equalizer, audio, properties, share. */
@Composable internal fun VideoMenuPanel(song: Song, state: PlaybackState, playback: PlaybackViewModel, night: Boolean,
    onNight: (Boolean) -> Unit, onTool: (PlayerTool) -> Unit, onAudioOnly: () -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf<String?>(null) }
    var properties by remember { mutableStateOf(false) }
    Panel {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            MenuRow(Icons.Default.Timer, R.string.sleep_timer) { onTool(PlayerTool.Sleep) }
            MenuRow(Icons.Default.Bedtime, R.string.video_night_mode, trailing = if (night) "ON" else "OFF") { onNight(!night) }
            MenuRow(Icons.Default.LightMode, R.string.video_brightness) { expanded = if (expanded == "light") null else "light" }
            if (expanded == "light") {
                var value by remember { mutableFloatStateOf(currentWindowBrightness(context)) }
                Slider(value, { value = it; setWindowBrightness(context, it) }, Modifier.padding(horizontal = 24.dp), valueRange = .02f..1f)
            }
            MenuRow(Icons.AutoMirrored.Filled.VolumeUp, R.string.video_volume) { expanded = if (expanded == "volume") null else "volume" }
            if (expanded == "volume") Slider(state.volumePercent.toFloat(), { StonePlaybackService.setVolumePercent(it.roundToInt()) },
                Modifier.padding(horizontal = 24.dp), valueRange = 0f..state.audioMode.maxVolumePercent.toFloat())
            MenuRow(Icons.Default.Tune, R.string.equalizer) { onTool(PlayerTool.Equalizer) }
            MenuRow(Icons.Default.MusicNote, R.string.audio_only, onClick = onAudioOnly)
            MenuRow(Icons.Default.Info, R.string.video_properties) { properties = true }
            MenuRow(Icons.Default.Share, R.string.share) { onTool(PlayerTool.Share) }
        }
    }
    if (properties) {
        val path by produceState("", song.uri) { value = withContext(Dispatchers.IO) { runCatching { MediaFileIo.displayPath(context, song.uri) }.getOrDefault("") } }
        val size = playback.player()?.videoSize
        val seconds = song.durationMs / 1000
        AlertDialog(onDismissRequest = { properties = false; onClose() },
            title = { Text(stringResource(R.string.video_properties)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Property(R.string.tags_name, song.title)
                    Property(R.string.tags_path, path)
                    Property(R.string.video_duration, "%d:%02d".format(seconds / 60, seconds % 60))
                    if (size != null && size.width > 0) Property(R.string.video_resolution, "${size.width} × ${size.height}")
                    Property(R.string.video_file_size, android.text.format.Formatter.formatShortFileSize(context, song.size))
                    Property(R.string.video_modified, DateFormat.getDateTimeInstance().format(Date(song.modifiedMs)))
                }
            },
            confirmButton = { TextButton(onClick = { properties = false }) { Text(stringResource(R.string.close)) } })
    }
}

@Composable private fun MenuRow(icon: ImageVector, label: Int, trailing: String? = null, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(26.dp))
        Text(stringResource(label), Modifier.weight(1f).padding(start = 18.dp), color = Color.White, fontSize = 17.sp)
        trailing?.let { Text(it, color = if (it == "ON") StonePalette.Yellow else Color.White.copy(alpha = .6f), fontSize = 12.sp, fontWeight = FontWeight.Bold) }
    }
}

@Composable private fun Property(label: Int, value: String) {
    Column {
        Text(stringResource(label), color = StonePalette.Muted, fontSize = 12.sp)
        Text(value.ifBlank { "—" }, fontSize = 14.sp)
    }
}

/** Video list (the play queue) beside the picture; the current one is highlighted. */
@Composable internal fun VideoListPanel(state: PlaybackState, playback: PlaybackViewModel, onClose: () -> Unit) {
    val list = rememberLazyListState(initialFirstVisibleItemIndex = state.index.coerceAtLeast(0))
    Panel {
        Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Default.ArrowBackIosNew, stringResource(R.string.back), tint = Color.White) }
            Text(stringResource(R.string.queue), color = Color.White, fontSize = 20.sp)
            Text(" (${state.queue.size})", color = Color.White.copy(alpha = .6f), fontSize = 16.sp)
        }
        LazyColumn(state = list) {
            itemsIndexed(state.queue, key = { _, item -> item.id }) { index, item ->
                val current = index == state.index
                Row(Modifier.fillMaxWidth().background(if (current) Color.White.copy(alpha = .08f) else Color.Transparent)
                    .clickable { playback.playAt(index) }.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    VideoThumb(item.uri, Modifier.size(112.dp, 63.dp).clip(RoundedCornerShape(8.dp)))
                    Text(item.title, Modifier.padding(start = 14.dp), color = if (current) StonePalette.Yellow else Color.White,
                        fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable private fun VideoThumb(uri: String?, modifier: Modifier) {
    val context = LocalContext.current.applicationContext
    val frame by produceState<android.graphics.Bitmap?>(null, uri) {
        value = uri?.let { runCatching { ArtworkLoader.loadVideoFrame(context, it, 0L).bitmap }.getOrNull() }
    }
    frame?.let { Image(it.asImageBitmap(), null, modifier, contentScale = ContentScale.Crop) }
        ?: Box(modifier.background(Color(0xFF34343A)))
}
