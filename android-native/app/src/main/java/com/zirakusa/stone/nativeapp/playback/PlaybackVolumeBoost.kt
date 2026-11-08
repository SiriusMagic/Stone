package com.zirakusa.stone.nativeapp.playback

import android.media.audiofx.LoudnessEnhancer
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import kotlin.math.log10
import kotlin.math.roundToInt

/** A percentage above 100 maps to a logarithmic gain; 100% is true unity (0 dB). */
internal fun boostGainMillibels(percent: Int, allowed: Boolean): Int {
    val safePercent = percent.coerceIn(0, 300)
    return if (allowed && safePercent > 100)
        (20.0 * log10(safePercent / 100.0) * 100.0).roundToInt() else 0
}

/** Smooths gain in the player's audio session so crossing 100% cannot toggle the effect mid-sample. */
class PlaybackVolumeBoost {
    private val handler = Handler(Looper.getMainLooper())
    private var effect: LoudnessEnhancer? = null
    private var sessionId = 0
    private var appliedGainMb = 0
    private var targetGainMb = 0
    private var rampStartGainMb = 0
    private var rampStartedAtMs = 0L
    private var boostAllowed = false
    private val ramp = object : Runnable {
        override fun run() {
            val enhancer = effect ?: return
            val elapsed = (SystemClock.uptimeMillis() - rampStartedAtMs).coerceAtLeast(0L)
            val fraction = (elapsed / RAMP_DURATION_MS.toFloat()).coerceIn(0f, 1f)
            val gain = (rampStartGainMb + (targetGainMb - rampStartGainMb) * fraction).roundToInt()
            if (gain != appliedGainMb) {
                if (runCatching { enhancer.setTargetGain(gain) }.isFailure) return
                appliedGainMb = gain
            }
            if (fraction < 1f) handler.postDelayed(this, FRAME_MS)
            else if (!boostAllowed && appliedGainMb == 0) runCatching { enhancer.enabled = false }
        }
    }

    fun attach(audioSessionId: Int) {
        if (audioSessionId <= 0 || (audioSessionId == sessionId && effect != null)) return
        release()
        sessionId = audioSessionId
        effect = runCatching { LoudnessEnhancer(audioSessionId).apply { setTargetGain(0) } }.getOrNull()
    }

    fun setPercent(percent: Int, allowBoost: Boolean = true) {
        val value = percent.coerceIn(0, 300)
        val enhancer = effect ?: return
        boostAllowed = allowBoost
        val nextGain = boostGainMillibels(value, allowBoost)
        val isEnabled = runCatching { enhancer.enabled }.getOrElse { return }
        if (targetGainMb == nextGain && appliedGainMb == nextGain &&
            isEnabled == allowBoost) return
        handler.removeCallbacks(ramp)
        targetGainMb = nextGain
        rampStartGainMb = appliedGainMb
        rampStartedAtMs = SystemClock.uptimeMillis()
        // Keep the effect active at 0 dB in modes that allow boost. Enabling and
        // disabling it on every 100% crossing caused an audible transient.
        if (allowBoost && !isEnabled) {
            if (runCatching { enhancer.enabled = true }.isFailure) return
        }
        if (appliedGainMb != targetGainMb || (!allowBoost && isEnabled))
            handler.post(ramp)
    }

    fun release() {
        handler.removeCallbacks(ramp)
        runCatching { effect?.release() }
        effect = null
        sessionId = 0
        appliedGainMb = 0
        targetGainMb = 0
        boostAllowed = false
    }

    private companion object {
        const val RAMP_DURATION_MS = 160L
        const val FRAME_MS = 20L
    }
}
