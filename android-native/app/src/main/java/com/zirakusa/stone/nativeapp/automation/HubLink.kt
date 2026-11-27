package com.zirakusa.stone.nativeapp.automation

import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import com.zirakusa.stone.nativeapp.data.persistence.StoneSettingsStore

/**
 * How Arka runs Stone's Modo MCP. Two locks, both required: Android lets only apps signed with Stone's key call
 * [HubLinkProvider] (signature permission), and the caller's signature is checked again here. «link» gives the hub link
 * (only with the public link on), «connect» / «disconnect» turn it on or off (Arka's «Activar MCP», the user's tap there is
 * the consent), «status» says how it is, and «tool» runs one JSON-RPC request on the phone for Lucía (Arka's «Permitir MCP
 * local» decides whether she asks). The link never leaves the phone except to the hub.
 */
object HubLink {
    const val METHOD = "link"
    const val KEY_LINK = "link"
    const val KEY_ERROR = "error"
    const val KEY_MESSAGE = "message"

    fun answer(sameDeveloper: Boolean): Bundle = when {
        !sameDeveloper -> error("NOT_SAME_DEVELOPER", "Solo apps firmadas por el mismo desarrollador que Stone.")
        !ApiMode.remote -> error("REDIRECT_OFF", "Activa «Redireccionar a Hub» en Stone › Ajustes › Modo MCP.")
        !ApiMode.relayAvailable -> error("NO_RELAY", "Esta versión de Stone no tiene enlace público (relay).")
        else -> Bundle().apply { putString(KEY_LINK, ApiMode.hubLink()) }
    }

    fun connect(sameDeveloper: Boolean): Bundle {
        if (sameDeveloper && !ApiMode.remote) ApiMode.turnRemote(true)
        return answer(sameDeveloper)
    }

    fun disconnect(sameDeveloper: Boolean): Bundle =
        if (!sameDeveloper) answer(false) else { if (ApiMode.remote) ApiMode.turnRemote(false); status(true) }

    fun status(sameDeveloper: Boolean): Bundle = if (!sameDeveloper) answer(false) else Bundle().apply {
        putBoolean("remote", ApiMode.remote)
        putBoolean("enabled", ApiMode.enabled)
        putBoolean("localMcp", ApiMode.localMcp)
        putString("state", ApiMode.relayState.name)
    }

    fun tool(sameDeveloper: Boolean, request: String?): Bundle = when {
        !sameDeveloper -> answer(false)
        request.isNullOrBlank() -> error("BAD_REQUEST", "Falta la petición.")
        !ApiMode.localMcp -> error("LOCAL_DISABLED", "Activa «Permitir MCP local» en Stone › Ajustes › Modo MCP.")
        else -> com.zirakusa.stone.nativeapp.automation.server.McpServer.local(request)?.let { r -> Bundle().apply { putString("response", r) } }
            ?: error("NOT_READY", "Stone aún no está lista.")
    }

    private fun error(code: String, message: String) = Bundle().apply { putString(KEY_ERROR, code); putString(KEY_MESSAGE, message) }
}

/** content://<applicationId>.hublink: only the methods of [HubLink]. Nothing else is exposed. */
class HubLinkProvider : ContentProvider() {
    override fun onCreate() = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (method !in METHODS) return null
        val context = context ?: return null
        val caller = callingPackage
        val same = caller != null && context.packageManager.checkSignatures(context.packageName, caller) == PackageManager.SIGNATURE_MATCH
        if (same && !ApiMode.loaded) ApiMode.load(StoneSettingsStore(context.applicationContext), context.applicationContext)
        return when (method) {
            "connect" -> HubLink.connect(same)
            "disconnect" -> HubLink.disconnect(same)
            "status" -> HubLink.status(same)
            "tool" -> HubLink.tool(same, extras?.getString("request"))
            else -> HubLink.answer(same)
        }
    }

    override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<out String>?) = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?) = 0

    private companion object { val METHODS = setOf(HubLink.METHOD, "connect", "disconnect", "status", "tool") }
}
