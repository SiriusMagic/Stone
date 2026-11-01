package com.zirakusa.stone.nativeapp.playback

import android.media.audiofx.LoudnessEnhancer
import kotlin.math.log10
import kotlin.math.roundToInt

/** Applies gain inside the player's audio session; it never changes Android's media-stream volume. */
class PlaybackVolumeBoost {
    private var effect: LoudnessEnhancer? = null
    private var sessionId = 0

    fun attach(audioSessionId: Int) {
        if (audioSessionId <= 0 || audioSessionId == sessionId) return
        release()
        sessionId = audioSessionId
        effect = runCatching { LoudnessEnhancer(audioSessionId) }.getOrNull()
    }

    fun setPercent(percent: Int) = runCatching {
        val value = percent.coerceIn(0, 300)
        val enhancer = effect ?: return@runCatching
        enhancer.setTargetGain(if (value <= 100) 0 else
            (20.0 * log10(value / 100.0) * 1000.0).roundToInt())
        enhancer.enabled = value > 100
    }

    fun release() {
        runCatching { effect?.release() }
        effect = null
        sessionId = 0
    }
}
