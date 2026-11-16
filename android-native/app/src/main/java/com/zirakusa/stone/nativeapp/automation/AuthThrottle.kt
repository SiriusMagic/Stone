package com.zirakusa.stone.nativeapp.automation

import java.util.concurrent.ConcurrentHashMap

/**
 * Slows down token guessing: after [LIMIT] wrong or missing tokens from the same caller within a
 * minute, that caller gets HTTP 429 for a minute. A correct token is never blocked by others.
 */
internal object AuthThrottle {
    private const val LIMIT = 20
    private const val WINDOW_MS = 60_000L
    private class Entry(var count: Int, var since: Long, var blockedUntil: Long = 0)
    private val callers = ConcurrentHashMap<String, Entry>()

    fun blocked(caller: String, now: Long = System.currentTimeMillis()): Boolean =
        (callers[caller]?.blockedUntil ?: 0) > now

    fun failed(caller: String, now: Long = System.currentTimeMillis()) {
        if (callers.size > 1_000) callers.clear()
        val entry = callers.getOrPut(caller) { Entry(0, now) }
        synchronized(entry) {
            if (now - entry.since > WINDOW_MS) { entry.count = 0; entry.since = now }
            if (++entry.count >= LIMIT) { entry.blockedUntil = now + WINDOW_MS; entry.count = 0; entry.since = now }
        }
    }

    internal fun reset() = callers.clear()
}
