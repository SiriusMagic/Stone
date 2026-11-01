package com.zirakusa.stone

import android.media.audiofx.Equalizer
import kotlin.math.roundToInt

object StoneEqualizer {
  private var effect: Equalizer? = null
  private var enabled = false
  private var values = IntArray(10)

  fun attach(audioSessionId: Int) {
    release()
    if (audioSessionId <= 0) return
    runCatching { effect = Equalizer(0, audioSessionId) }
    apply()
  }

  fun configure(nextEnabled: Boolean, nextValues: IntArray) {
    enabled = nextEnabled
    values = IntArray(10) { nextValues.getOrElse(it) { 0 }.coerceIn(-10, 10) }
    apply()
  }

  private fun apply() {
    val equalizer = effect ?: return
    runCatching {
      val bands = equalizer.numberOfBands.toInt().coerceAtLeast(1)
      val range = equalizer.bandLevelRange
      repeat(bands) { band ->
        val source = if (bands == 1) 0 else (band * (values.size - 1f) / (bands - 1f)).roundToInt()
        val value = values[source]
        val level = if (value < 0) value / 10f * -range[0] else value / 10f * range[1]
        equalizer.setBandLevel(band.toShort(), level.roundToInt().toShort())
      }
      equalizer.enabled = enabled
    }
  }

  fun release() {
    runCatching { effect?.release() }
    effect = null
  }
}
