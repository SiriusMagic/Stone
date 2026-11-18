package com.zirakusa.stone.nativeapp.automation.provider

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.zirakusa.stone.nativeapp.data.persistence.StoneSettingsStore
import java.security.KeyFactory
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * Stone's link with Gateway Hub: the environment it belongs to, the environment's public key (to check
 * execution packages), this installation's id, what Gateway last reported, and whether the user cut it.
 * No secret is kept here: the public key only verifies.
 */
object ProviderState {
    const val CONTRACT = 1
    var linked by mutableStateOf(false); private set
    var disconnectedByUser by mutableStateOf(false); private set
    /** What Gateway reported last ("active", "paused", "disconnected") and its mode ("safe", "background"). */
    var environment by mutableStateOf(""); private set
    var mode by mutableStateOf(""); private set
    var lastActivity by mutableStateOf(0L); private set
    /** Stone on screen (MainActivity between onStart and onStop): safe mode serves only then. */
    @Volatile var visible = false
    var envId: String = ""; private set
    var publicKey: PublicKey? = null; private set
    var instanceId: String = ""; private set
    private var store: StoneSettingsStore? = null
    val loaded: Boolean get() = store != null

    fun load(store: StoneSettingsStore) {
        this.store = store
        instanceId = store.look("hub.instance", "").ifBlank { "inst_" + random(12).also { store.setLook("hub.instance", it) } }
            .let { if (it.startsWith("inst_")) it else "inst_$it" }
        disconnectedByUser = store.look("hub.disconnected", "false").toBoolean()
        envId = store.look("hub.env", "")
        publicKey = parseKey(store.look("hub.envKey", ""))
        linked = envId.isNotBlank() && publicKey != null && !disconnectedByUser
        environment = store.look("hub.environment", ""); mode = store.look("hub.mode", "")
        Packages.load(store)
    }

    /** gatewayhub/link: a new environment replaces the previous one and its packages. */
    fun link(env: String, keyBase64: String): Boolean {
        if (disconnectedByUser || !Regex("^env_[A-Za-z0-9_-]{8,64}$").matches(env)) return false
        val key = parseKey(keyBase64) ?: return false
        if (env != envId || keyBase64 != store?.look("hub.envKey", "")) Packages.clear()
        envId = env; publicKey = key; linked = true
        store?.setLook("hub.env", env); store?.setLook("hub.envKey", keyBase64)
        return true
    }

    fun report(environment: String, mode: String, activity: Long) {
        this.environment = environment.take(20); this.mode = mode.take(20); if (activity > 0) lastActivity = activity
        store?.setLook("hub.environment", this.environment); store?.setLook("hub.mode", this.mode)
    }

    /** "Desconectar de Gateway Hub": forgets the environment and every package, and refuses Gateway until reconnected. */
    fun disconnect() {
        Packages.clear(); envId = ""; publicKey = null; linked = false; disconnectedByUser = true
        store?.setLook("hub.env", ""); store?.setLook("hub.envKey", ""); store?.setLook("hub.disconnected", "true")
    }

    fun allowAgain() { disconnectedByUser = false; store?.setLook("hub.disconnected", "false") }

    /** Package audience: this app and this installation. */
    fun audience(packageName: String) = "$packageName#$instanceId"

    private fun parseKey(base64: String): PublicKey? = runCatching {
        if (base64.isBlank() || base64.length > 512) return null
        KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(base64)))
    }.getOrNull()

    private fun random(size: Int) = ByteArray(size).also(SecureRandom()::nextBytes).joinToString("") { "%02x".format(it) }
}
