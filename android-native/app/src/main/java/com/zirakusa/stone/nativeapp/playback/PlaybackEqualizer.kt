package com.zirakusa.stone.nativeapp.playback

import android.media.audiofx.Equalizer
import android.media.audiofx.DynamicsProcessing
import android.os.Build
import kotlin.math.roundToInt

class PlaybackEqualizer {
    private var effect: Equalizer? = null
    private var dynamics: DynamicsProcessing? = null
    private var sessionId = 0
    private var enabled = false
    private var values = IntArray(10)

    fun attach(nextSessionId: Int) {
        if (nextSessionId <= 0) return
        // A failed effect construction must remain retryable. Some Android audio routes expose
        // the session before the effect can be attached (especially after Bluetooth changes).
        if (nextSessionId == sessionId && effect != null) return
        if (nextSessionId != sessionId) release()
        sessionId = nextSessionId
        effect = runCatching { Equalizer(0, nextSessionId) }.getOrNull()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            dynamics = runCatching {
                val config = DynamicsProcessing.Config.Builder(
                    DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION, 2,
                    false, 0, false, 0, false, 0, true).build()
                DynamicsProcessing(0, nextSessionId, config)
            }.getOrNull()
        }
        apply()
    }

    fun configure(nextEnabled: Boolean, nextValues: IntArray) {
        enabled = nextEnabled
        values = IntArray(10) { nextValues.getOrElse(it) { 0 }.coerceIn(-10, 10) }
        apply()
    }

    private fun apply() = runCatching {
        val equalizer = effect ?: return@runCatching
        val bands = equalizer.numberOfBands.toInt().coerceAtLeast(1)
        val range = equalizer.bandLevelRange
        repeat(bands) { band ->
            val source = if (bands == 1) 0 else (band * (values.size - 1f) / (bands - 1f)).roundToInt()
            val value = values[source]
            val level = if (value < 0) value / 10f * -range[0] else value / 10f * range[1]
            equalizer.setBandLevel(band.toShort(), level.roundToInt().toShort())
        }
        equalizer.enabled = enabled
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) dynamics?.let { processor ->
            val active = enabled
            processor.setLimiterAllChannelsTo(DynamicsProcessing.Limiter(
                active, true, 0, 10f, 80f, 1f, -1f, 0f))
            processor.enabled = active
        }
    }

    fun release() {
        runCatching { effect?.release() }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) runCatching { dynamics?.release() }
        effect = null
        dynamics = null
        sessionId = 0
    }
}
