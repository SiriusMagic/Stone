package com.zirakusa.stone.nativeapp.automation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.zirakusa.stone.nativeapp.BuildConfig
import com.zirakusa.stone.nativeapp.data.persistence.SecretBox
import com.zirakusa.stone.nativeapp.data.persistence.StoneSettingsStore
import java.security.SecureRandom
import com.zirakusa.stone.nativeapp.automation.actions.StoneAction
import com.zirakusa.stone.nativeapp.automation.activity.ActivityLog
import com.zirakusa.stone.nativeapp.automation.activity.RequestStats
import com.zirakusa.stone.nativeapp.automation.auth.AccessMode
import com.zirakusa.stone.nativeapp.automation.auth.ApiTokens
import com.zirakusa.stone.nativeapp.automation.relay.RelayClient
import com.zirakusa.stone.nativeapp.automation.server.McpServer

/** User switches for the MCP server (Ajustes › Modo API). Off by default. */
object ApiMode {
    var enabled by mutableStateOf(false)
        private set
    /** Secret of the main token (the one behind the big Copy button). */
    val token: String get() = ApiTokens.primary?.secret.orEmpty()
    /** Fixed: the app offers no way to change it. */
    val port: Int get() = McpServer.DEFAULT_PORT
    var running by mutableStateOf(false)
        private set
    /** Connection to the public relay (fixed internet link); OFFLINE when off or not configured. */
    var relayState by mutableStateOf(RelayClient.State.OFFLINE)
        private set
    private var store: StoneSettingsStore? = null
    private var relay: RelayClient? = null
    /** Only to reconnect the relay as soon as the phone has a network again. */
    private var connectivity: android.net.ConnectivityManager? = null
    private var device = ""
    private var relaySecret = ""
    /** Relay address; built in through BuildConfig, replaceable in tests. */
    internal var relayUrl: String = BuildConfig.RELAY_URL

    /** Whether this build has an internet relay (else only this phone). */
    val relayAvailable: Boolean get() = relayUrl.isNotBlank()

    fun load(store: StoneSettingsStore, context: android.content.Context? = null) {
        this.store = store
        connectivity = context?.applicationContext?.getSystemService(android.net.ConnectivityManager::class.java)
        enabled = store.look("api.enabled", "false").toBoolean()
        // Settings the app no longer offers (Wi-Fi access, header-only token, custom port) are wiped.
        for (old in listOf("api.lan", "api.urlToken", "api.port")) if (store.look(old, "").isNotEmpty()) store.setLook(old, "")
        ApiTokens.load(store, "Principal")
        ActivityLog.attach(store)
        RequestStats.attach(store)
        device = store.look("api.device", "").ifBlank { newToken(10).also { store.setLook("api.device", it) } }
        // Stored encrypted with a Keystore key; values from older versions are re-sealed here.
        val storedRelay = store.look("api.relaySecret", "")
        val openedRelay = SecretBox.open(storedRelay)
        relaySecret = openedRelay.ifBlank { newToken(32) }
        // Save when it was plain text (older versions) or could not be decrypted (Keystore key lost):
        // otherwise a new key would be used but never stored, and the relay would refuse the phone.
        if (!SecretBox.isSealed(storedRelay) || openedRelay.isBlank()) store.setLook("api.relaySecret", SecretBox.seal(relaySecret))
        apply()
    }

    fun enable(value: Boolean) { enabled = value; store?.setLook("api.enabled", value.toString()); apply() }
    /** Access mode of the main token (the cards of the General tab). */
    val mode: AccessMode get() = ApiTokens.primary?.mode ?: AccessMode.WRITE

    /** Whether a token with [access] may use [action]: allowed by its mode and not switched off. */
    fun permits(action: StoneAction, access: AccessMode = mode): Boolean =
        access.permits(action.risk) // tools are allowed per token (ApiToken.disabledTools)

    private fun apply() {
        running = if (enabled) McpServer.start(ApiTokens.tokens, port) else { McpServer.stop(); false }
        if (enabled && running && relayAvailable) {
            if (relay == null) relay = RelayClient(relayUrl, device, relaySecret, { port }, connectivity) { relayState = it }.also { it.start() }
        } else { relay?.stop(); relay = null; relayState = RelayClient.State.OFFLINE }
    }

    /** The one link users paste into Claude or ChatGPT; changing the token revokes the old one. */
    fun publicLink(secret: String = token): String? =
        if (relayAvailable) "${relayUrl.trimEnd('/')}/d/$device${McpServer.PATH}${tokenQuery(secret)}" else null

    /** Internet link when there is a relay, else this phone's address. */
    fun linkFor(secret: String = token): String = publicLink(secret) ?: "http://127.0.0.1:$port${McpServer.PATH}${tokenQuery(secret)}"

    private fun tokenQuery(secret: String) = "?token=$secret"

    /** What the copy buttons give: the link with its token. */
    fun shareText(secret: String = token): String = linkFor(secret)

    private fun newToken(size: Int = 24): String {
        val bytes = ByteArray(size).also(SecureRandom()::nextBytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
