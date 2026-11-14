package com.zirakusa.stone.nativeapp.playback

import android.media.AudioManager
import android.os.Build
import kotlin.math.roundToInt

/**
 * Conversion between Android's discrete STREAM_MUSIC levels and Stone's
 * native 0..100% display. Android remains the source of truth in this range.
 */
internal data class AndroidVolumeRange(
    val minLevel: Int,
    val maxLevel: Int,
) {
    private val span: Int get() = (maxLevel - minLevel).coerceAtLeast(1)

    fun levelToPercent(level: Int): Int =
        (((level.coerceIn(minLevel, maxLevel) - minLevel) * 100f) / span)
            .roundToInt().coerceIn(0, 100)

    fun percentToLevel(percent: Int): Int =
        (minLevel + (span * percent.coerceIn(0, 100) / 100f).roundToInt())
            .coerceIn(minLevel, maxLevel)
}

internal fun AudioManager.streamMusicRange(): AndroidVolumeRange {
    val min = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        runCatching { getStreamMinVolume(AudioManager.STREAM_MUSIC) }.getOrDefault(0)
    } else 0
    val max = getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(min + 1)
    return AndroidVolumeRange(minLevel = min, maxLevel = max)
}
