package com.zirakusa.stone.nativeapp.automation

import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import com.zirakusa.stone.nativeapp.automation.cloudflare.Cloudflare
import com.zirakusa.stone.nativeapp.data.persistence.StoneSettingsStore

/**
 * Automatic, secure linking with the Gateway Hub app on the same phone ("Redirección hub").
 * Three locks, all required:
 * 1. Android lets only apps signed with Stone's key call it (signature permission on the provider).
 * 2. The caller's signature is checked again here.
 * 3. The user turned "Redirección hub" on in Stone (and API mode).
 * The answer is the link of the token reserved for the hub; it never leaves the phone except to the hub.
 */
object HubLink {
    const val METHOD = "link"
    /** Gateway Hub tells Stone its mode: arg "safe" (MCP only while Stone is on screen) or "background". */
    const val METHOD_MODE = "mode"
    const val KEY_MODE = "mode"
    /** The Cloudflare token shared with Gateway Hub: call("cloudflare") reads it; with a token in arg it is kept if newer. */
    const val METHOD_CLOUDFLARE = "cloudflare"
    const val KEY_LINK = "link"
    const val KEY_ERROR = "error"
    const val KEY_MESSAGE = "message"

    fun answer(sameDeveloper: Boolean): Bundle = when {
        !sameDeveloper -> error("NOT_SAME_DEVELOPER", "Solo apps firmadas por el mismo desarrollador que Stone.")
        !ApiMode.hubRedirect -> error("REDIRECT_OFF", "Activa «Redirección hub» en Stone › Ajustes › Modo API › Gateway Hub.")
        !ApiMode.enabled -> error("API_OFF", "Activa MCP en Stone › Ajustes › Modo API.")
        !ApiMode.relayAvailable -> error("NO_RELAY", "Esta versión de Stone no tiene enlace público (relay).")
        else -> Bundle().apply { putString(KEY_LINK, ApiMode.shareText(ApiMode.hubToken().secret)) }
    }

    /**
     * Applies the mode chosen in Gateway Hub. Only the signature lock applies: the mode never gives out a
     * link or a token, it only decides when Stone serves MCP. The answer is the mode now in force.
     */
    fun answerMode(sameDeveloper: Boolean, mode: String?): Bundle = when {
        !sameDeveloper -> error("NOT_SAME_DEVELOPER", "Solo apps firmadas por el mismo desarrollador que Stone.")
        mode != "safe" && mode != "background" -> error("INVALID_MODE", "Modo: safe o background.")
        else -> { ApiMode.useHubSafeMode(mode == "safe"); Bundle().apply { putString(KEY_MODE, mode) } }
    }

    /** Same locks as the link (signature + "Redirección hub"): it is a secret. */
    fun answerCloudflare(sameDeveloper: Boolean, token: String?, expiresAt: Long, savedAt: Long): Bundle = when {
        !sameDeveloper -> error("NOT_SAME_DEVELOPER", "Solo apps firmadas por el mismo desarrollador que Stone.")
        !ApiMode.hubRedirect -> error("REDIRECT_OFF", "Activa «Redirección hub» en Stone › Ajustes › Modo API › Gateway Hub.")
        token != null -> Bundle().apply { putBoolean("saved", Cloudflare.receive(token, expiresAt, savedAt)) }
        else -> Bundle().apply {
            Cloudflare.token()?.let { putString("token", it); putLong("expiresAt", Cloudflare.expiresAt); putLong("savedAt", Cloudflare.savedAt) }
        }
    }

    private fun error(code: String, message: String) = Bundle().apply { putString(KEY_ERROR, code); putString(KEY_MESSAGE, message) }
}

/** content://<applicationId>.hublink: call("link"), call("mode", …) and call("cloudflare", …), see [HubLink]. Nothing else is exposed. */
class HubLinkProvider : ContentProvider() {
    override fun onCreate() = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (method != HubLink.METHOD && method != HubLink.METHOD_MODE && method != HubLink.METHOD_CLOUDFLARE) return null
        val context = context ?: return null
        val caller = callingPackage
        val same = caller != null && context.packageManager.checkSignatures(context.packageName, caller) == PackageManager.SIGNATURE_MATCH
        if (same && !ApiMode.loaded) ApiMode.load(StoneSettingsStore(context.applicationContext), context.applicationContext)
        return when (method) {
            HubLink.METHOD_MODE -> HubLink.answerMode(same, arg)
            HubLink.METHOD_CLOUDFLARE -> HubLink.answerCloudflare(same, arg, extras?.getLong("expiresAt") ?: 0, extras?.getLong("savedAt") ?: 0)
            else -> HubLink.answer(same)
        }
    }

    override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<out String>?) = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?) = 0
}
