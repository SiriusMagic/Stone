package com.zirakusa.stone.nativeapp.feature.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zirakusa.stone.core.LyricsDocument
import com.zirakusa.stone.core.LyricsParser
import com.zirakusa.stone.core.Song
import com.zirakusa.stone.core.lyricsDocument
import com.zirakusa.stone.nativeapp.R
import com.zirakusa.stone.nativeapp.data.library.LyricsOverrides
import com.zirakusa.stone.nativeapp.playback.PlaybackState
import com.zirakusa.stone.nativeapp.playback.PlaybackViewModel
import com.zirakusa.stone.nativeapp.ui.components.StoneArtwork
import com.zirakusa.stone.nativeapp.ui.theme.StonePalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.atan2

/** One full turn of the record moves the song this far (like scratching a real vinyl). */
private const val MS_PER_TURN = 15_000L
private val Cream = Color(0xFFF1E3CC)

/**
 * Landscape audio player: a turning vinyl on the left (drag it around to scrub; the bar
 * fills while you turn it) and title, lyrics and transport on the right.
 */
@Composable internal fun LandscapeVinylPlayer(state: PlaybackState, song: Song?, progress: Pair<Long, Long>,
    playback: PlaybackViewModel, back: () -> Unit, onOptions: () -> Unit, onLyrics: () -> Unit,
    onEqualizer: () -> Unit, onQueue: () -> Unit) {
    var scrubMs by remember(song?.id) { mutableStateOf<Long?>(null) }
    val shownProgress = scrubMs?.let { it to progress.second } ?: progress
    Row(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.displayCutout)) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
            val size = maxHeight * 1.05f
            if (song != null) VinylDisc(song, state.playing, progress, onScrub = { scrubMs = it },
                onRelease = { target -> playback.seek(target); scrubMs = null },
                modifier = Modifier.size(size).align(Alignment.CenterEnd).offset(x = -(maxWidth * .08f)))
        }
        Column(Modifier.weight(1.05f).fillMaxHeight().padding(start = 12.dp, end = 24.dp, top = 8.dp, bottom = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(state.title, color = Cream, fontSize = 24.sp, fontWeight = FontWeight.Black,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(state.artist, color = StonePalette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = onEqualizer) { Icon(Icons.Default.Tune, stringResource(R.string.equalizer)) }
                IconButton(onClick = onQueue) { Icon(Icons.Default.QueueMusic, stringResource(R.string.queue)) }
                IconButton(onClick = onOptions) { Icon(Icons.Default.MoreVert, stringResource(R.string.more_options)) }
                IconButton(onClick = back) { Icon(Icons.Default.KeyboardArrowDown, stringResource(R.string.close_player)) }
            }
            LyricLines(song, shownProgress.first, Modifier.weight(1f).fillMaxWidth().padding(vertical = 10.dp)
                .pointerInput(song?.id) { detectTapGestures { onLyrics() } })
            if (scrubMs != null) PlayerProgress(shownProgress, playback)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconToggleButton(state.favorite, { playback.toggleFavorite() }) {
                    Icon(if (state.favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        stringResource(R.string.favorite), tint = if (state.favorite) StonePalette.Yellow else Cream,
                        modifier = Modifier.size(30.dp))
                }
                Spacer(Modifier.weight(1f))
                IconButton(playback::previous, Modifier.size(56.dp)) {
                    Icon(Icons.Default.FastRewind, stringResource(R.string.previous), tint = Cream, modifier = Modifier.size(40.dp))
                }
                IconButton(playback::toggle, Modifier.size(64.dp)) {
                    Icon(if (state.playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                        stringResource(if (state.playing) R.string.pause else R.string.play), tint = Cream,
                        modifier = Modifier.size(48.dp))
                }
                IconButton(playback::next, Modifier.size(56.dp)) {
                    Icon(Icons.Default.FastForward, stringResource(R.string.next), tint = Cream, modifier = Modifier.size(40.dp))
                }
            }
        }
    }
}

@Composable private fun VinylDisc(song: Song, playing: Boolean, progress: Pair<Long, Long>,
    onScrub: (Long) -> Unit, onRelease: (Long) -> Unit, modifier: Modifier) {
    val spin = remember { Animatable(0f) }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(playing, dragging) {
        if (playing && !dragging) while (true) spin.animateTo(spin.value + 360f, tween(8_000, easing = LinearEasing))
    }
    val currentProgress by rememberUpdatedState(progress)
    // The arm swings onto the record while playing, like a turntable.
    val armAngle by animateFloatAsState(if (playing) 18f else 0f, tween(500), label = "tonearm")
    var manual by remember { mutableFloatStateOf(0f) }
    Box(modifier.pointerInput(song.id) {
        var lastAngle = 0f
        var target = 0L
        detectDragGestures(
            onDragStart = { start ->
                dragging = true
                lastAngle = angleOf(start, size.width / 2f, size.height / 2f)
                target = currentProgress.first
            },
            onDragEnd = { dragging = false; onRelease(target) },
            onDragCancel = { dragging = false; onRelease(target) },
        ) { change, _ ->
            val angle = angleOf(change.position, size.width / 2f, size.height / 2f)
            var delta = angle - lastAngle
            if (delta > 180f) delta -= 360f
            if (delta < -180f) delta += 360f
            lastAngle = angle
            manual += delta
            val duration = currentProgress.second.coerceAtLeast(1)
            target = (target + (delta / 360f * MS_PER_TURN).toLong()).coerceIn(0L, duration)
            onScrub(target)
        }
    }) {
        Box(Modifier.fillMaxSize().graphicsLayer { rotationZ = spin.value + manual }
            .clip(CircleShape)
            .background(Brush.radialGradient(listOf(Color(0xFF5A5F8C), Color(0xFF474B76), Color(0xFF3B3E66)))),
            contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val radius = size.minDimension / 2f
                for (index in 1..14) drawCircle(Color(0x18FFFFFF), radius * (.55f + index * .03f),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(1f))
            }
            Box(Modifier.fillMaxSize(.62f).clip(CircleShape).border(10.dp, Color(0x33FFFFFF), CircleShape)) {
                StoneArtwork(song.artworkSource, song.modifiedMs, Modifier.fillMaxSize().clip(CircleShape))
            }
        }
        Canvas(Modifier.fillMaxSize()) {
            val pivot = Offset(size.width * .86f, -size.height * .02f)
            val length = size.height * .52f
            val radians = Math.toRadians((100.0 - armAngle))
            val end = Offset(pivot.x + (length * kotlin.math.cos(radians)).toFloat(),
                pivot.y + (length * kotlin.math.sin(radians)).toFloat())
            drawLine(Color(0xFFEDEDF2), pivot, end, strokeWidth = 7f, cap = StrokeCap.Round)
            drawCircle(Color(0xFFEDEDF2), 14f, end)
        }
    }
}

private fun angleOf(point: Offset, cx: Float, cy: Float): Float =
    Math.toDegrees(atan2((point.y - cy).toDouble(), (point.x - cx).toDouble())).toFloat()

/** Up to four lyric lines; the current one is highlighted. */
@Composable private fun LyricLines(song: Song?, positionMs: Long, modifier: Modifier) {
    val context = LocalContext.current.applicationContext
    val overrides = remember(context) { LyricsOverrides(context) }
    val custom by produceState<String?>(null, song?.id) {
        val id = song?.id ?: return@produceState
        value = withContext(Dispatchers.IO) { overrides.get(id) }
    }
    val document = remember(song?.id, song?.lyrics, song?.syncedLyrics, custom) {
        custom?.let(LyricsParser::parse) ?: song?.lyricsDocument() ?: LyricsDocument(emptyList(), emptyList())
    }
    val lines = if (document.isTimed) document.synced.map { it.text.ifBlank { "♪" } } else document.plain
    val active = if (document.isTimed) LyricsParser.activeIndex(document.synced, positionMs) else -1
    Column(modifier, verticalArrangement = Arrangement.Center) {
        if (lines.isEmpty()) {
            Text(stringResource(R.string.no_lyrics), color = StonePalette.Muted, fontSize = 18.sp)
            return@Column
        }
        val start = (active - 1).coerceIn(0, (lines.size - 4).coerceAtLeast(0))
        lines.drop(start).take(4).forEachIndexed { offset, line ->
            val current = start + offset == active
            Text(line, Modifier.padding(vertical = 6.dp), color = if (current) Cream else Color(0xFFA8A9BE),
                fontSize = if (current) 22.sp else 20.sp, fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
