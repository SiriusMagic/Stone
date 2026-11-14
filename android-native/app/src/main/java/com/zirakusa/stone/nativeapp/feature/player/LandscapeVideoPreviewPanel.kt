@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.zirakusa.stone.nativeapp.feature.player

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.ui.PlayerView
import androidx.compose.ui.viewinterop.AndroidView
import com.zirakusa.stone.core.Song
import com.zirakusa.stone.nativeapp.R
import com.zirakusa.stone.nativeapp.data.library.ArtworkLoader
import com.zirakusa.stone.nativeapp.playback.PlaybackState
import com.zirakusa.stone.nativeapp.playback.PlaybackViewModel
import com.zirakusa.stone.nativeapp.ui.theme.StonePalette

/** Video-specific preview used by the horizontal library layout. */
@Composable fun LandscapeVideoPreviewPanel(
    state: PlaybackState,
    song: Song?,
    active: Boolean,
    progress: Pair<Long, Long>,
    playback: PlaybackViewModel,
    videoScale: VideoScale,
    controls: VideoControlSettings,
    onSelectVideo: (Song) -> Unit,
    onOpenFullscreen: () -> Unit,
    onAudioOnly: (Song) -> Unit,
    onOptions: () -> Unit,
    onQueue: () -> Unit,
    onScaleToggle: () -> Unit,
) {
    Surface(
        Modifier.fillMaxSize().testTag("home-video-player"),
        shape = RoundedCornerShape(20.dp),
        color = StonePalette.Background,
    ) {
        if (song == null) {
            VideoPreviewPlaceholder()
        } else {
            VideoPreviewContent(
                state = state,
                song = song,
                active = active,
                progress = progress,
                playback = playback,
                videoScale = videoScale,
                controls = controls,
                onSelectVideo = onSelectVideo,
                onOpenFullscreen = onOpenFullscreen,
                onAudioOnly = { onAudioOnly(song) },
                onOptions = onOptions,
                onQueue = onQueue,
                onScaleToggle = onScaleToggle,
            )
        }
    }
}

@Composable private fun VideoPreviewPlaceholder() {
    Box(
        Modifier.fillMaxSize().background(
            Brush.radialGradient(
                colors = listOf(Color(0xFF3A2C60), Color(0xFF111017), Color(0xFF09090D)),
            )
        ),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Tv, null, Modifier.size(64.dp), tint = StonePalette.Yellow)
            Text(
                stringResource(R.string.video_preview_empty),
                Modifier.padding(top = 12.dp),
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(R.string.video_preview_empty_hint),
                Modifier.padding(horizontal = 28.dp, vertical = 6.dp),
                color = Color(0xFFB6B0C0),
                style = MaterialTheme.typography.bodySmall,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@Composable private fun VideoPreviewContent(
    state: PlaybackState,
    song: Song,
    active: Boolean,
    progress: Pair<Long, Long>,
    playback: PlaybackViewModel,
    videoScale: VideoScale,
    controls: VideoControlSettings,
    onSelectVideo: (Song) -> Unit,
    onOpenFullscreen: () -> Unit,
    onAudioOnly: () -> Unit,
    onOptions: () -> Unit,
    onQueue: () -> Unit,
    onScaleToggle: () -> Unit,
) {
    var controlsVisible by remember(song.id) { mutableStateOf(true) }
    var locked by remember(song.id) { mutableStateOf(false) }
    val currentSelect by rememberUpdatedState(onSelectVideo)
    val currentOpen by rememberUpdatedState(onOpenFullscreen)
    val currentToggle = rememberUpdatedState { if (active) playback.toggle() else currentSelect(song) }
    val context = LocalContext.current.applicationContext
    val resolvedScale = rememberResolvedVideoScale(playback.player(), videoScale, true, song.id)
    var frame by remember(song.uri, song.modifiedMs) {
        mutableStateOf(ArtworkLoader.VideoFrame(null, 0, 0))
    }
    LaunchedEffect(song.uri, song.modifiedMs, active) {
        if (!active) frame = ArtworkLoader.loadVideoFrame(context, song.uri, song.modifiedMs)
    }
    val displayedProgress = if (active) progress else 0L to song.durationMs.coerceAtLeast(0L)

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 4.dp)) {
        if (controlsVisible) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                IconToggleButton(
                    checked = active && state.favorite,
                    onCheckedChange = { if (active) playback.toggleFavorite() },
                    enabled = active,
                ) {
                    Icon(
                        if (active && state.favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        stringResource(R.string.favorite),
                        tint = if (active && state.favorite) StonePalette.Yellow else StonePalette.Secondary,
                    )
                }
                IconButton(onClick = onOptions, enabled = active) {
                    Icon(Icons.Default.Tune, stringResource(R.string.more_options))
                }
            }
        }

        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF17151E)),
            contentAlignment = Alignment.Center,
        ) {
            if (active) {
                AndroidView(
                    factory = { viewContext ->
                        PlayerView(viewContext).apply {
                            useController = false
                            resizeMode = resolvedScale.resizeMode
                            player = playback.player()
                        }
                    },
                    update = {
                        it.player = playback.player()
                        it.resizeMode = resolvedScale.resizeMode
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                frame.bitmap?.let {
                    Image(
                        it.asImageBitmap(),
                        null,
                        Modifier.fillMaxSize(),
                        contentScale = when (videoScale.resolve(frame.width, frame.height, true)) {
                            VideoScale.FILL -> ContentScale.FillBounds
                            VideoScale.ZOOM -> ContentScale.Crop
                            else -> ContentScale.Fit
                        },
                    )
                }
            }
            Box(
                Modifier.matchParentSize().pointerInput(song.id, active, locked) {
                    detectTapGestures(
                        onTap = { currentToggle.value() },
                        onDoubleTap = {
                            if (!active) currentSelect(song)
                            currentOpen()
                        },
                        onLongPress = { if (!locked) controlsVisible = !controlsVisible },
                    )
                }
            )
            if (!active || !state.playing) {
                FilledIconButton(
                    onClick = { currentToggle.value() },
                    modifier = Modifier.size(58.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = StonePalette.Yellow,
                        contentColor = StonePalette.Background,
                    ),
                ) {
                    Icon(
                        if (active && state.playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                        stringResource(if (active && state.playing) R.string.pause else R.string.play),
                        Modifier.size(32.dp),
                    )
                }
            }
            if (locked) {
                IconButton(
                    onClick = { locked = false; controlsVisible = true },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp)
                        .background(Color(0xCC111017), RoundedCornerShape(12.dp)),
                ) {
                    Icon(Icons.Default.Lock, stringResource(R.string.video_unlock), tint = StonePalette.Yellow)
                }
            }
        }

        if (controlsVisible) {
            Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Bottom) {
                Text(
                    song.title,
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    song.artist.ifBlank { song.folder },
                    Modifier.fillMaxWidth().padding(top = 2.dp),
                    color = StonePalette.Secondary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                VideoPreviewTimeline(displayedProgress, playback, active)
                Row(
                    Modifier.fillMaxWidth().padding(top = 2.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = playback::previous, enabled = active) {
                        Icon(Icons.Default.SkipPrevious, stringResource(R.string.previous), Modifier.size(34.dp))
                    }
                    FilledIconButton(
                        onClick = { currentToggle.value() },
                        enabled = active || song.isVideo,
                        modifier = Modifier.size(54.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = StonePalette.Yellow,
                            contentColor = StonePalette.Background,
                        ),
                    ) {
                        Icon(
                            if (active && state.playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                            stringResource(if (active && state.playing) R.string.pause else R.string.play),
                            Modifier.size(30.dp),
                        )
                    }
                    IconButton(onClick = playback::next, enabled = active) {
                        Icon(Icons.Default.SkipNext, stringResource(R.string.next), Modifier.size(34.dp))
                    }
                }
                if (active) {
                    VideoControlRow(
                        controls.visibleIds,
                        onAudioOnly = onAudioOnly,
                        onOptions = onOptions,
                        onQueue = onQueue,
                        onScaleToggle = onScaleToggle,
                        onLock = { locked = true; controlsVisible = false },
                        onFullscreen = onOpenFullscreen,
                    )
                }
            }
        } else {
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable private fun VideoPreviewTimeline(
    progress: Pair<Long, Long>,
    playback: PlaybackViewModel,
    active: Boolean,
) {
    if (active) {
        PlayerProgress(progress, playback)
    } else {
        Slider(
            value = 0f,
            onValueChange = {},
            valueRange = 0f..progress.second.coerceAtLeast(1L).toFloat(),
            enabled = false,
            modifier = Modifier.fillMaxWidth().height(40.dp),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(duration(0L), style = MaterialTheme.typography.labelSmall)
            Text(duration(progress.second), style = MaterialTheme.typography.labelSmall)
        }
    }
}
