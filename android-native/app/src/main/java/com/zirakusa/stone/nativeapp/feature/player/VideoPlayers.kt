@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.zirakusa.stone.nativeapp.feature.player

import android.content.Context
import android.util.Rational
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.get
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import androidx.media3.common.Player
import com.zirakusa.stone.core.Song
import com.zirakusa.stone.nativeapp.playback.PlaybackState
import com.zirakusa.stone.nativeapp.playback.PlaybackViewModel
import kotlin.math.roundToInt

@Composable internal fun PictureInPictureVideoPlayer(playback: PlaybackViewModel, scale: VideoScale) {
    val resolvedScale = rememberResolvedVideoScale(playback.player(), scale, false, "pip")
    AndroidView(factory = { context ->
        PlayerView(context).apply {
            useController = false
            resizeMode = resolvedScale.resizeMode
            player = playback.player()
        }
    }, update = { it.player = playback.player(); it.resizeMode = resolvedScale.resizeMode },
        modifier = Modifier.fillMaxSize())
}

internal fun enterVideoPictureInPicture(context: Context, player: Player?) {
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

@Composable internal fun FullscreenVideoPlayer(song: Song, state: PlaybackState,
    progress: Pair<Long, Long>, playback: PlaybackViewModel, close: () -> Unit,
    onAudioOnly: (Song) -> Unit, videoScale: VideoScale,
    onVideoScaleChange: (VideoScale) -> Unit, videoControls: VideoControlSettings,
    onOptions: (() -> Unit)? = null,
    onQueue: (() -> Unit)? = null, onSpeed: () -> Unit = {}, onTool: (PlayerTool) -> Unit = {}) {
    val controls = rememberPlaybackControls(song.id)
    val resolvedScale = rememberResolvedVideoScale(playback.player(), videoScale, true, song.id)
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { context ->
            PlayerView(context).apply {
                useController = false
                resizeMode = resolvedScale.resizeMode
                player = playback.player()
            }
        }, update = { it.player = playback.player(); it.resizeMode = resolvedScale.resizeMode },
            modifier = Modifier.fillMaxSize())
        VideoGestureOverlay(state, playback, Modifier.matchParentSize(), controls.toggle,
            onDoubleTap = { leftSide ->
                seekVideoBy(playback, progress, if (leftSide) -10_000L else 10_000L)
            }, onPrevious = playback::previous, onNext = playback::next,
        )
        LandscapeVideoChrome(song, state, progress, playback, controls.visible, onBack = close,
            onAudioOnly = { onAudioOnly(song) }, onScaleToggle = { onVideoScaleChange(videoScale.next()) },
            onTool = onTool)
    }
}

@Composable internal fun PortraitVideoPlayer(song: Song, state: PlaybackState,
    progress: Pair<Long, Long>, playback: PlaybackViewModel, back: () -> Unit,
    onOptions: () -> Unit, onQueue: () -> Unit,
    onAudioOnly: (Song) -> Unit, videoScale: VideoScale,
    onVideoScaleChange: (VideoScale) -> Unit,
    videoControls: VideoControlSettings, onSpeed: () -> Unit = {}) {
    val controls = rememberPlaybackControls(song.id)
    val resolvedScale = rememberResolvedVideoScale(playback.player(), videoScale, false, song.id)
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { context ->
            PlayerView(context).apply {
                useController = false
                resizeMode = resolvedScale.resizeMode
                player = playback.player()
            }
        }, update = { it.player = playback.player(); it.resizeMode = resolvedScale.resizeMode },
            modifier = Modifier.fillMaxSize())
        VideoGestureOverlay(state, playback, Modifier.matchParentSize(), controls.toggle,
            onDoubleTap = { leftSide ->
                seekVideoBy(playback, progress, if (leftSide) -10_000L else 10_000L)
            }, onPrevious = playback::previous, onNext = playback::next,
        )
        PortraitVideoChrome(song, state, progress, playback, controls.visible, videoControls, onBack = back,
            onQueue = onQueue, onOptions = onOptions, onAudioOnly = { onAudioOnly(song) },
            onScaleToggle = { onVideoScaleChange(videoScale.next()) }, onSpeed = onSpeed)
    }
}

internal data class VideoControlsState(val visible: Boolean, val toggle: () -> Unit,
    val show: () -> Unit)

/** Controls stay visible; only a tap on the video toggles them (no automatic hiding). */
@Composable internal fun rememberPlaybackControls(songId: String): VideoControlsState {
    var visible by remember(songId) { mutableStateOf(true) }
    return VideoControlsState(visible, { visible = !visible }, { visible = true })
}

internal fun seekVideoBy(playback: PlaybackViewModel, progress: Pair<Long, Long>, deltaMs: Long) {
    if (progress.second > 0) playback.seek((progress.first + deltaMs).coerceIn(0L, progress.second))
}
