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
import com.zirakusa.stone.nativeapp.automation.auth.ApiToken
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

    /** Gateway Hub MCP address (one link for several apps); it reaches Stone through the relay. */
    internal var hubUrl: String = BuildConfig.HUB_URL
    val hubAvailable: Boolean get() = hubUrl.isNotBlank() && relayAvailable
    /** Hub page where the owner pastes Stone's link. */
    val hubAdminUrl: String get() = "${hubUrl.trimEnd('/')}/admin"

    /**
     * The token reserved for the hub: reused if it exists, else created ("Lectura y escritura", so
     * critical actions still ask on the phone). Revoking it in Stone cuts the hub off at once.
     */
    fun hubToken(): ApiToken = ApiTokens.tokens.firstOrNull { it.name == HUB_TOKEN_NAME }
        ?: ApiTokens.create(HUB_TOKEN_NAME, AccessMode.WRITE)

    const val HUB_TOKEN_NAME = "Gateway Hub"

    /**
     * "Redirección hub": lets the Gateway Hub app on this phone (signed by the same developer) get
     * the hub's link by itself through [HubLink], with no copy and paste. Off by default.
     */
    var hubRedirect by mutableStateOf(false)
        private set
    fun allowHubRedirect(value: Boolean) { hubRedirect = value; store?.setLook("api.hubRedirect", value.toString()) }

    /**
     * Modo seguro (set from the Gateway Hub app, or here): the MCP server and the relay run only while
     * Stone is on screen. Off = MCP keeps working in the background (as before). Off by default.
     */
    var hubSafeMode by mutableStateOf(false)
        private set
    fun useHubSafeMode(value: Boolean) {
        if (hubSafeMode == value) return
        hubSafeMode = value; store?.setLook("api.hubSafe", value.toString()); apply()
    }
    /** Stone is on screen (MainActivity between onStart and onStop). */
    private var visible = false
    fun setVisible(value: Boolean) {
        if (visible == value) return
        visible = value
        if (hubSafeMode) apply() // outside safe mode, visibility changes nothing (no restart)
    }
    /** Whether MCP is served now: switched on and, in safe mode, Stone visible. */
    val serving: Boolean get() = enabled && (!hubSafeMode || visible)

    /** Whether [load] already ran in this process (the link provider can be called before any screen). */
    val loaded: Boolean get() = store != null

    fun load(store: StoneSettingsStore, context: android.content.Context? = null) {
        this.store = store
        connectivity = context?.applicationContext?.getSystemService(android.net.ConnectivityManager::class.java)
        enabled = store.look("api.enabled", "false").toBoolean()
        hubRedirect = store.look("api.hubRedirect", "false").toBoolean()
        hubSafeMode = store.look("api.hubSafe", "false").toBoolean()
        // Settings the app no longer offers (Wi-Fi access, header-only token, custom port) are wiped.
        for (old in listOf("api.lan", "api.urlToken", "api.port")) if (store.look(old, "").isNotEmpty()) store.setLook(old, "")
        ApiTokens.load(store, "Principal")
        store.setLook("cf.token", "") // Stone 1.7.15 kept a Cloudflare token: wiped, it is never stored again
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
        running = if (serving) McpServer.start(ApiTokens.tokens, port) else { McpServer.stop(); false }
        if (serving && running && relayAvailable) {
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
