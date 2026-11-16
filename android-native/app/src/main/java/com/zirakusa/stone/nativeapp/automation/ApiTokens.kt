package com.zirakusa.stone.nativeapp.automation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.zirakusa.stone.nativeapp.data.persistence.SecretBox
import com.zirakusa.stone.nativeapp.data.persistence.StoneSettingsStore
import java.security.SecureRandom

/**
 * Access tokens of Ajustes › Modo API › Seguridad. Each one has a name and an [AccessMode] and
 * can be rotated or revoked alone. The first token is the "main" one used by the big Copy button.
 */
object ApiTokens {
    var tokens by mutableStateOf(emptyList<ApiToken>())
        private set
    private var store: StoneSettingsStore? = null
    private var lastSaved = 0L

    val primary: ApiToken? get() = tokens.firstOrNull()

    /** Loads the tokens; the single token of 1.6.8 and earlier becomes "Principal" with its old permissions. */
    fun load(store: StoneSettingsStore, defaultName: String) {
        this.store = store
        val stored = store.look("api.tokens", "[]")
        tokens = ApiToken.parse(SecretBox.open(stored).ifBlank { "[]" })
        if (tokens.isNotEmpty() && !SecretBox.isSealed(stored)) save() // seal what older versions stored in plain text
        if (tokens.isEmpty()) {
            val legacy = store.look("api.token", "").takeUnless(SecretBox::isSealed).orEmpty().ifBlank { secret() }
            val mode = if (store.look("api.readOnly", "false").toBoolean()) AccessMode.READ else AccessMode.WRITE
            tokens = listOf(ApiToken(id(), defaultName, legacy, mode, System.currentTimeMillis()))
            save()
        }
        publish()
    }

    /** [validDays] 0 = never expires. */
    fun create(name: String, mode: AccessMode, validDays: Int = 0): ApiToken {
        val now = System.currentTimeMillis()
        return ApiToken(id(), name.trim().ifEmpty { "Agente ${tokens.size + 1}" }, secret(), mode, now,
            expiresAt = if (validDays > 0) now + validDays * 86_400_000L else 0)
            .also { tokens = tokens + it; save(); publish() }
    }

    /** New expiry counted from now ([validDays] 0 = never): renews an expired token or shortens one. */
    fun setExpiry(id: String, validDays: Int) = change(id) {
        it.copy(expiresAt = if (validDays > 0) System.currentTimeMillis() + validDays * 86_400_000L else 0)
    }

    fun rename(id: String, name: String) = change(id) { it.copy(name = name.trim().ifEmpty { it.name }) }
    fun setMode(id: String, mode: AccessMode) = change(id) { it.copy(mode = mode) }
    /** New secret for the same token: whoever had the old link loses access. */
    fun rotate(id: String) = change(id) { it.copy(secret = secret(), lastUsedAt = 0) }

    /** Removes a token; the last one is rotated instead so the link card always has something to show. */
    fun revoke(id: String) {
        if (tokens.size <= 1) return rotate(id)
        tokens = tokens.filterNot { it.id == id }; save(); publish()
    }

    /** Called by the server on every authorized request; saved at most once a minute. */
    fun touch(id: String) {
        val now = System.currentTimeMillis()
        tokens = tokens.map { if (it.id == id) it.copy(lastUsedAt = now) else it }
        if (now - lastSaved > 60_000) save()
    }

    /** For tests and the server overload that takes a bare secret. */
    internal fun replaceForTests(list: List<ApiToken>) { tokens = list; publish() }

    private fun change(id: String, transform: (ApiToken) -> ApiToken) {
        tokens = tokens.map { if (it.id == id) transform(it) else it }; save(); publish()
    }

    private fun save() {
        lastSaved = System.currentTimeMillis()
        // Encrypted with a key kept in the Android Keystore; the old plain-text copy is wiped.
        store?.setLook("api.tokens", SecretBox.seal(ApiToken.serialize(tokens)))
        store?.setLook("api.token", "")
    }

    private fun publish() = McpServer.updateTokens(tokens)

    private fun id() = random(6)
    /**
     * New secrets: "stn_" + 256 random bits in URL-safe Base64. The prefix lets secret scanners
     * recognise a leaked Stone token; older hex tokens keep working.
     */
    fun secret(): String = "stn_" + java.util.Base64.getUrlEncoder().withoutPadding()
        .encodeToString(ByteArray(32).also(SecureRandom()::nextBytes))
    fun random(size: Int): String = ByteArray(size).also(SecureRandom()::nextBytes).joinToString("") { "%02x".format(it) }
}
