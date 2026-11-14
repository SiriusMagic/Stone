package com.zirakusa.stone.nativeapp.ui

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

/**
 * Detects a deliberate shake from linear acceleration, not from the constant pull of gravity.
 * The threshold is expressed in g so it remains comparable across devices; the cooldown keeps
 * one physical shake from opening several forms.
 */
internal class ShakeDetector(
    private val onShake: () -> Unit,
    private val thresholdG: Float = 2.7f,
    private val cooldownMs: Long = 1_200L,
) : SensorEventListener {
    private var gravityX = 0f
    private var gravityY = 0f
    private var gravityZ = 0f
    private var initialized = false
    private var lastShakeAtMs = Long.MIN_VALUE

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER || event.values.size < 3) return
        val alpha = 0.8f
        if (!initialized) {
            gravityX = event.values[0]
            gravityY = event.values[1]
            gravityZ = event.values[2]
            initialized = true
            return
        }
        gravityX = alpha * gravityX + (1f - alpha) * event.values[0]
        gravityY = alpha * gravityY + (1f - alpha) * event.values[1]
        gravityZ = alpha * gravityZ + (1f - alpha) * event.values[2]
        val linearX = event.values[0] - gravityX
        val linearY = event.values[1] - gravityY
        val linearZ = event.values[2] - gravityZ
        val accelerationG = sqrt(linearX * linearX + linearY * linearY + linearZ * linearZ) /
            SensorManager.GRAVITY_EARTH
        val now = event.timestamp / 1_000_000L
        if (accelerationG >= thresholdG &&
            (lastShakeAtMs == Long.MIN_VALUE || now - lastShakeAtMs >= cooldownMs)) {
            lastShakeAtMs = now
            onShake()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
