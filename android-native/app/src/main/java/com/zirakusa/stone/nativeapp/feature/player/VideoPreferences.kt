@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.zirakusa.stone.nativeapp.feature.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.ui.AspectRatioFrameLayout

/** How the video is fitted inside its available bounds. */
enum class VideoScale(val key: String) {
    /** Resolve to zoom for horizontal sources and fit for vertical sources. */
    AUTO("auto"),
    FIT("fit"),
    ZOOM("zoom"),
    FILL("fill");

    val resizeMode: Int
        get() = when (this) {
            AUTO, FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
            ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
        }

    fun resolve(sourceWidth: Int, sourceHeight: Int, horizontalFallback: Boolean): VideoScale {
        if (this != AUTO) return this
        if (sourceWidth > 0 && sourceHeight > 0) return if (sourceWidth > sourceHeight) ZOOM else FIT
        return if (horizontalFallback) ZOOM else FIT
    }

    fun next(): VideoScale = when (this) {
        AUTO, FIT -> ZOOM
        ZOOM -> FILL
        FILL -> FIT
    }

    companion object {
        fun fromKey(value: String?): VideoScale = entries.firstOrNull { it.key == value } ?: AUTO
    }
}

/** Updates automatic framing when Media3 reports the actual source dimensions. */
@Composable
internal fun rememberResolvedVideoScale(player: Player?, requested: VideoScale,
    horizontalFallback: Boolean, key: Any?): VideoScale {
    var dimensions by remember(player, key) { mutableStateOf(0 to 0) }
    DisposableEffect(player, key) {
        if (player == null) {
            onDispose { }
        } else {
            val listener = object : Player.Listener {
                override fun onVideoSizeChanged(videoSize: VideoSize) {
                    dimensions = videoSize.width to videoSize.height
                }
            }
            dimensions = player.videoSize.width to player.videoSize.height
            player.addListener(listener)
            onDispose { player.removeListener(listener) }
        }
    }
    return requested.resolve(dimensions.first, dimensions.second, horizontalFallback)
}

/** The single gesture/action used to unlock the immersive video controls. */
enum class VideoUnlockMethod(val key: String) {
    BUTTON("button"),
    DOUBLE_TAP("double_tap"),
    SWIPE_UP("swipe_up");

    companion object {
        fun fromKey(value: String?): VideoUnlockMethod =
            entries.firstOrNull { it.key == value } ?: BUTTON
    }
}
