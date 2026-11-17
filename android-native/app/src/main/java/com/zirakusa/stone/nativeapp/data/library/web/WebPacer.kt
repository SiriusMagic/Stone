package com.zirakusa.stone.nativeapp.data.library.web

import java.net.HttpURLConnection
import java.net.URL

/** The service said "too many requests" (HTTP 429); try again after [retryAfterSeconds]. */
class RateLimitedException(val service: String, val retryAfterSeconds: Int) :
    Exception("$service is limiting requests (HTTP 429). Try again in $retryAfterSeconds s.")

/**
 * Polite access to free public services (lrclib.net, iTunes) shared by every agent and screen:
 * - a minimum gap between requests to the same service, so batches never fire in bursts;
 * - on HTTP 429 it honours Retry-After (or waits 2, 4, 8 s) and retries a couple of times;
 * - if the service is still limiting, every caller fails fast with [RateLimitedException]
 *   until the cool-down ends, instead of hammering it and extending the block.
 */
class WebPacer(val service: String, private val minGapMs: Long, private val maxRetries: Int = 2,
    private val maxWaitMs: Long = 20_000, private val clock: () -> Long = System::currentTimeMillis,
    private val sleep: (Long) -> Unit = Thread::sleep) {
    private val lock = Any()
    private var nextAllowedAt = 0L
    @Volatile var coolDownUntil = 0L; private set

    /** Runs [request] (returns the HTTP status and body) respecting the pace; null body for 404. */
    fun call(request: () -> Pair<Int, String?>): String? {
        var attempt = 0
        while (true) {
            waitTurn()
            val (status, body) = request()
            when (status) {
                200 -> return body
                404 -> return null
                429, 503 -> {
                    val retryAfter = body?.toIntOrNull()?.coerceIn(1, 3600) ?: (2 shl attempt)
                    val waitMs = retryAfter * 1000L
                    coolDownUntil = clock() + waitMs
                    if (attempt >= maxRetries || waitMs > maxWaitMs) throw RateLimitedException(service, retryAfter)
                    sleep(waitMs); attempt++
                }
                else -> error("$service answered HTTP $status")
            }
        }
    }

    private fun waitTurn() {
        val wait = synchronized(lock) {
            val now = clock()
            if (coolDownUntil > now) {
                // Someone else hit the limit: do not join the pile-up.
                throw RateLimitedException(service, ((coolDownUntil - now + 999) / 1000).toInt())
            }
            val at = maxOf(now, nextAllowedAt)
            nextAllowedAt = at + minGapMs
            at - now
        }
        if (wait > 0) sleep(wait)
    }

    companion object {
        val lrclib = WebPacer("lrclib.net", minGapMs = 700)
        /** Apple documents roughly 20 searches a minute. */
        val itunes = WebPacer("iTunes", minGapMs = 3_000)

        /**
         * Plain GET for [WebPacer.call]: status plus body (for 429/503 the body is the Retry-After
         * header so the pacer can read it).
         */
        fun get(url: String): Pair<Int, String?> {
            val connection = URL(url).openConnection() as HttpURLConnection
            return try {
                connection.connectTimeout = 10_000; connection.readTimeout = 15_000
                connection.setRequestProperty("User-Agent", "Stone music player")
                val status = connection.responseCode
                status to when (status) {
                    200 -> connection.inputStream.bufferedReader().use { it.readText() }
                    429, 503 -> connection.getHeaderField("Retry-After")
                    else -> null
                }
            } finally { connection.disconnect() }
        }
    }
}
