package com.zirakusa.stone.nativeapp.automation.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.zirakusa.stone.nativeapp.data.persistence.SecretBox
import com.zirakusa.stone.nativeapp.data.persistence.StoneSettingsStore
import java.security.SecureRandom
import com.zirakusa.stone.nativeapp.automation.server.McpServer

/**
 * The one token Stone serves: Arka's (FULL, never expires). Arka turns the Modo MCP on or off and the hub decides what each
 * AI may do, so there are no per-agent tokens here any more. A token from before (the old «Redireccionar a Hub» one) is
 * kept, so the hub's link keeps working; the user's own tokens of older versions are dropped.
 */
object ApiTokens {
    var tokens by mutableStateOf(emptyList<ApiToken>())
        private set
    private var store: StoneSettingsStore? = null
    private var lastSaved = 0L

    fun load(store: StoneSettingsStore) {
        this.store = store
        val stored = ApiToken.parse(SecretBox.open(store.look("api.tokens", "[]")).ifBlank { "[]" })
        tokens = stored.filter { it.kind == TokenKind.REDIRECT }.take(1)
        if (tokens != stored) save()
        publish()
    }

    /** Arka's token: reused if it exists, else created with full access and no end date. */
    fun arka(): ApiToken = tokens.firstOrNull()
        ?: ApiToken(random(6), "Arka", secret(), AccessMode.FULL, System.currentTimeMillis(), kind = TokenKind.REDIRECT, usage = "Arka")
            .also { tokens = listOf(it); save(); publish() }

    /** Called by the server on every authorized request; saved at most once a minute. */
    fun touch(id: String) {
        val now = System.currentTimeMillis()
        tokens = tokens.map { if (it.id == id) it.copy(lastUsedAt = now) else it }
        if (now - lastSaved > 60_000) save()
    }

    /** For tests and the server overload that takes a bare secret. */
    internal fun replaceForTests(list: List<ApiToken>) { tokens = list; publish() }

    private fun save() {
        lastSaved = System.currentTimeMillis()
        // Encrypted with a key kept in the Android Keystore; the old plain-text copy is wiped.
        store?.setLook("api.tokens", SecretBox.seal(ApiToken.serialize(tokens)))
        store?.setLook("api.token", "")
    }

    /** What the server accepts: Arka's token, only while the public link is on. */
    fun served(): List<ApiToken> = if (com.zirakusa.stone.nativeapp.automation.ApiMode.remote) tokens else emptyList()

    internal fun publish() = McpServer.updateTokens(served())

    /**
     * New secrets: "stn_" + 256 random bits in URL-safe Base64. The prefix lets secret scanners
     * recognise a leaked Stone token.
     */
    fun secret(): String = "stn_" + java.util.Base64.getUrlEncoder().withoutPadding()
        .encodeToString(ByteArray(32).also(SecureRandom()::nextBytes))
    fun random(size: Int): String = ByteArray(size).also(SecureRandom()::nextBytes).joinToString("") { "%02x".format(it) }
}
