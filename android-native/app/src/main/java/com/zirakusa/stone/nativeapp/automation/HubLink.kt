package com.zirakusa.stone.nativeapp.automation

import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import com.zirakusa.stone.nativeapp.data.persistence.StoneSettingsStore

/**
 * «Redireccionar a Hub»: the Gateway Hub app on this phone gets the hub's link to Stone by itself, with no copy and
 * paste. Three locks, all required:
 * 1. Android lets only apps signed with Stone's key call it (signature permission on the provider).
 * 2. The caller's signature is checked again here.
 * 3. The user turned «Redireccionar a Hub» on in Stone.
 * The answer is the link of the token reserved for the hub; it never leaves the phone except to the hub.
 */
object HubLink {
    const val METHOD = "link"
    const val KEY_LINK = "link"
    const val KEY_ERROR = "error"
    const val KEY_MESSAGE = "message"

    fun answer(sameDeveloper: Boolean): Bundle = when {
        !sameDeveloper -> error("NOT_SAME_DEVELOPER", "Solo apps firmadas por el mismo desarrollador que Stone.")
        !ApiMode.hubRedirect -> error("REDIRECT_OFF", "Activa «Redireccionar a Hub» en Stone › Ajustes › Modo MCP.")
        !ApiMode.relayAvailable -> error("NO_RELAY", "Esta versión de Stone no tiene enlace público (relay).")
        else -> Bundle().apply { putString(KEY_LINK, ApiMode.hubLink()) }
    }

    /** «Conectar todas» in Gateway Hub: the user's tap there turns Stone's «Redireccionar a Hub» on. */
    fun connect(sameDeveloper: Boolean): Bundle {
        if (sameDeveloper && !ApiMode.hubRedirect) ApiMode.redirectToHub(true)
        return answer(sameDeveloper)
    }

    /** One tool for Lucía, on the phone: [request] is a JSON-RPC «tools/call»; the answer comes back in «response». */
    fun tool(sameDeveloper: Boolean, request: String?): Bundle = when {
        !sameDeveloper || !ApiMode.hubRedirect -> answer(sameDeveloper)
        request.isNullOrBlank() -> error("BAD_REQUEST", "Falta la petición.")
        else -> com.zirakusa.stone.nativeapp.automation.server.McpServer.local(request)?.let { r -> Bundle().apply { putString("response", r) } }
            ?: error("NOT_READY", "Stone aún no está lista.")
    }

    private fun error(code: String, message: String) = Bundle().apply { putString(KEY_ERROR, code); putString(KEY_MESSAGE, message) }
}

/** content://<applicationId>.hublink: only call("link"), call("connect") and call("tool"), see [HubLink]. Nothing else is exposed. */
class HubLinkProvider : ContentProvider() {
    override fun onCreate() = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (method !in setOf(HubLink.METHOD, "connect", "tool")) return null
        val context = context ?: return null
        val caller = callingPackage
        val same = caller != null && context.packageManager.checkSignatures(context.packageName, caller) == PackageManager.SIGNATURE_MATCH
        if (same && !ApiMode.loaded) ApiMode.load(StoneSettingsStore(context.applicationContext), context.applicationContext)
        return when (method) {
            "connect" -> HubLink.connect(same)
            "tool" -> HubLink.tool(same, extras?.getString("request"))
            else -> HubLink.answer(same)
        }
    }

    override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<out String>?) = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?) = 0
}
