package com.zirakusa.stone.nativeapp.automation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.zirakusa.stone.nativeapp.BuildConfig
import com.zirakusa.stone.nativeapp.data.persistence.SecretBox
import com.zirakusa.stone.nativeapp.data.persistence.StoneSettingsStore
import java.net.Inet4Address
import java.net.NetworkInterface
import java.security.SecureRandom

/** User switches for the MCP server (Ajustes › Modo API). Off by default. */
object ApiMode {
    var enabled by mutableStateOf(false)
        private set
    var localNetwork by mutableStateOf(false)
        private set
    /**
     * Whether the token may travel in the link (?token=). Needed by apps that only take a URL
     * (ChatGPT); off = only the Authorization: Bearer header is accepted, links carry no secret.
     */
    var urlTokens by mutableStateOf(true)
        private set
    /** Secret of the main token (the one behind the big Copy button). */
    val token: String get() = ApiTokens.primary?.secret.orEmpty()
    var port by mutableIntStateOf(McpServer.DEFAULT_PORT)
        private set
    var running by mutableStateOf(false)
        private set
    /** Connection to the public relay (fixed internet link); OFFLINE when off or not configured. */
    var relayState by mutableStateOf(RelayClient.State.OFFLINE)
        private set
    private var store: StoneSettingsStore? = null
    private var relay: RelayClient? = null
    private var device = ""
    private var relaySecret = ""
    /** Relay address; built in through BuildConfig, replaceable in tests. */
    internal var relayUrl: String = BuildConfig.RELAY_URL

    /** Whether this build has an internet relay (else only this phone / Wi-Fi). */
    val relayAvailable: Boolean get() = relayUrl.isNotBlank()

    fun load(store: StoneSettingsStore) {
        this.store = store
        enabled = store.look("api.enabled", "false").toBoolean()
        localNetwork = store.look("api.lan", "false").toBoolean()
        urlTokens = store.look("api.urlToken", "true").toBoolean()
        McpServer.allowQueryToken = urlTokens
        disabledTools = store.look("api.disabled", "").split(',').filter { it.isNotBlank() }.toSet()
        port = store.look("api.port", McpServer.DEFAULT_PORT.toString()).toIntOrNull()?.takeIf { it in PORTS }
            ?: McpServer.DEFAULT_PORT
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
    fun allowUrlTokens(value: Boolean) {
        urlTokens = value; McpServer.allowQueryToken = value; store?.setLook("api.urlToken", value.toString())
    }
    fun allowLocalNetwork(value: Boolean) { localNetwork = value; store?.setLook("api.lan", value.toString()); apply() }
    /** Access mode of the main token (the cards of the General tab). */
    val mode: AccessMode get() = ApiTokens.primary?.mode ?: AccessMode.WRITE
    fun changeMode(value: AccessMode) { ApiTokens.primary?.let { ApiTokens.setMode(it.id, value) } }

    /** Tools the user switched off one by one (MCP names). */
    var disabledTools by mutableStateOf(emptySet<String>())
        private set

    fun allowTool(toolName: String, allowed: Boolean) {
        disabledTools = if (allowed) disabledTools - toolName else disabledTools + toolName
        store?.setLook("api.disabled", disabledTools.sorted().joinToString(","))
    }
    fun allowAllTools(allowed: Boolean, toolNames: List<String>) {
        disabledTools = if (allowed) emptySet() else toolNames.toSet() - "stone_help"
        store?.setLook("api.disabled", disabledTools.sorted().joinToString(","))
    }

    /** Whether a token with [access] may use [action]: allowed by its mode and not switched off. */
    fun permits(action: StoneAction, access: AccessMode = mode): Boolean =
        access.permits(action.risk) && action.toolName !in disabledTools

    /** Revokes every agent that knows the old main token. */
    fun regenerateToken() { ApiTokens.primary?.let { ApiTokens.rotate(it.id) } }

    /** Changes the port (1024-65535); false when the value is not valid. */
    fun changePort(value: Int): Boolean {
        if (value !in PORTS) return false
        port = value; store?.setLook("api.port", value.toString()); apply()
        return true
    }

    private fun apply() {
        running = if (enabled) McpServer.start(ApiTokens.tokens, localNetwork, port) else { McpServer.stop(); false }
        if (enabled && running && relayAvailable) {
            if (relay == null) relay = RelayClient(relayUrl, device, relaySecret, { port }) { relayState = it }.also { it.start() }
        } else { relay?.stop(); relay = null; relayState = RelayClient.State.OFFLINE }
    }

    /** The one link users paste into Claude or ChatGPT; changing the token revokes the old one. */
    fun publicLink(secret: String = token): String? =
        if (relayAvailable) "${relayUrl.trimEnd('/')}/d/$device${McpServer.PATH}${tokenQuery(secret)}" else null

    /** Internet link when there is a relay, else this phone's address. */
    fun linkFor(secret: String = token): String = publicLink(secret) ?: "http://127.0.0.1:$port${McpServer.PATH}${tokenQuery(secret)}"

    private fun tokenQuery(secret: String) = if (urlTokens) "?token=$secret" else ""

    /** What the copy buttons give: the link, plus the header line when the token may not go in it. */
    fun shareText(secret: String = token): String =
        if (urlTokens) linkFor(secret) else "${linkFor(secret)}\nAuthorization: Bearer $secret"

    /** Wi-Fi addresses of this phone (only when the local network is allowed). */
    fun lanHosts(): List<String> = if (!localNetwork) emptyList() else runCatching {
        NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }.filterIsInstance<Inet4Address>().filter { it.isSiteLocalAddress }
            .mapNotNull { it.hostAddress }
    }.getOrDefault(emptyList())

    fun urls(): List<String> = (listOf("127.0.0.1") + lanHosts()).map { "http://$it:$port${McpServer.PATH}" }

    /** Address other devices should use: the Wi-Fi one when allowed, else this phone only. */
    private fun mainHost() = lanHosts().firstOrNull() ?: "127.0.0.1"

    fun urlWithToken() = "http://${mainHost()}:$port${McpServer.PATH}${tokenQuery(token)}"
    fun sseUrlWithToken() = "http://${mainHost()}:$port/sse${tokenQuery(token)}"
    fun claudeCodeCommand() =
        "claude mcp add --transport http stone http://${mainHost()}:$port${McpServer.PATH} --header \"Authorization: Bearer $token\""
    fun jsonConfig() = """{
  "mcpServers": {
    "stone": {
      "type": "http",
      "url": "http://${mainHost()}:$port${McpServer.PATH}",
      "headers": { "Authorization": "Bearer $token" }
    }
  }
}"""

    private fun newToken(size: Int = 24): String {
        val bytes = ByteArray(size).also(SecureRandom()::nextBytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private val PORTS = 1024..65535
}
