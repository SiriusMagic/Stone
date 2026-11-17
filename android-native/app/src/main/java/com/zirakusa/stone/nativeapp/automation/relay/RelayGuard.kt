package com.zirakusa.stone.nativeapp.automation.relay

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

/**
 * Safety rules for requests that arrive through the internet relay.
 *
 * - [key]: random per run of Stone, known only to [RelayClient] and [McpServer]. The extra
 *   information the relay adds (real client address, when the request arrived, its relay id) is
 *   trusted only when it comes with this key, so another app on the phone cannot fake it.
 * - Queue deadline: a request that waited too long is answered "busy" WITHOUT running it, because
 *   the relay has (or is about to have) answered 504 already.
 * - Late calls: a change that finished after the relay gave up is remembered for a few minutes. If
 *   the same token asks for exactly the same change again (an agent retrying after the 504), the
 *   remembered result is returned instead of doing it twice. Each memory is used once.
 */
/** What the relay told Stone about one forwarded request (trusted only with [RelayGuard.key]). */
data class RelayInfo(val receivedAt: Long, val id: String?, val client: String?)

object RelayGuard {
    val key: String = ByteArray(32).also(SecureRandom()::nextBytes).joinToString("") { "%02x".format(it) }

    /** The relay answers 504 after 75 s; past this the request is not started. */
    @Volatile var queueDeadlineMs = 60_000L
    /** A change that ends after this is late for sure, even without the relay's notice. */
    @Volatile var lateAfterMs = 76_000L
    private const val REMEMBER_MS = 10 * 60_000L
    private const val MAX_REMEMBERED = 200

    fun isRelay(givenKey: String?): Boolean =
        givenKey != null && MessageDigest.isEqual(givenKey.toByteArray(), key.toByteArray())

    /** Only an IPv4/IPv6 literal is accepted as a client address. */
    fun cleanAddress(value: String?): String? =
        value?.trim()?.takeIf { it.length in 2..45 && it.all { c -> c.isLetterOrDigit() || c == '.' || c == ':' } }

    // ---- relay ids the relay already answered with 504 ----
    private val timedOut = ConcurrentHashMap<String, Long>()

    fun relayTimedOut(id: String) {
        val now = System.currentTimeMillis()
        timedOut[id] = now
        if (timedOut.size > 500) timedOut.entries.removeIf { now - it.value > REMEMBER_MS }
    }

    fun hasTimedOut(id: String?): Boolean = id != null && timedOut.containsKey(id)

    // ---- late changes, by token + tool + arguments ----
    private class Late(val at: Long, val result: Any)
    private val late = ConcurrentHashMap<String, Late>()

    fun callKey(tokenId: String, tool: String, arguments: JSONObject): String {
        val digest = MessageDigest.getInstance("SHA-256").digest("$tokenId|$tool|${canonical(arguments)}".toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun rememberLate(key: String, result: Any) {
        val now = System.currentTimeMillis()
        late.entries.removeIf { now - it.value.at > REMEMBER_MS }
        if (late.size < MAX_REMEMBERED) late[key] = Late(now, result)
    }

    /** The remembered result of an identical late change (removed so it is used only once). */
    fun takeLate(key: String): Any? {
        val found = late.remove(key) ?: return null
        return found.result.takeIf { System.currentTimeMillis() - found.at <= REMEMBER_MS }
    }

    internal fun clearForTests() { late.clear(); timedOut.clear(); queueDeadlineMs = 60_000L; lateAfterMs = 76_000L }

    /** JSON with object keys sorted, so the same arguments in another order give the same key. */
    fun canonical(value: Any?): String = when (value) {
        is JSONObject -> value.keys().asSequence().sorted().joinToString(",", "{", "}") { JSONObject.quote(it) + ":" + canonical(value.opt(it)) }
        is JSONArray -> (0 until value.length()).joinToString(",", "[", "]") { canonical(value.opt(it)) }
        is String -> JSONObject.quote(value)
        null, JSONObject.NULL -> "null"
        else -> value.toString()
    }
}
