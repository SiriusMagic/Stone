package com.zirakusa.stone.nativeapp.playback

import android.media.audiofx.LoudnessEnhancer
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
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
    private var lastError: String? = null
    private val ramp = object : Runnable {
        override fun run() {
            val enhancer = effect ?: return
            val elapsed = (SystemClock.uptimeMillis() - rampStartedAtMs).coerceAtLeast(0L)
            val fraction = (elapsed / RAMP_DURATION_MS.toFloat()).coerceIn(0f, 1f)
            val gain = (rampStartGainMb + (targetGainMb - rampStartGainMb) * fraction).roundToInt()
            if (gain != appliedGainMb) {
                val result = runCatching { enhancer.setTargetGain(gain) }
                if (result.isFailure) {
                    recordError("setTargetGain($gain)", result.exceptionOrNull())
                    return
                }
                appliedGainMb = gain
            }
            if (fraction < 1f) handler.postDelayed(this, FRAME_MS)
            else if (!boostAllowed && appliedGainMb == 0) {
                val disableResult = runCatching { enhancer.enabled = false }
                if (disableResult.isFailure) {
                    recordError("disable(session=$sessionId)", disableResult.exceptionOrNull())
                }
            }
        }
    }

    fun attach(audioSessionId: Int) {
        if (audioSessionId <= 0 || (audioSessionId == sessionId && effect != null)) return
        release()
        sessionId = audioSessionId
        val created = runCatching { LoudnessEnhancer(audioSessionId) }
        val enhancer = created.getOrNull()
        effect = enhancer
        if (created.isFailure) {
            recordError("attach(session=$audioSessionId)", created.exceptionOrNull())
            return
        }
        // A newly attached effect must start both at unity and disabled. This
        // prevents a session recreation from inheriting a previous gain.
        runCatching { enhancer?.setTargetGain(0) }
            .onFailure { recordError("attachTargetGain(session=$audioSessionId)", it) }
        runCatching { enhancer?.enabled = false }
            .onFailure { recordError("attachDisable(session=$audioSessionId)", it) }
    }

    fun setPercent(percent: Int, allowBoost: Boolean = true) {
        val value = percent.coerceIn(0, 300)
        val enhancer = effect ?: return
        // LoudnessEnhancer is completely off for the native Android range.
        // It is only enabled when Stone is explicitly above 100%.
        val shouldBoost = allowBoost && value > 100
        boostAllowed = shouldBoost
        val nextGain = boostGainMillibels(value, shouldBoost)
        val enabledResult = runCatching { enhancer.enabled }
        val isEnabled = enabledResult.getOrElse {
            recordError("readEnabled(session=$sessionId)", enabledResult.exceptionOrNull())
            return
        }
        if (targetGainMb == nextGain && appliedGainMb == nextGain &&
            isEnabled == shouldBoost) return
        handler.removeCallbacks(ramp)
        targetGainMb = nextGain
        rampStartGainMb = appliedGainMb
        rampStartedAtMs = SystemClock.uptimeMillis()
        if (shouldBoost && !isEnabled) {
            val enableResult = runCatching { enhancer.enabled = true }
            if (enableResult.isFailure) {
                recordError("enable(session=$sessionId)", enableResult.exceptionOrNull())
                return
            }
        }
        if (appliedGainMb != targetGainMb || (!shouldBoost && isEnabled))
            handler.post(ramp)
    }

    /** Read-only state used by the audio investigation; it does not touch the effect. */
    fun diagnostics(): String {
        val enhancer = effect ?: return "LoudnessEnhancer:none(session=$sessionId)"
        return runCatching {
            "LoudnessEnhancer(session=$sessionId,id=${enhancer.id},enabled=${enhancer.enabled}," +
                "targetGainMb=${enhancer.targetGain},appliedGainMb=$appliedGainMb," +
                "requestedGainMb=$targetGainMb,allowed=$boostAllowed," +
                "lastError=${lastError ?: "none"})"
        }.getOrElse { "LoudnessEnhancer:error(session=$sessionId,lastError=${lastError ?: "read_failed"})" }
    }

    /**
     * Diagnostic-only A/B operation. It removes the effect and cancels its
     * ramp without touching Android's stream volume or the player's volume.
     */
    fun disableForDiagnostics() {
        handler.removeCallbacks(ramp)
        effect?.let { enhancer ->
            runCatching { enhancer.setTargetGain(0) }
                .onFailure { recordError("diagnosticTargetGain(session=$sessionId)", it) }
            runCatching { enhancer.enabled = false }
                .onFailure { recordError("diagnosticDisable(session=$sessionId)", it) }
            runCatching { enhancer.release() }
                .onFailure { recordError("diagnosticRelease(session=$sessionId)", it) }
        }
        effect = null
        sessionId = 0
        appliedGainMb = 0
        targetGainMb = 0
        boostAllowed = false
    }

    fun release() {
        handler.removeCallbacks(ramp)
        effect?.let { enhancer ->
            runCatching { enhancer.setTargetGain(0) }
                .onFailure { recordError("releaseTargetGain(session=$sessionId)", it) }
            runCatching { enhancer.enabled = false }
                .onFailure { recordError("releaseDisable(session=$sessionId)", it) }
            runCatching { enhancer.release() }
                .onFailure { recordError("release(session=$sessionId)", it) }
        }
        effect = null
        sessionId = 0
        appliedGainMb = 0
        targetGainMb = 0
        boostAllowed = false
        lastError = null
    }

    private fun recordError(operation: String, error: Throwable?) {
        val detail = error?.javaClass?.simpleName ?: "unknown"
        lastError = "$operation:$detail"
        Log.w(AudioDiagnostics.TAG, "PlaybackVolumeBoost $lastError", error)
    }

    private companion object {
        const val RAMP_DURATION_MS = 160L
        const val FRAME_MS = 20L
    }
}
