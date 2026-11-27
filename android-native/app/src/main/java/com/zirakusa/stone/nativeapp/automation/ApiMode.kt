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
import com.zirakusa.stone.nativeapp.automation.auth.AccessMode
import com.zirakusa.stone.nativeapp.automation.auth.ApiTokens
import com.zirakusa.stone.nativeapp.automation.relay.RelayClient
import com.zirakusa.stone.nativeapp.automation.server.McpServer

object ApiMode {
    /** Fixed: the app offers no way to change it. */
    val port: Int get() = McpServer.DEFAULT_PORT
    var running by mutableStateOf(false)
        private set
    /** Connection to the public relay (fixed internet link); OFFLINE when off or not configured. */
    var relayState by mutableStateOf(RelayClient.State.OFFLINE)
        private set
    /** Redirect through Arka. Keep its existing stored key. */
    var remote by mutableStateOf(false)
        private set
    var enabled by mutableStateOf(false)
        private set
    var localMcp by mutableStateOf(false)
        private set
    val active: Boolean get() = enabled || remote
    private var store: StoneSettingsStore? = null
    private var relay: RelayClient? = null
    /** Only to reconnect the relay as soon as the phone has a network again. */
    private var connectivity: android.net.ConnectivityManager? = null
    private var device = ""
    private var relaySecret = ""
    /** Relay address; built in through BuildConfig, replaceable in tests. */
    internal var relayUrl: String = BuildConfig.RELAY_URL

    fun enable(value: Boolean) {
        enabled = value
        store?.setLook(ENABLED, value.toString())
        ApiTokens.publish()
        apply()
    }

    fun allowLocal(value: Boolean) {
        localMcp = value
        store?.setLook(LOCAL, value.toString())
    }

    fun turnRemote(value: Boolean) {
        remote = value; store?.setLook(REMOTE, value.toString())
        ApiTokens.publish(); apply()
    }

    /** Link the hub uses to reach Stone (only for Arka, through [HubLink]). */
    fun hubLink(): String = linkFor(ApiTokens.arka().secret)

    /** Whether [load] already ran in this process (the hub link provider can be called before any screen). */
    val loaded: Boolean get() = store != null

    /** Whether this build has an internet relay (else only this phone). */
    val relayAvailable: Boolean get() = relayUrl.isNotBlank()

    fun load(store: StoneSettingsStore, context: android.content.Context? = null) {
        this.store = store
        connectivity = context?.applicationContext?.getSystemService(android.net.ConnectivityManager::class.java)
        remote = store.look(REMOTE, "true").ifBlank { "true" }.toBoolean()
        enabled = store.look(ENABLED, "false").toBoolean()
        localMcp = store.look(LOCAL, "false").toBoolean()
        // Settings the app no longer offers (its own MCP screen before Arka ran it) are wiped.
        for (old in listOf("api.lan", "api.urlToken", "api.port", "api.stats"))
            if (store.look(old, "").isNotEmpty()) store.setLook(old, "")
        ApiTokens.load(store)
        ActivityLog.attach(store)
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

    /** Whether a token with [access] may use [action]: allowed by its mode. */
    fun permits(action: StoneAction, access: AccessMode = AccessMode.FULL): Boolean = access.permits(action.risk)

    private fun apply() {
        running = if (active) McpServer.start(ApiTokens.served(), port) else { McpServer.stop(); false }
        if (active && running && relayAvailable) {
            if (relay == null) relay = RelayClient(relayUrl, device, relaySecret, { port }, connectivity) { relayState = it }.also { it.start() }
        } else { relay?.stop(); relay = null; relayState = RelayClient.State.OFFLINE }
    }

    /** Internet link when there is a relay, else this phone's address. */
    fun linkFor(secret: String): String =
        if (relayAvailable) "${relayUrl.trimEnd('/')}/d/$device${McpServer.PATH}?token=$secret"
        else "http://127.0.0.1:$port${McpServer.PATH}?token=$secret"

    private fun newToken(size: Int = 24): String {
        val bytes = ByteArray(size).also(SecureRandom()::nextBytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private const val ENABLED = "api.enabled"
    private const val LOCAL = "api.localMcp"
    private const val REMOTE = "api.hubRedirect"
}
