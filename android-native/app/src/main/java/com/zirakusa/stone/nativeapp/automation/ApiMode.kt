package com.zirakusa.stone.nativeapp.automation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.zirakusa.stone.nativeapp.BuildConfig
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
    var token by mutableStateOf("")
        private set
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
        port = store.look("api.port", McpServer.DEFAULT_PORT.toString()).toIntOrNull()?.takeIf { it in PORTS }
            ?: McpServer.DEFAULT_PORT
        token = store.look("api.token", "").ifBlank { newToken().also { store.setLook("api.token", it) } }
        device = store.look("api.device", "").ifBlank { newToken(10).also { store.setLook("api.device", it) } }
        relaySecret = store.look("api.relaySecret", "").ifBlank { newToken(32).also { store.setLook("api.relaySecret", it) } }
        apply()
    }

    fun enable(value: Boolean) { enabled = value; store?.setLook("api.enabled", value.toString()); apply() }
    fun allowLocalNetwork(value: Boolean) { localNetwork = value; store?.setLook("api.lan", value.toString()); apply() }
    /** Revokes every agent that knows the old token. */
    fun regenerateToken() { token = newToken(); store?.setLook("api.token", token); apply() }

    /** Changes the port (1024-65535); false when the value is not valid. */
    fun changePort(value: Int): Boolean {
        if (value !in PORTS) return false
        port = value; store?.setLook("api.port", value.toString()); apply()
        return true
    }

    private fun apply() {
        running = if (enabled) McpServer.start(token, localNetwork, port) else { McpServer.stop(); false }
        if (enabled && running && relayAvailable) {
            if (relay == null) relay = RelayClient(relayUrl, device, relaySecret, { port }) { relayState = it }.also { it.start() }
        } else { relay?.stop(); relay = null; relayState = RelayClient.State.OFFLINE }
    }

    /** The one link users paste into Claude or ChatGPT; changing the token revokes the old one. */
    fun publicLink(): String? = if (relayAvailable) "${relayUrl.trimEnd('/')}/d/$device${McpServer.PATH}?token=$token" else null

    /** Wi-Fi addresses of this phone (only when the local network is allowed). */
    fun lanHosts(): List<String> = if (!localNetwork) emptyList() else runCatching {
        NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }.filterIsInstance<Inet4Address>().filter { it.isSiteLocalAddress }
            .mapNotNull { it.hostAddress }
    }.getOrDefault(emptyList())

    fun urls(): List<String> = (listOf("127.0.0.1") + lanHosts()).map { "http://$it:$port${McpServer.PATH}" }

    /** Address other devices should use: the Wi-Fi one when allowed, else this phone only. */
    private fun mainHost() = lanHosts().firstOrNull() ?: "127.0.0.1"

    fun urlWithToken() = "http://${mainHost()}:$port${McpServer.PATH}?token=$token"
    fun sseUrlWithToken() = "http://${mainHost()}:$port/sse?token=$token"
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
