package com.zirakusa.stone.core

/** Event-triggered only. No timer, work scheduler, or observer is owned here. */
class RefreshPolicy(private val minimumIntervalMs: Long = 15 * 60 * 1000L) {
    private var lastSuccess: Long? = null
    private var running = false

    @Synchronized fun tryStart(manual: Boolean, nowMs: Long): Boolean {
        if (running) return false
        val elapsed = lastSuccess?.let { nowMs - it }
        if (!manual && elapsed != null && elapsed >= 0 && elapsed < minimumIntervalMs) return false
        running = true
        return true
    }

    @Synchronized fun finish(success: Boolean, nowMs: Long) {
        if (success) lastSuccess = nowMs
        running = false
    }
}
