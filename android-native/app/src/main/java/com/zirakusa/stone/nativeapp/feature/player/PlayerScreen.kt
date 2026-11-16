@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.zirakusa.stone.nativeapp.feature.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.produceState
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.graphics.get
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import com.zirakusa.stone.core.Song
import com.zirakusa.stone.nativeapp.R
import com.zirakusa.stone.nativeapp.playback.PlaybackState
import com.zirakusa.stone.nativeapp.playback.PlaybackViewModel
import com.zirakusa.stone.nativeapp.data.library.ArtworkLoader
import com.zirakusa.stone.nativeapp.data.library.StonePlaylist
import com.zirakusa.stone.nativeapp.ui.theme.StoneLook
import com.zirakusa.stone.nativeapp.ui.components.SleepTimerIndicator
import com.zirakusa.stone.nativeapp.ui.theme.StonePalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun PlayerScreen(state: PlaybackState, song: Song?, playback: PlaybackViewModel,
    back: () -> Unit, onDelete: (Song) -> Unit,
    onMetadataChanged: () -> Unit, playlists: List<StonePlaylist>,
    onCreatePlaylist: (String) -> Unit, onAddToPlaylist: (String, Song) -> Unit,
    modifier: Modifier = Modifier, onArtist: () -> Unit = {}, onRecommendations: () -> Unit = {},
    isPictureInPicture: Boolean = false,
    onAudioOnly: (Song) -> Unit = {},
    sheets: PlayerSheetsState = rememberPlayerSheetsState(),
    videoScale: VideoScale = VideoScale.AUTO,
    onVideoScaleChange: (VideoScale) -> Unit = {},
    videoControls: VideoControlSettings = VideoControlSettings.defaults()) {
    BackHandler(onBack = back)
    val context = LocalContext.current
    // Girar / pantalla completa lock the orientation; closing the player gives rotation back to the sensor.
    DisposableEffect(Unit) {
        onDispose { context.findActivity()?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }
    val dynamicBackground by produceState(
        Color(0xFF0D0C11), song?.id, song?.artworkSource, song?.modifiedMs, song?.isVideo
    ) {
        value = withContext(Dispatchers.IO) {
            val current = song ?: return@withContext Color(0xFF0D0C11)
            val artwork = ArtworkLoader.load(context.applicationContext,
                current.artworkSource, current.modifiedMs)
                ?: if (current.isVideo) ArtworkLoader.loadVideoFrame(
                    context.applicationContext, current.uri, current.modifiedMs
                ).bitmap else null
            artwork?.let(::coverBackgroundColor) ?: Color(0xFF0D0C11)
        }
    }
    val animatedBackground by animateColorAsState(dynamicBackground, animationSpec = tween(350),
        label = "player-background")
    if (isPictureInPicture && song?.isVideo == true) {
        PictureInPictureVideoPlayer(playback, videoScale)
        return
    }
    val progress = rememberPlayerProgress(state, playback)
    val audioControls = rememberPlaybackControls(song?.id.orEmpty())
    val audioSwipeModifier = if (song?.isVideo != true) Modifier.pointerInput(song?.id) {
        detectTrackSwipe(playback::previous, playback::next, onUpFromBottom = { sheets.lyricsVisible = true },
            onUpFromMiddle = onRecommendations)
    } else Modifier
    BoxWithConstraints(modifier.fillMaxSize().background(animatedBackground).then(audioSwipeModifier)) {
    // Decide by the window, not this box: while the player animates closed the box becomes
    // wide and short, which used to flash the landscape vinyl inside the mini player.
    val window = androidx.compose.ui.platform.LocalWindowInfo.current.containerSize
    val landscape = window.width > window.height && maxWidth > maxHeight && maxHeight > 220.dp
    if (landscape && song?.isVideo == true) {
        FullscreenVideoPlayer(song, state, progress, playback, close = back,
            onAudioOnly = onAudioOnly, videoScale = videoScale,
            onVideoScaleChange = onVideoScaleChange,
            videoControls = videoControls,
            onOptions = { sheets.optionsSong = null; sheets.optionsSidePanel = false; sheets.optionsVisible = true }, onQueue = { sheets.queueVisible = true },
            onSpeed = { sheets.tool = PlayerTool.Speed }, onTool = { sheets.tool = it },
        )
    } else if (landscape) {
        LandscapeVinylPlayer(state, song, progress, playback, back,
            onOptions = { sheets.optionsSong = null; sheets.optionsSidePanel = false; sheets.optionsVisible = true }, onLyrics = { sheets.lyricsVisible = true },
            onEqualizer = { sheets.tool = PlayerTool.Equalizer },
            onQueue = { sheets.queueVisible = true })
    } else if (song?.isVideo == true) PortraitVideoPlayer(song, state, progress, playback, back,
        onOptions = { sheets.optionsSong = null; sheets.optionsSidePanel = false; sheets.optionsVisible = true }, onQueue = { sheets.queueVisible = true },
        onAudioOnly = {
            onAudioOnly(it)
        }, videoScale = videoScale,
        onVideoScaleChange = onVideoScaleChange, videoControls = videoControls,
        onSpeed = { sheets.tool = PlayerTool.Speed })
    else {
    val artworkShape = StoneLook.artworkShape
    if (song != null && artworkShape == "fullscreen") FullscreenArtwork(song, animatedBackground)
    Column(Modifier.fillMaxSize().padding(horizontal = 28.dp).padding(bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().height(70.dp)
            .windowInsetsPadding(WindowInsets.displayCutout)) {
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
                    onClick = { sheets.tool = PlayerTool.Sleep })
            }
        }
        if (song?.isVideo == true) Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(factory = { context ->
                PlayerView(context).apply { useController = false; resizeMode = videoScale.resolve(0, 0, false).resizeMode
                    player = playback.player() }
            }, update = { it.player = playback.player(); it.resizeMode = videoScale.resolve(0, 0, false).resizeMode }, modifier = Modifier.fillMaxSize()
                .clip(RoundedCornerShape(20.dp)))
        }
        else if (song != null && artworkShape == "fullscreen") Box(Modifier.weight(1f).fillMaxWidth()
            .pointerInput(song.id) { detectTapGestures { audioControls.show() } })
        else if (song != null) PlayerArtwork(song, artworkShape, state.playing,
            Modifier.weight(1f).aspectRatio(1f).fillMaxWidth()
                .pointerInput(song.id) { detectTapGestures { audioControls.show() } })
        else Box(Modifier.weight(1f).aspectRatio(1f).fillMaxWidth()
            .pointerInput(Unit) { detectTapGestures { audioControls.show() } })
        Row(Modifier.fillMaxWidth().padding(top = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(state.title, fontSize = 25.sp, fontWeight = FontWeight.Black, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, color = PlayerCream)
                Text(state.artist, color = Color(0xFF8A848F), fontSize = 15.sp,
                    modifier = Modifier.padding(top = 5.dp).clickable(
                        enabled = state.artist.isNotBlank(), onClick = onArtist), maxLines = 1)
            }
            IconToggleButton(state.favorite, { playback.toggleFavorite() }) {
                Icon(if (state.favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    stringResource(R.string.favorite), tint = if (state.favorite) StonePalette.Yellow else Color(0xFFE8E6EE))
            }
            IconButton(onClick = { sheets.optionsSong = null; sheets.optionsVisible = true }) {
                Icon(Icons.Default.MoreVert, stringResource(R.string.more_options))
            }
        }
        AnimatedVisibility(audioControls.visible) {
            Column(Modifier.fillMaxWidth()) {
                PlayerProgress(progress, playback)
                Row(Modifier.fillMaxWidth().height(104.dp), horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically) {
                    IconButton(playback::previous, Modifier.size(78.dp)) {
                        Icon(Icons.Default.FastRewind, stringResource(R.string.previous), Modifier.size(64.dp), tint = PlayerCream)
                    }
                    IconButton(playback::toggle, Modifier.size(78.dp)) {
                        Icon(if (state.playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                            stringResource(if (state.playing) R.string.pause else R.string.play), Modifier.size(72.dp), tint = PlayerCream)
                    }
                    IconButton(playback::next, Modifier.size(78.dp)) {
                        Icon(Icons.Default.FastForward, stringResource(R.string.next), Modifier.size(64.dp), tint = PlayerCream)
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    IconButton(onClick = { sheets.tool = PlayerTool.Equalizer }) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Tune, stringResource(R.string.equalizer))
                            if (state.equalizerEnabled) Text("ON", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    CurrentLyricChip(song, progress.first, { sheets.lyricsVisible = true }, Modifier.weight(1f))
                    IconButton(onClick = { sheets.queueVisible = true }) { Icon(Icons.Default.Menu, stringResource(R.string.queue)) }
                }
            }
        }
    }
    }
    }
    val playerOptionExtras = if (song?.isVideo == true) listOf(
        PlayerOptionExtra(Icons.Default.Headphones, R.string.audio_only) {
            sheets.optionsVisible = false
            sheets.optionsSong = null
            onAudioOnly(song)
        }
    ) else emptyList()
    PlayerSheetsHost(sheets, state, song, playback, progress, playlists, onCreatePlaylist,
        onAddToPlaylist, onDelete, onMetadataChanged, onEditComplete = back,
        extraActions = playerOptionExtras, videoScale = videoScale, onVideoScaleChange = onVideoScaleChange)
}


/** Horizontal swipe changes tracks; a deliberate swipe up opens the lyrics ([onUp]). */
/**
 * Horizontal swipe changes tracks. A swipe up depends on where it starts: from the bottom area
 * (the Letras bar) it opens the lyrics, from the middle it opens the recommendations.
 */
private suspend fun PointerInputScope.detectTrackSwipe(onPrevious: () -> Unit, onNext: () -> Unit,
    onUpFromBottom: () -> Unit = {}, onUpFromMiddle: () -> Unit = {}) {
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
                if (abs(totalY) > abs(totalX)) {
                    if (totalY > 0f) break
                    if (-totalY >= maxOf(viewConfiguration.touchSlop * 3f, density * 72f)) {
                        change.consume()
                        if (down.position.y > size.height * .78f) onUpFromBottom() else onUpFromMiddle()
                        break
                    }
                    continue
                }
                if (abs(totalX) >= maxOf(viewConfiguration.touchSlop * 2.5f, density * 64f)) {
                    change.consume()
                    if (totalX < 0f) onNext() else onPrevious()
                    break
                }
            }
        }
    }
}

private val PlayerCream = Color(0xFFF1E3CC)
