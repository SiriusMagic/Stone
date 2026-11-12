@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.zirakusa.stone.nativeapp.feature.player

import androidx.media3.ui.AspectRatioFrameLayout

/** The two presentation modes available for a landscape video. */
enum class VideoPresentation {
    IMMERSIVE,
    SPLIT,
}

/** How the video is fitted inside its available bounds. */
enum class VideoScale(val key: String) {
    FIT("fit"),
    ZOOM("zoom");

    val resizeMode: Int
        get() = when (this) {
            FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
            ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        }

    companion object {
        fun fromKey(value: String?): VideoScale = entries.firstOrNull { it.key == value } ?: FIT
    }
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
