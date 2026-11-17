package com.zirakusa.stone.nativeapp.automation.relay

import kotlin.random.Random

/**
 * When the phone should dial the relay again. Every attempt is one billable request to
 * Cloudflare, so answers that will never change stop the loop instead of retrying forever:
 * 400/401/403/404/426 from the relay (bad device id or secret, another phone owns the link,
 * wrong relay address) and "replaced" (this phone already connected again elsewhere).
 * Network and server problems retry with a growing wait: 2, 4, 8… up to 5 minutes, ±20 %.
 */
object ReconnectPolicy {
    const val MAX_DELAY_SECONDS = 300L
    private val permanentStatus = setOf(400, 401, 403, 404, 426)

    fun isPermanent(httpStatus: Int?, closeReason: String? = null): Boolean =
        (httpStatus != null && httpStatus in permanentStatus) || closeReason == "replaced"

    /** Seconds before attempt number [attempt] (1 = first retry). */
    fun delaySeconds(attempt: Int, random: Random = Random.Default): Long {
        val base = (1L shl attempt.coerceIn(1, 9)).coerceAtMost(MAX_DELAY_SECONDS)
        val jitter = (base * 0.2 * (random.nextDouble() * 2 - 1)).toLong()
        return (base + jitter).coerceIn(1, MAX_DELAY_SECONDS)
    }
}
