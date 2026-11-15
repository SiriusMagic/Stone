package com.zirakusa.stone.nativeapp.automation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
    var running by mutableStateOf(false)
        private set
    private var store: StoneSettingsStore? = null

    fun load(store: StoneSettingsStore) {
        this.store = store
        enabled = store.look("api.enabled", "false").toBoolean()
        localNetwork = store.look("api.lan", "false").toBoolean()
        token = store.look("api.token", "").ifBlank { newToken().also { store.setLook("api.token", it) } }
        apply()
    }

    fun enable(value: Boolean) { enabled = value; store?.setLook("api.enabled", value.toString()); apply() }
    fun allowLocalNetwork(value: Boolean) { localNetwork = value; store?.setLook("api.lan", value.toString()); apply() }
    /** Revokes every agent that knows the old token. */
    fun regenerateToken() { token = newToken(); store?.setLook("api.token", token); apply() }

    private fun apply() {
        running = if (enabled) McpServer.start(token, localNetwork) else { McpServer.stop(); false }
    }

    fun urls(): List<String> = buildList {
        add("http://127.0.0.1:${McpServer.PORT}${McpServer.PATH}")
        if (localNetwork) runCatching {
            NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }.filterIsInstance<Inet4Address>().filter { it.isSiteLocalAddress }
                .forEach { add("http://${it.hostAddress}:${McpServer.PORT}${McpServer.PATH}") }
        }
    }

    private fun newToken(): String {
        val bytes = ByteArray(24).also(SecureRandom()::nextBytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
