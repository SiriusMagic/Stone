package com.zirakusa.stone.nativeapp.automation.cloudflare

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.zirakusa.stone.nativeapp.data.persistence.SecretBox
import com.zirakusa.stone.nativeapp.data.persistence.StoneSettingsStore
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.OffsetDateTime

/** Sends one request to Cloudflare; replaceable in tests. Returns the HTTP status and the body. */
fun interface CloudflareTransport { fun send(method: String, url: String, token: String): Pair<Int, String> }

class CloudflareException(val code: String) : Exception(code)

/**
 * The person's own Cloudflare API token (the account behind Stone's relay, "the tunnel", and the hub).
 * Stored encrypted (SecretBox). It is shared with the Gateway Hub app through [com.zirakusa.stone.nativeapp.automation.HubLink]
 * so updating it in either app is enough. Only api.cloudflare.com is called, over HTTPS.
 */
object Cloudflare {
    const val BASE = "https://api.cloudflare.com/client/v4"
    const val CREATE_URL = "https://dash.cloudflare.com/profile/api-tokens?permissionGroupKeys=" +
        "%5B%7B%22key%22%3A%22workers_scripts%22%2C%22type%22%3A%22edit%22%7D%5D&name=Gateway%20Hub%20y%20Stone"
    const val RELAY_SCRIPT = "stone-relay"

    /** When the saved token expires (0 = no end date); 0 savedAt = nothing saved. */
    var expiresAt by mutableStateOf(0L); private set
    var savedAt by mutableStateOf(0L); private set
    val saved: Boolean get() = savedAt > 0
    internal var transport: CloudflareTransport = HttpTransport
    private var store: StoneSettingsStore? = null

    fun load(store: StoneSettingsStore) {
        this.store = store
        savedAt = if (store.look("cf.token", "").isBlank()) 0 else store.look("cf.savedAt", "0").toLongOrNull() ?: 0
        expiresAt = store.look("cf.expiresAt", "0").toLongOrNull() ?: 0
    }

    fun token(): String? = store?.look("cf.token", "")?.takeIf { it.isNotBlank() }?.let(SecretBox::open)?.takeIf { it.isNotBlank() }

    /** Days left (negative once expired), or null with no end date. */
    fun daysLeft(now: Long = System.currentTimeMillis()): Long? = if (expiresAt <= 0) null else Math.floorDiv(expiresAt - now, 86_400_000L)

    /** Checks [token] with Cloudflare and saves it (call off the main thread). */
    fun update(token: String) { val clean = token.trim(); keep(clean, verify(clean), System.currentTimeMillis()) }

    /** From Gateway Hub: kept only when newer than ours. */
    fun receive(token: String, expires: Long, at: Long): Boolean {
        if (token.isBlank() || at <= savedAt) return false
        keep(token, expires, at); return true
    }

    fun forget() { store?.setLook("cf.token", ""); savedAt = 0; expiresAt = 0 }

    private fun keep(token: String, expires: Long, at: Long) {
        store?.setLook("cf.token", SecretBox.seal(token)); store?.setLook("cf.expiresAt", expires.toString()); store?.setLook("cf.savedAt", at.toString())
        expiresAt = expires; savedAt = at
    }

    fun verify(token: String): Long {
        if (token.isBlank()) throw CloudflareException("EMPTY")
        val result = runCatching { get(token, "/user/tokens/verify") }.getOrNull()
            ?: accounts(token).firstNotNullOfOrNull { runCatching { get(token, "/accounts/$it/tokens/verify") }.getOrNull() }
            ?: throw CloudflareException("INVALID")
        val r = result.getJSONObject("result")
        if (r.optString("status") != "active") throw CloudflareException("INACTIVE")
        return r.optString("expires_on").takeIf { it.isNotBlank() && it != "null" }
            ?.let { runCatching { OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull() } ?: 0
    }

    /** The tunnel: the relay Worker's address in the person's account, or null when it is not there. */
    fun relayAddress(token: String = token() ?: throw CloudflareException("MISSING")): String? {
        for (account in accounts(token)) {
            val scripts = get(token, "/accounts/$account/workers/scripts").getJSONArray("result")
            if ((0 until scripts.length()).none { scripts.getJSONObject(it).getString("id") == RELAY_SCRIPT }) continue
            val sub = get(token, "/accounts/$account/workers/subdomain").getJSONObject("result").getString("subdomain")
            return "https://$RELAY_SCRIPT.$sub.workers.dev"
        }
        return null
    }

    private fun accounts(token: String): List<String> =
        get(token, "/accounts").getJSONArray("result").let { a -> (0 until a.length()).map { a.getJSONObject(it).getString("id") } }

    private fun get(token: String, path: String): JSONObject {
        val (status, text) = try { transport.send("GET", BASE + path, token) } catch (e: Exception) { throw CloudflareException("NETWORK") }
        if (status == 401 || status == 403) throw CloudflareException("FORBIDDEN")
        val json = runCatching { JSONObject(text) }.getOrNull()
        if (status !in 200..299 || json?.optBoolean("success") != true) throw CloudflareException("ERROR")
        return json
    }

    private object HttpTransport : CloudflareTransport {
        override fun send(method: String, url: String, token: String): Pair<Int, String> {
            val c = URL(url).openConnection() as HttpURLConnection
            try {
                c.requestMethod = method; c.connectTimeout = 15_000; c.readTimeout = 30_000; c.instanceFollowRedirects = false
                c.setRequestProperty("Authorization", "Bearer $token"); c.setRequestProperty("Accept", "application/json")
                val status = c.responseCode
                return status to ((if (status >= 400) c.errorStream else c.inputStream)?.bufferedReader()?.use { it.readText() } ?: "")
            } finally { c.disconnect() }
        }
    }
}
