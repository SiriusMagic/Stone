@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.zirakusa.stone.nativeapp.feature.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import androidx.media3.ui.AspectRatioFrameLayout
import com.zirakusa.stone.core.Song
import com.zirakusa.stone.core.LyricsDocument
import com.zirakusa.stone.core.LyricsParser
import com.zirakusa.stone.nativeapp.data.library.LyricsOverrides
import com.zirakusa.stone.nativeapp.R
import com.zirakusa.stone.nativeapp.playback.PlaybackState
import com.zirakusa.stone.nativeapp.playback.PlaybackViewModel
import com.zirakusa.stone.nativeapp.playback.StonePlaybackService
import com.zirakusa.stone.nativeapp.data.library.SongActions
import com.zirakusa.stone.nativeapp.data.library.ArtworkLoader
import com.zirakusa.stone.nativeapp.data.library.StonePlaylist
import com.zirakusa.stone.nativeapp.ui.components.StoneArtwork
import com.zirakusa.stone.nativeapp.ui.theme.StonePalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun PlayerScreen(state: PlaybackState, song: Song?, playback: PlaybackViewModel,
    back: () -> Unit, onHide: (Song) -> Unit, onDelete: (Song) -> Unit,
    onMetadataChanged: () -> Unit, playlists: List<StonePlaylist>,
    onCreatePlaylist: (String) -> Unit, onAddToPlaylist: (String, Song) -> Unit,
    modifier: Modifier = Modifier, onArtist: () -> Unit = {}) {
    BackHandler(onBack = back)
    val context = LocalContext.current
    var queueVisible by remember { mutableStateOf(false) }
    var lyricsVisible by remember { mutableStateOf(false) }
    var optionsVisible by remember { mutableStateOf(false) }
    var tool by remember { mutableStateOf<PlayerTool?>(null) }
    var energySaver by remember { mutableStateOf(false) }
    var videoFullscreen by remember { mutableStateOf(false) }
    val dynamicBackground by produceState(Color(0xFF0D0C11), song?.uri, song?.modifiedMs) {
        value = withContext(Dispatchers.IO) {
            song?.uri?.let { uri -> ArtworkLoader.load(context.applicationContext, uri, song.modifiedMs) }
                ?.let(::coverBackgroundColor) ?: Color(0xFF0D0C11)
        }
    }
    if (energySaver) {
        EnergySaverScreen(state, song, playback) { energySaver = false }
        return
    }
    if (videoFullscreen && song?.isVideo == true) {
        BackHandler { videoFullscreen = false }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(factory = { context ->
                PlayerView(context).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    player = playback.player()
                }
            }, update = { it.player = playback.player() }, modifier = Modifier.fillMaxSize())
            IconButton(onClick = { videoFullscreen = false }, modifier = Modifier
                .align(Alignment.BottomStart).padding(18.dp)
                .background(Color(0xFFE51C23), RoundedCornerShape(5.dp))) {
                Icon(Icons.Default.FullscreenExit, "Salir de pantalla completa", tint = Color.White)
            }
        }
        return
    }
    val progress = rememberPlayerProgress(state, playback)
    BoxWithConstraints(modifier.fillMaxSize().background(dynamicBackground)) {
    val landscape = maxWidth > maxHeight
    if (landscape && song?.isVideo == true) {
        LandscapeVideoPlayer(song, state, progress, playback, back,
            onOptions = { optionsVisible = true }, onEqualizer = { tool = PlayerTool.Equalizer },
            onQueue = { queueVisible = true }, onFullscreen = { videoFullscreen = true })
    } else if (landscape) {
        LandscapeAudioPlayer(state, song, progress, playback, back,
            onOptions = { optionsVisible = true }, onLyrics = { lyricsVisible = true },
            onEqualizer = { tool = PlayerTool.Equalizer },
            onQueue = { queueVisible = true })
    } else if (song?.isVideo == true) PortraitVideoPlayer(state, progress, playback, back,
        onOptions = { optionsVisible = true }, onFullscreen = { videoFullscreen = true })
    else Column(Modifier.fillMaxSize().padding(horizontal = 28.dp).padding(bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().height(70.dp)) {
            IconButton(onClick = back, Modifier.align(Alignment.CenterStart)) {
                Icon(Icons.Default.KeyboardArrowDown, stringResource(R.string.close_player), Modifier.size(36.dp))
            }
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.player_now), color = Color(0xFF8A848F), fontSize = 12.sp,
                    letterSpacing = 1.5.sp)
                Text(state.artist.uppercase(), fontWeight = FontWeight.Bold, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
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
            PlayerTool.Energy -> energySaver = true
            else -> tool = selected
        }
    }
    when (tool) {
        PlayerTool.Speed -> SpeedSheet(state, playback) { tool = null }
        PlayerTool.Equalizer -> EqualizerSheet(state, playback, song = song) { tool = null }
        PlayerTool.Visualizer -> MusicVisualizerSheet(StonePlaybackService.playbackAudioSessionId) { tool = null }
        PlayerTool.Sleep -> SleepSheet(state, playback) { tool = null }
        PlayerTool.Tags -> song?.let { TagEditorSheet(it, { tool = null }) {
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

@Composable private fun PortraitVideoPlayer(state: PlaybackState, progress: Pair<Long, Long>,
    playback: PlaybackViewModel, back: () -> Unit, onOptions: () -> Unit, onFullscreen: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { context ->
            PlayerView(context).apply {
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                player = playback.player()
            }
        }, update = { it.player = playback.player() }, modifier = Modifier.fillMaxSize())
        Row(Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton(onClick = onOptions) { Icon(Icons.Default.Settings, stringResource(R.string.more_options), tint = Color.White, modifier = Modifier.size(34.dp)) }
            IconButton(onClick = back) { Icon(Icons.Default.Close, stringResource(R.string.close_player), tint = Color.White, modifier = Modifier.size(38.dp)) }
        }
        IconButton(onClick = onFullscreen, modifier = Modifier.align(Alignment.Center)) {
            Icon(Icons.Default.Fullscreen, stringResource(R.string.more_options), tint = Color.White, modifier = Modifier.size(64.dp))
        }
        Column(Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(horizontal = 28.dp, vertical = 22.dp)) {
            Text(state.title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            PlayerProgress(progress, playback)
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically) {
                IconButton(playback::previous) { Icon(Icons.Default.SkipPrevious, stringResource(R.string.previous), tint = Color.White, modifier = Modifier.size(52.dp)) }
                IconButton(playback::toggle, Modifier.size(72.dp)) {
                    Icon(if (state.playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                        stringResource(if (state.playing) R.string.pause else R.string.play), tint = Color.White, modifier = Modifier.size(64.dp))
                }
                IconButton(playback::next) { Icon(Icons.Default.SkipNext, stringResource(R.string.next), tint = Color.White, modifier = Modifier.size(52.dp)) }
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

@Composable private fun LandscapeVideoPlayer(song: Song, state: PlaybackState,
    progress: Pair<Long, Long>, playback: PlaybackViewModel, back: () -> Unit,
    onOptions: () -> Unit, onEqualizer: () -> Unit, onQueue: () -> Unit,
    onFullscreen: () -> Unit) {
    Row(Modifier.fillMaxSize().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        AndroidView(factory = { context ->
            PlayerView(context).apply {
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                player = playback.player()
            }
        }, update = { it.player = playback.player() },
            modifier = Modifier.weight(1.55f).fillMaxHeight().clip(RoundedCornerShape(18.dp)))
        Column(Modifier.weight(1f).fillMaxHeight().padding(start = 18.dp, end = 8.dp, top = 4.dp, bottom = 2.dp),
            verticalArrangement = Arrangement.SpaceBetween) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = back) { Icon(Icons.Default.KeyboardArrowDown,
                    stringResource(R.string.close_player), Modifier.size(30.dp)) }
                Text(stringResource(R.string.videos), Modifier.weight(1f), textAlign = TextAlign.Center,
                    color = StonePalette.Muted, fontWeight = FontWeight.Bold)
                IconButton(onClick = onOptions) { Icon(Icons.Default.MoreVert,
                    stringResource(R.string.more_options)) }
            }
            Column(Modifier.fillMaxWidth()) {
                Text(state.title, fontSize = 21.sp, fontWeight = FontWeight.Black,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(song.folder, color = StonePalette.Muted, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
            }
            PlayerProgress(progress, playback)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically) {
                IconButton(playback::previous) { Icon(Icons.Default.SkipPrevious,
                    stringResource(R.string.previous), Modifier.size(38.dp)) }
                FilledIconButton(playback::toggle, Modifier.size(54.dp)) {
                    Icon(if (state.playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                        stringResource(if (state.playing) R.string.pause else R.string.play), Modifier.size(34.dp))
                }
                IconButton(playback::next) { Icon(Icons.Default.SkipNext,
                    stringResource(R.string.next), Modifier.size(38.dp)) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                IconButton(onClick = onFullscreen, modifier = Modifier
                    .background(Color(0xFFE51C23), RoundedCornerShape(5.dp))) {
                    Icon(Icons.Default.Fullscreen, "Reproducir en pantalla completa", tint = Color.White)
                }
                IconButton(onClick = onEqualizer) { Icon(Icons.Default.Tune, stringResource(R.string.equalizer)) }
                IconButton(onClick = onQueue) { Icon(Icons.Default.QueueMusic, stringResource(R.string.queue)) }
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
            ?: LyricsDocument(song?.lyrics.orEmpty(), song?.syncedLyrics.orEmpty())
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
