@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.zirakusa.stone.nativeapp.feature.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.util.Rational
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.produceState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.common.Player
import com.zirakusa.stone.core.Song
import com.zirakusa.stone.core.LyricsDocument
import com.zirakusa.stone.core.LyricsParser
import com.zirakusa.stone.nativeapp.data.library.LyricsOverrides
import com.zirakusa.stone.core.lyricsDocument
import com.zirakusa.stone.nativeapp.R
import com.zirakusa.stone.nativeapp.playback.PlaybackState
import com.zirakusa.stone.nativeapp.playback.PlaybackViewModel
import com.zirakusa.stone.nativeapp.playback.StonePlaybackService
import com.zirakusa.stone.nativeapp.data.library.SongActions
import com.zirakusa.stone.nativeapp.data.library.ArtworkLoader
import com.zirakusa.stone.nativeapp.data.library.StonePlaylist
import com.zirakusa.stone.nativeapp.ui.components.StoneArtwork
import com.zirakusa.stone.nativeapp.ui.components.SleepTimerIndicator
import com.zirakusa.stone.nativeapp.ui.theme.StonePalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun PlayerScreen(state: PlaybackState, song: Song?, playback: PlaybackViewModel,
    back: () -> Unit, onHide: (Song) -> Unit, onDelete: (Song) -> Unit,
    onMetadataChanged: () -> Unit, playlists: List<StonePlaylist>,
    onCreatePlaylist: (String) -> Unit, onAddToPlaylist: (String, Song) -> Unit,
    modifier: Modifier = Modifier, onArtist: () -> Unit = {}, isPictureInPicture: Boolean = false,
    onAudioOnly: (Song) -> Unit = {}, openCoverEditor: Boolean = false,
    onCoverEditorConsumed: () -> Unit = {}) {
    BackHandler(onBack = back)
    val context = LocalContext.current
    var queueVisible by remember { mutableStateOf(false) }
    var lyricsVisible by remember { mutableStateOf(false) }
    var optionsVisible by remember { mutableStateOf(false) }
    var tool by remember { mutableStateOf<PlayerTool?>(null) }
    var videoFullscreen by remember { mutableStateOf(false) }
    val dynamicBackground by produceState(Color(0xFF0D0C11), song?.uri, song?.modifiedMs) {
        value = withContext(Dispatchers.IO) {
            song?.uri?.let { uri -> ArtworkLoader.load(context.applicationContext, uri, song.modifiedMs) }
                ?.let(::coverBackgroundColor) ?: Color(0xFF0D0C11)
        }
    }
    LaunchedEffect(openCoverEditor, song?.id) {
        if (openCoverEditor && song != null) {
            tool = PlayerTool.Cover
            onCoverEditorConsumed()
        }
    }
    if (isPictureInPicture && song?.isVideo == true) {
        PictureInPictureVideoPlayer(playback)
        return
    }
    if (videoFullscreen && song?.isVideo == true) {
        BackHandler { videoFullscreen = false }
        FullscreenVideoPlayer(song, state, rememberPlayerProgress(state, playback), playback,
            close = { videoFullscreen = false }, onAudioOnly = {
                videoFullscreen = false
                onAudioOnly(it)
            })
        return
    }
    val progress = rememberPlayerProgress(state, playback)
    val audioSwipeModifier = if (song?.isVideo != true) Modifier.pointerInput(song?.id) {
        detectTrackSwipe(playback::previous, playback::next)
    } else Modifier
    BoxWithConstraints(modifier.fillMaxSize().background(dynamicBackground).then(audioSwipeModifier)) {
    val landscape = maxWidth > maxHeight
    if (landscape && song?.isVideo == true) {
        LandscapeVideoPlayer(song, state, progress, playback, back,
            onOptions = { optionsVisible = true }, onQueue = { queueVisible = true },
            onAudioOnly = {
                videoFullscreen = false
                onAudioOnly(it)
            })
    } else if (landscape) {
        LandscapeAudioPlayer(state, song, progress, playback, back,
            onOptions = { optionsVisible = true }, onLyrics = { lyricsVisible = true },
            onEqualizer = { tool = PlayerTool.Equalizer },
            onQueue = { queueVisible = true })
    } else if (song?.isVideo == true) PortraitVideoPlayer(song, state, progress, playback, back,
        onOptions = { optionsVisible = true }, onQueue = { queueVisible = true },
        onAudioOnly = {
            videoFullscreen = false
            onAudioOnly(it)
        })
    else Column(Modifier.fillMaxSize().padding(horizontal = 28.dp).padding(bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().height(70.dp)) {
            IconButton(onClick = back, Modifier.align(Alignment.CenterStart)) {
                Icon(Icons.Default.KeyboardArrowDown, stringResource(R.string.close_player), Modifier.size(36.dp))
            }
            Column(Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = 52.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.player_now), color = Color(0xFF8A848F), fontSize = 12.sp,
                    letterSpacing = 1.5.sp)
                Text(state.artist.uppercase(), fontWeight = FontWeight.Bold, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
                if (state.sleepMinutes > 0) SleepTimerIndicator(state.sleepMinutes,
                    Modifier.height(30.dp), endAtMillis = state.sleepEndAtMs,
                    onClick = { tool = PlayerTool.Sleep })
            }
        }
        if (song?.isVideo == true) Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(factory = { context ->
                PlayerView(context).apply { useController = false; resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    player = playback.player() }
            }, update = { it.player = playback.player() }, modifier = Modifier.fillMaxSize()
                .clip(RoundedCornerShape(20.dp)))
            IconButton(onClick = { videoFullscreen = true }, modifier = Modifier
                .align(Alignment.BottomStart).padding(14.dp)
                .background(Color(0xFFE51C23), RoundedCornerShape(5.dp))) {
                Icon(Icons.Default.Fullscreen, "Reproducir en pantalla completa", tint = Color.White)
            }
        }
        else if (song != null) StoneArtwork(song.artworkSource, song.modifiedMs,
            Modifier.weight(1f).aspectRatio(1f).fillMaxWidth().clip(RoundedCornerShape(28.dp)))
        else Box(Modifier.weight(1f).aspectRatio(1f).fillMaxWidth())
        Row(Modifier.fillMaxWidth().padding(top = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(state.title, fontSize = 25.sp, fontWeight = FontWeight.Black, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
                Text(state.artist.uppercase(), color = Color(0xFF8A848F), fontSize = 15.sp,
                    modifier = Modifier.padding(top = 5.dp).clickable(
                        enabled = state.artist.isNotBlank(), onClick = onArtist), maxLines = 1)
            }
            IconToggleButton(state.favorite, { playback.toggleFavorite() }) {
                Icon(if (state.favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    stringResource(R.string.favorite), tint = if (state.favorite) StonePalette.Yellow else Color(0xFFE8E6EE))
            }
            IconButton(onClick = { optionsVisible = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.more_options)) }
        }
        PlayerProgress(progress, playback)
        Row(Modifier.fillMaxWidth().height(104.dp), horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(playback::previous, Modifier.size(78.dp)) {
                Icon(Icons.Default.SkipPrevious, stringResource(R.string.previous), Modifier.size(68.dp))
            }
            IconButton(playback::toggle, Modifier.size(78.dp)) {
                Icon(if (state.playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                    stringResource(if (state.playing) R.string.pause else R.string.play), Modifier.size(72.dp))
            }
            IconButton(playback::next, Modifier.size(78.dp)) {
                Icon(Icons.Default.SkipNext, stringResource(R.string.next), Modifier.size(68.dp))
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton(onClick = { tool = PlayerTool.Equalizer }) { Icon(Icons.Default.Tune, stringResource(R.string.equalizer)) }
            CurrentLyricChip(song, progress.first, { lyricsVisible = true }, Modifier.weight(1f))
            IconButton(onClick = { queueVisible = true }) { Icon(Icons.Default.Menu, stringResource(R.string.queue)) }
        }
    }
    }
    if (queueVisible) QueueSheet(state, playback) { queueVisible = false }
    if (lyricsVisible && song != null) LyricsSheet(song, state, playback, progress) { lyricsVisible = false }
    if (optionsVisible) PlayerOptionsSheet(state, song, { optionsVisible = false }) { selected ->
        when (selected) {
            PlayerTool.Share -> song?.let { runCatching { SongActions.share(context, it.uri, it.title) }
                .onFailure { error -> android.widget.Toast.makeText(context, error.message, android.widget.Toast.LENGTH_LONG).show() } }
            PlayerTool.FloatingWindow -> {
                enterVideoPictureInPicture(context, playback.player())
                tool = null
            }
            else -> tool = selected
        }
    }
    when (tool) {
        PlayerTool.Speed -> SpeedSheet(state, playback) { tool = null }
        PlayerTool.Equalizer -> EqualizerSheet(state, playback, song = song) { tool = null }
        PlayerTool.Cover -> song?.let { TagEditorSheet(it, initialTab = 0, close = { tool = null }) {
            tool = null; onMetadataChanged(); back()
        } }
        PlayerTool.Sleep -> SleepSheet(state, playback) { tool = null }
        PlayerTool.Tags -> song?.let { TagEditorSheet(it, close = { tool = null }) {
            tool = null; onMetadataChanged(); back()
        } }
        PlayerTool.Ringtone -> song?.let { RingtoneSheet(it) { tool = null } }
        PlayerTool.Playlist -> song?.let { PlaylistPickerSheet(it, playlists, onCreatePlaylist,
            onAddToPlaylist) { tool = null } }
        PlayerTool.Hide -> ConfirmSongAction(false, {
            song?.let { playback.remove(it.id); onHide(it) }; tool = null; back()
        }, { tool = null })
        PlayerTool.Delete -> ConfirmSongAction(true, {
            song?.let { playback.remove(it.id); onDelete(it) }; tool = null; back()
        }, { tool = null })
        else -> Unit
    }
}

@Composable private fun PictureInPictureVideoPlayer(playback: PlaybackViewModel) {
    AndroidView(factory = { context ->
        PlayerView(context).apply {
            useController = false
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            player = playback.player()
        }
    }, update = { it.player = playback.player() }, modifier = Modifier.fillMaxSize())
}

private fun enterVideoPictureInPicture(context: Context, player: Player?) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val activity = context.findActivity() ?: return
    val videoSize = player?.videoSize
    val fallback = if (activity.resources.configuration.orientation ==
        android.content.res.Configuration.ORIENTATION_LANDSCAPE) Rational(16, 9) else Rational(9, 16)
    val width = videoSize?.width ?: 0
    val height = videoSize?.height ?: 0
    val pixelRatio = videoSize?.pixelWidthHeightRatio?.takeIf { it > 0f } ?: 1f
    val aspect = if (width > 0 && height > 0) {
        val adjustedWidth = (width * pixelRatio).roundToInt().coerceAtLeast(1)
        val ratio = adjustedWidth.toFloat() / height
        when {
            ratio > 2.39f -> Rational(239, 100)
            ratio < 1f / 2.39f -> Rational(100, 239)
            else -> Rational(adjustedWidth, height)
        }
    } else fallback
    runCatching {
        activity.enterPictureInPictureMode(
            android.app.PictureInPictureParams.Builder().setAspectRatio(aspect).build()
        )
    }
}

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable private fun FullscreenVideoPlayer(song: Song, state: PlaybackState,
    progress: Pair<Long, Long>, playback: PlaybackViewModel, close: () -> Unit,
    onAudioOnly: (Song) -> Unit) {
    val controls = rememberVideoControls(song.id, state.playing)
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { context ->
            PlayerView(context).apply {
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                player = playback.player()
            }
        }, update = { it.player = playback.player() }, modifier = Modifier.fillMaxSize())
        VideoGestureOverlay(state, playback, Modifier.matchParentSize(), controls.toggle,
            onPrevious = playback::previous, onNext = playback::next)
        if (controls.visible) {
            Row(Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = close) { Icon(Icons.Default.Close, stringResource(R.string.close_player), tint = Color.White) }
                Text(song.title, Modifier.weight(1f), color = Color.White, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                SleepTimerIndicator(state.sleepMinutes, endAtMillis = state.sleepEndAtMs,
                    showLabel = false)
                IconButton(onClick = { onAudioOnly(song) }) {
                    Icon(Icons.Default.Headphones, stringResource(R.string.audio_only), tint = StonePalette.Yellow)
                }
            }
            VideoTransportControls(state, progress, playback, Modifier.align(Alignment.Center))
            Column(Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                .background(Color(0x990D0C11)).padding(horizontal = 18.dp, vertical = 12.dp)) {
                VideoTimeline(song, progress, playback)
                VideoActionRow(
                    onAudioOnly = { onAudioOnly(song) },
                    onClearScreen = controls.toggle,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable private fun PortraitVideoPlayer(song: Song, state: PlaybackState,
    progress: Pair<Long, Long>, playback: PlaybackViewModel, back: () -> Unit,
    onOptions: () -> Unit, onQueue: () -> Unit,
    onAudioOnly: (Song) -> Unit) {
    val controls = rememberVideoControls(song.id, state.playing)
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { context ->
            PlayerView(context).apply {
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                player = playback.player()
            }
        }, update = { it.player = playback.player() }, modifier = Modifier.fillMaxSize())
        VideoGestureOverlay(state, playback, Modifier.matchParentSize(), controls.toggle,
            onPrevious = playback::previous, onNext = playback::next)
        if (controls.visible) {
            Row(Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = back) { Icon(Icons.Default.ArrowBack, stringResource(R.string.close_player), tint = Color.White) }
                Column(Modifier.weight(1f)) {
                    Text(song.title, color = Color.White, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${song.folder} · ${duration(progress.second)}", color = Color(0xFFCCC8D2),
                        fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                SleepTimerIndicator(state.sleepMinutes, endAtMillis = state.sleepEndAtMs,
                    showLabel = false)
                IconButton(onClick = { onAudioOnly(song) }) {
                    Icon(Icons.Default.Headphones, stringResource(R.string.audio_only), tint = StonePalette.Yellow)
                }
                IconButton(onClick = onOptions) {
                    Icon(Icons.Default.MoreVert, stringResource(R.string.more_options), tint = Color.White)
                }
            }
            VideoTransportControls(state, progress, playback, Modifier.align(Alignment.Center))
            Column(Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                .background(Color(0xAA0D0C11)).padding(horizontal = 18.dp, vertical = 12.dp)) {
                VideoTimeline(song, progress, playback)
                VideoActionRow(
                    onAudioOnly = { onAudioOnly(song) },
                    onOptions = onOptions,
                    onQueue = onQueue,
                    onClearScreen = controls.toggle,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

private data class VideoControlsState(val visible: Boolean, val toggle: () -> Unit)

@Composable private fun rememberVideoControls(songId: String, playing: Boolean): VideoControlsState {
    var visible by remember(songId) { mutableStateOf(false) }
    LaunchedEffect(visible, playing) {
        if (visible && playing) {
            delay(3_500)
            visible = false
        }
    }
    return VideoControlsState(visible) { visible = !visible }
}

@Composable private fun VideoTransportControls(state: PlaybackState, progress: Pair<Long, Long>,
    playback: PlaybackViewModel, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        IconButton(onClick = { seekVideoBy(playback, progress, -10_000L) }) {
            Icon(Icons.Default.Replay10, "Retroceder 10 segundos", tint = Color.White, modifier = Modifier.size(32.dp))
        }
        IconButton(onClick = playback::previous) {
            Icon(Icons.Default.SkipPrevious, stringResource(R.string.previous), tint = Color.White, modifier = Modifier.size(34.dp))
        }
        FilledIconButton(onClick = playback::toggle, modifier = Modifier.size(68.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = StonePalette.Yellow, contentColor = StonePalette.Background)) {
            Icon(if (state.playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                stringResource(if (state.playing) R.string.pause else R.string.play), modifier = Modifier.size(38.dp))
        }
        IconButton(onClick = { seekVideoBy(playback, progress, 10_000L) }) {
            Icon(Icons.Default.Forward10, "Avanzar 10 segundos", tint = Color.White, modifier = Modifier.size(32.dp))
        }
        IconButton(onClick = playback::next) {
            Icon(Icons.Default.SkipNext, stringResource(R.string.next), tint = Color.White, modifier = Modifier.size(34.dp))
        }
    }
}

private fun seekVideoBy(playback: PlaybackViewModel, progress: Pair<Long, Long>, deltaMs: Long) {
    if (progress.second > 0) playback.seek((progress.first + deltaMs).coerceIn(0L, progress.second))
}

@Composable private fun VideoTimeline(song: Song, progress: Pair<Long, Long>, playback: PlaybackViewModel) {
    var framesVisible by remember(song.id, progress.second) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        if (framesVisible) {
            VideoFrameStrip(song, progress, playback, Modifier.fillMaxWidth().padding(bottom = 4.dp))
        }
        PlayerProgress(progress, playback, onVerticalDrag = { dragAmount ->
            when {
                dragAmount < -8f -> framesVisible = true
                dragAmount > 8f -> framesVisible = false
            }
        })
    }
}

@Composable private fun VideoActionRow(onAudioOnly: () -> Unit, onOptions: (() -> Unit)? = null,
    onQueue: (() -> Unit)? = null, onClearScreen: (() -> Unit)? = null,
    modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onAudioOnly) {
            Icon(Icons.Default.Headphones, stringResource(R.string.audio_only), tint = Color.White)
        }
        onOptions?.let { action ->
            IconButton(onClick = action) {
                Icon(Icons.Default.Tune, stringResource(R.string.more_options), tint = Color.White)
            }
        }
        onQueue?.let { action ->
            IconButton(onClick = action) {
                Icon(Icons.Default.QueueMusic, stringResource(R.string.queue), tint = Color.White)
            }
        }
        onClearScreen?.let { action ->
            IconButton(onClick = action) {
                Icon(Icons.Default.Fullscreen, "Limpiar pantalla", tint = Color.White)
            }
        }
    }
}

private fun coverBackgroundColor(bitmap: android.graphics.Bitmap): Color {
    var red = 0L; var green = 0L; var blue = 0L; var count = 0L
    val stepX = (bitmap.width / 24).coerceAtLeast(1)
    val stepY = (bitmap.height / 24).coerceAtLeast(1)
    for (y in 0 until bitmap.height step stepY) for (x in 0 until bitmap.width step stepX) {
        val pixel = bitmap.getPixel(x, y)
        red += android.graphics.Color.red(pixel); green += android.graphics.Color.green(pixel)
        blue += android.graphics.Color.blue(pixel); count++
    }
    val darken = .52f
    return Color((red / count / 255f * darken).toFloat().coerceIn(.035f, .25f),
        (green / count / 255f * darken).toFloat().coerceIn(.035f, .25f),
        (blue / count / 255f * darken).toFloat().coerceIn(.035f, .25f))
}

/**
 * A bounded, YouTube-like preview strip. Frames are loaded lazily and only while the video
 * controls are visible; dragging the strip seeks the same player instead of creating a second
 * player or decoder.
 */
@Composable private fun VideoFrameStrip(song: Song, progress: Pair<Long, Long>,
    playback: PlaybackViewModel, modifier: Modifier) {
    val durationMs = progress.second
    if (durationMs <= 0L) return
    val context = LocalContext.current
    val framePositions = remember(song.id, song.modifiedMs, durationMs) {
        List(8) { index -> durationMs * index / 7L }
    }
    var frames by remember(song.id, song.modifiedMs, durationMs) {
        mutableStateOf(List(framePositions.size) { ArtworkLoader.VideoFrame(null, 0, 0) })
    }
    LaunchedEffect(song.id, song.modifiedMs, durationMs) {
        framePositions.forEachIndexed { index, positionMs ->
            val frame = ArtworkLoader.loadVideoFrameAt(
                context.applicationContext, song.uri, song.modifiedMs, positionMs, maxDimension = 180
            )
            frames = frames.toMutableList().also { it[index] = frame }
        }
    }
    val currentFraction = (progress.first.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    Box(
        modifier.height(54.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, StonePalette.Yellow.copy(alpha = .65f), RoundedCornerShape(8.dp))
            .pointerInput(song.id, durationMs) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var moved = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (!change.pressed) break
                        val horizontal = change.position.x - down.position.x
                        if (!moved && abs(horizontal) > viewConfiguration.touchSlop) moved = true
                        if (moved) {
                            val fraction = (change.position.x / size.width.toFloat().coerceAtLeast(1f))
                                .coerceIn(0f, 1f)
                            // The thumbnail strip is deliberately a light-weight seek affordance;
                            // the actual seek is still performed by the shared PlaybackViewModel.
                            // The gesture is consumed only after it becomes a drag.
                            change.consume()
                            playback.seek((durationMs * fraction).toLong().coerceIn(0L, durationMs))
                        }
                    }
                }
            }
    ) {
        Row(Modifier.fillMaxSize()) {
            frames.forEachIndexed { index, frame ->
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    frame.bitmap?.let { bitmap ->
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } ?: Box(Modifier.fillMaxSize().background(Color(0x4417171B)))
                    val start = index.toFloat() / framePositions.size
                    val end = (index + 1).toFloat() / framePositions.size
                    if (currentFraction in start..end ||
                        (index == framePositions.lastIndex && currentFraction == 1f)) {
                        Box(Modifier.fillMaxHeight().width(2.dp)
                            .background(StonePalette.Yellow).align(Alignment.CenterStart))
                    }
                    Box(Modifier.fillMaxHeight().width(.5.dp)
                        .background(Color(0x553A353D)).align(Alignment.CenterEnd))
                }
            }
        }
    }
}

private enum class VideoAdjustmentSide { Brightness, Volume }
private data class VideoAdjustment(val side: VideoAdjustmentSide, val value: Float)

/** Handles vertical brightness/volume adjustments and horizontal track changes. */
@Composable private fun VideoGestureOverlay(state: PlaybackState, playback: PlaybackViewModel,
    modifier: Modifier, onTap: () -> Unit, onPrevious: () -> Unit, onNext: () -> Unit) {
    val context = LocalContext.current
    var brightness by remember { mutableStateOf(currentWindowBrightness(context)) }
    var volume by remember { mutableStateOf(state.volumePercent.toFloat().coerceIn(0f, 300f)) }
    var adjustment by remember { mutableStateOf<VideoAdjustment?>(null) }
    LaunchedEffect(state.volumePercent) { volume = state.volumePercent.toFloat().coerceIn(0f, 300f) }
    LaunchedEffect(adjustment) {
        if (adjustment != null) {
            delay(850)
            adjustment = null
        }
    }
    val currentTap = rememberUpdatedState(onTap)
    val currentAdjustment = rememberUpdatedState(newValue = { leftSide: Boolean, delta: Float ->
        if (leftSide) {
            brightness = (brightness + delta).coerceIn(.02f, 1f)
            setWindowBrightness(context, brightness)
            adjustment = VideoAdjustment(VideoAdjustmentSide.Brightness, brightness)
        } else {
            volume = (volume + delta * 300f).coerceIn(0f, 300f)
            StonePlaybackService.setVolumePercent(volume.roundToInt(), showOverlay = false)
            adjustment = VideoAdjustment(VideoAdjustmentSide.Volume, volume / 300f)
        }
    })
    Box(modifier.pointerInput(Unit) {
        detectVideoGestures(
            onTap = { currentTap.value() },
            onVerticalChange = { leftSide, delta -> currentAdjustment.value(leftSide, delta) },
            onHorizontalSwipe = { deltaX ->
                if (deltaX < 0f) onNext() else onPrevious()
            }
        )
    }) {
        adjustment?.let { VideoAdjustmentIndicator(it, Modifier.fillMaxSize()) }
    }
}

@Composable private fun VideoAdjustmentIndicator(adjustment: VideoAdjustment, modifier: Modifier) {
    val left = adjustment.side == VideoAdjustmentSide.Brightness
    Box(
        modifier = modifier.padding(horizontal = 18.dp),
        contentAlignment = if (left) Alignment.CenterStart else Alignment.CenterEnd
    ) {
        Column(
            modifier = Modifier.width(52.dp).height(176.dp)
                .background(Color(0xCC17151B), RoundedCornerShape(26.dp))
                .border(1.dp, StonePalette.Yellow.copy(alpha = .55f), RoundedCornerShape(26.dp))
                .padding(vertical = 14.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                if (left) Icons.Default.WbSunny else Icons.Default.VolumeUp,
                contentDescription = if (left) "Brillo" else "Volumen",
                tint = StonePalette.Yellow,
                modifier = Modifier.size(20.dp)
            )
            Box(Modifier.weight(1f).width(7.dp).clip(RoundedCornerShape(4.dp))
                .background(Color(0x663A353D))) {
                Box(Modifier.fillMaxWidth().fillMaxHeight(adjustment.value.coerceIn(0f, 1f))
                    .align(Alignment.BottomCenter).background(StonePalette.Yellow))
            }
            val percentage = if (left) adjustment.value * 100f else adjustment.value * 300f
            Text("${percentage.roundToInt()}%", color = Color.White,
                fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}

private suspend fun PointerInputScope.detectVideoGestures(onTap: () -> Unit,
    onVerticalChange: (leftSide: Boolean, delta: Float) -> Unit,
    onHorizontalSwipe: (Float) -> Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var totalX = 0f
        var totalY = 0f
        var moved = false
        var horizontalTriggered = false
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull() ?: break
            if (!change.pressed) break
            val delta = change.positionChange()
            totalX += delta.x
            totalY += delta.y
            if (!moved && maxOf(abs(totalX), abs(totalY)) > viewConfiguration.touchSlop) moved = true
            if (moved) {
                if (abs(totalY) >= abs(totalX)) {
                    val normalized = (-delta.y / size.height.toFloat().coerceAtLeast(1f))
                        .coerceIn(-.12f, .12f)
                    onVerticalChange(down.position.x < size.width / 2f, normalized)
                } else if (!horizontalTriggered &&
                    abs(totalX) >= maxOf(viewConfiguration.touchSlop * 2.5f, density * 64f)) {
                    horizontalTriggered = true
                    onHorizontalSwipe(totalX)
                }
                change.consume()
            }
        }
        if (!moved) onTap()
    }
}

/** Changes tracks only after a deliberate horizontal swipe; vertical gestures are ignored. */
private suspend fun PointerInputScope.detectTrackSwipe(onPrevious: () -> Unit, onNext: () -> Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var totalX = 0f
        var totalY = 0f
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull() ?: break
            if (!change.pressed || change.isConsumed) break
            val delta = change.positionChange()
            totalX += delta.x
            totalY += delta.y
            val distance = maxOf(abs(totalX), abs(totalY))
            if (distance > viewConfiguration.touchSlop) {
                if (abs(totalY) > abs(totalX)) break
                if (abs(totalX) >= maxOf(viewConfiguration.touchSlop * 2.5f, density * 64f)) {
                    change.consume()
                    if (totalX < 0f) onNext() else onPrevious()
                    break
                }
            }
        }
    }
}

private fun currentWindowBrightness(context: Context): Float {
    val value = context.findActivity()?.window?.attributes?.screenBrightness ?: -1f
    return if (value in .02f..1f) value else .5f
}

private fun setWindowBrightness(context: Context, value: Float) {
    context.findActivity()?.let { activity ->
        val attributes = activity.window.attributes
        attributes.screenBrightness = value.coerceIn(.02f, 1f)
        activity.window.attributes = attributes
    }
}

@Composable private fun LandscapeVideoPlayer(song: Song, state: PlaybackState,
    progress: Pair<Long, Long>, playback: PlaybackViewModel, back: () -> Unit,
    onOptions: () -> Unit, onQueue: () -> Unit,
    onAudioOnly: (Song) -> Unit) {
    val controls = rememberVideoControls(song.id, state.playing)
    Row(Modifier.fillMaxSize().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1.55f).fillMaxHeight()) {
            AndroidView(factory = { context ->
                PlayerView(context).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    player = playback.player()
                }
            }, update = { it.player = playback.player() },
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(18.dp)))
            VideoGestureOverlay(state, playback, Modifier.matchParentSize(), controls.toggle,
                onPrevious = playback::previous, onNext = playback::next)
        }
        Box(Modifier.weight(1f).fillMaxHeight()) {
            Box(Modifier.matchParentSize().pointerInput(Unit) {
                detectTapGestures(onTap = { controls.toggle() })
            })
            if (controls.visible) {
                Column(Modifier.fillMaxSize().padding(start = 18.dp, end = 8.dp, top = 4.dp, bottom = 2.dp),
                    verticalArrangement = Arrangement.SpaceBetween) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = back) { Icon(Icons.Default.KeyboardArrowDown,
                            stringResource(R.string.close_player), Modifier.size(30.dp)) }
                        Text(song.title, Modifier.weight(1f), textAlign = TextAlign.Center,
                            color = StonePalette.Muted, fontWeight = FontWeight.Bold)
                        SleepTimerIndicator(state.sleepMinutes, endAtMillis = state.sleepEndAtMs,
                            showLabel = false)
                        IconButton(onClick = { onAudioOnly(song) }) {
                            Icon(Icons.Default.Headphones, stringResource(R.string.audio_only), tint = StonePalette.Yellow)
                        }
                        IconButton(onClick = onOptions) { Icon(Icons.Default.MoreVert,
                            stringResource(R.string.more_options)) }
                    }
                    Column(Modifier.fillMaxWidth()) {
                        Text(state.title, fontSize = 21.sp, fontWeight = FontWeight.Black,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(song.folder, color = StonePalette.Muted, maxLines = 1,
                            overflow = TextOverflow.Ellipsis)
                    }
                    VideoTimeline(song, progress, playback)
                    VideoTransportControls(state, progress, playback,
                        Modifier.fillMaxWidth().padding(vertical = 4.dp))
                    VideoActionRow(
                        onAudioOnly = { onAudioOnly(song) },
                        onOptions = onOptions,
                        onQueue = onQueue,
                        onClearScreen = controls.toggle
                    )
                }
            }
        }
    }
}

@Composable private fun LandscapeAudioPlayer(state: PlaybackState, song: Song?,
    progress: Pair<Long, Long>, playback: PlaybackViewModel, back: () -> Unit,
    onOptions: () -> Unit, onLyrics: () -> Unit, onEqualizer: () -> Unit, onQueue: () -> Unit) {
    Row(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(.9f).fillMaxHeight().padding(8.dp), contentAlignment = Alignment.Center) {
            if (song != null) LandscapeRecord(song, state.playing, Modifier.fillMaxHeight().aspectRatio(1f))
        }
        Column(Modifier.weight(1.1f).fillMaxHeight().padding(start = 18.dp, end = 14.dp, top = 4.dp, bottom = 2.dp),
            verticalArrangement = Arrangement.SpaceBetween) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = back) { Icon(Icons.Default.KeyboardArrowDown,
                    stringResource(R.string.close_player), Modifier.size(32.dp)) }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.player_now), color = StonePalette.Muted,
                        fontSize = 11.sp, letterSpacing = 1.5.sp)
                    Text(state.artist.uppercase(), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                IconButton(onClick = onOptions) { Icon(Icons.Default.MoreVert,
                    stringResource(R.string.more_options)) }
            }
            Column(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(state.title, fontSize = 24.sp, fontWeight = FontWeight.Black,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(state.artist, color = StonePalette.Muted, maxLines = 1,
                            overflow = TextOverflow.Ellipsis)
                    }
                    IconToggleButton(state.favorite, { playback.toggleFavorite() }) {
                        Icon(if (state.favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            stringResource(R.string.favorite), tint = if (state.favorite) StonePalette.Yellow else StonePalette.Secondary)
                    }
                }
                CurrentLandscapeLyric(song, progress.first)
            }
            PlayerProgress(progress, playback)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically) {
                IconButton(playback::previous) { Icon(Icons.Default.SkipPrevious,
                    stringResource(R.string.previous), Modifier.size(42.dp)) }
                FilledIconButton(playback::toggle, Modifier.size(58.dp)) {
                    Icon(if (state.playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                        stringResource(if (state.playing) R.string.pause else R.string.play), Modifier.size(36.dp))
                }
                IconButton(playback::next) { Icon(Icons.Default.SkipNext,
                    stringResource(R.string.next), Modifier.size(42.dp)) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onEqualizer) { Icon(Icons.Default.Tune, stringResource(R.string.equalizer)) }
                FilledTonalButton(onClick = onLyrics) { Icon(Icons.Default.ChatBubbleOutline, null); Text(stringResource(R.string.lyrics)) }
                IconButton(onClick = onQueue) { Icon(Icons.Default.QueueMusic, stringResource(R.string.queue)) }
            }
        }
    }
}

@Composable private fun CurrentLandscapeLyric(song: Song?, positionMs: Long) {
    val lyric = rememberCurrentLyric(song, positionMs).second
    Text(lyric.ifBlank { stringResource(R.string.no_lyrics) },
        Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp),
        color = if (lyric.isBlank()) StonePalette.Muted else Color(0xFFD8E5E3),
        fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 2,
        overflow = TextOverflow.Ellipsis, lineHeight = 23.sp)
}

@Composable private fun rememberCurrentLyric(song: Song?, positionMs: Long): Pair<Int, String> {
    val context = LocalContext.current.applicationContext
    val overrides = remember(context) { LyricsOverrides(context) }
    val custom by produceState<String?>(initialValue = null, song?.id) {
        val id = song?.id ?: return@produceState
        value = withContext(Dispatchers.IO) { overrides.get(id) }
    }
    val document = remember(song?.id, song?.lyrics, song?.syncedLyrics, custom) {
        custom?.let(LyricsParser::parse)
            ?: song?.lyricsDocument()
            ?: LyricsDocument(emptyList(), emptyList())
    }
    val active = LyricsParser.activeIndex(document.synced, positionMs)
    val index = if (document.synced.isEmpty()) 0 else active
    val line = if (document.synced.isNotEmpty()) document.synced.getOrNull(index)?.text.orEmpty()
        else document.plain.firstOrNull().orEmpty()
    return index to line
}

@Composable private fun CurrentLyricChip(song: Song?, positionMs: Long, onClick: () -> Unit,
    modifier: Modifier = Modifier) {
    val (lineIndex, line) = rememberCurrentLyric(song, positionMs)
    Surface(onClick = onClick, modifier = modifier.height(56.dp),
        shape = RoundedCornerShape(28.dp), color = Color(0xFF242633)) {
        Row(Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.ChatBubbleOutline, stringResource(R.string.lyrics),
                tint = StonePalette.Secondary, modifier = Modifier.size(18.dp))
            Box(Modifier.weight(1f).padding(start = 8.dp), contentAlignment = Alignment.Center) {
                AnimatedContent(targetState = lineIndex to line, transitionSpec = {
                    (slideInVertically { it / 2 } + fadeIn()).togetherWith(
                        slideOutVertically { -it / 2 } + fadeOut())
                }, label = "current-lyric") { index ->
                    Text(index.second.ifBlank { stringResource(R.string.lyrics) },
                        color = if (line.isBlank()) StonePalette.Secondary else Color(0xFFD8E5E3),
                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2,
                        overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                        lineHeight = 16.sp)
                }
            }
        }
    }
}

@Composable private fun LandscapeRecord(song: Song, playing: Boolean, modifier: Modifier = Modifier) {
    val angle = remember { Animatable(0f) }
    LaunchedEffect(playing) {
        if (playing) while (true) angle.animateTo(angle.value + 360f,
            animationSpec = tween(18_000, easing = LinearEasing))
    }
    val rotation = angle.value
    Box(modifier.graphicsLayer { rotationZ = if (playing) rotation else 0f }
        .clip(CircleShape).background(Brush.radialGradient(listOf(
            Color(0xFF285D58), Color(0xFF123B38), Color(0xFF092A29), Color(0xFF071C1C))))
        .padding(12.dp), contentAlignment = Alignment.Center) {
        repeat(5) { index ->
            Box(Modifier.fillMaxSize().padding((index * 9 + 4).dp)
                .border(1.dp, Color(0x337FA8A1), CircleShape))
        }
        StoneArtwork(song.artworkSource, song.modifiedMs, Modifier.fillMaxSize(.54f).clip(CircleShape))
        Box(Modifier.fillMaxSize(.045f).clip(CircleShape).background(Color(0xFF101719)))
    }
}
