package com.zirakusa.stone.nativeapp.automation.provider

import androidx.compose.runtime.mutableStateListOf
import com.zirakusa.stone.nativeapp.automation.actions.ActionRisk
import com.zirakusa.stone.nativeapp.automation.actions.StoneAction
import com.zirakusa.stone.nativeapp.data.persistence.StoneSettingsStore
import org.json.JSONArray
import org.json.JSONObject
import java.security.PublicKey
import java.security.Signature
import java.util.Base64

/** The five things a package may allow, mapped onto the risk every Stone tool already declares. */
enum class Act(val key: String) { LEER("leer"), REVISAR("revisar"), ESCRIBIR("escribir"), MODIFICAR("modificar"), REVERTIR("revertir") }

class PackageException(val code: String) : Exception(code)

/** A permission the owner's Gateway signed: checked once on arrival, then looked up on each call. */
data class ExecPackage(val id: String, val agent: String, val label: String, val acts: Set<Act>, val tools: List<String>,
    val nbf: Long, val exp: Long, val approved: Boolean, val envelope: String) {
    fun covers(toolName: String) = tools.isEmpty() || tools.any { p -> if (p.endsWith("*")) toolName.startsWith(p.dropLast(1)) else toolName == p }
}

/**
 * Execution packages. [install] verifies signature, environment, device and dates once; [authorize] is an
 * in-memory lookup per call. Reading-only packages are approved by themselves; any other waits for one tap
 * in Stone. CRITICAL actions still ask on screen on every call (see StoneAutomation).
 */
object Packages {
    const val MAX_BYTES = 8_192
    const val MAX_KEPT = 64
    private const val SKEW_S = 300L
    private const val MAX_LIFE_S = 366L * 86_400
    private val ID = Regex("^pkg_[A-Za-z0-9_-]{8,64}$")
    val all = mutableStateListOf<ExecPackage>()
    private var store: StoneSettingsStore? = null

    fun load(store: StoneSettingsStore) {
        this.store = store
        val key = ProviderState.publicKey
        val saved = runCatching { JSONArray(store.look("hub.packages", "[]")) }.getOrDefault(JSONArray())
        all.clear()
        // Stored packages are verified again on load: a changed file cannot grant anything.
        for (i in 0 until saved.length()) runCatching {
            val o = saved.getJSONObject(i)
            val p = verify(JSONObject(o.getString("envelope")), key ?: return@runCatching, nowS()).copy(approved = o.optBoolean("approved"))
            if (p.exp + SKEW_S > nowS()) all += p
        }
    }

    /** Verifies an offered envelope; returns it pending (or approved when it only reads). */
    fun install(envelope: JSONObject, packageName: String, now: Long = nowS()): ExecPackage {
        if (!ProviderState.linked) throw PackageException("NOT_LINKED")
        val p = verify(envelope, ProviderState.publicKey ?: throw PackageException("NOT_LINKED"), now, packageName)
        all.removeAll { it.id == p.id }
        if (all.size >= MAX_KEPT) all.removeAll { it.exp + SKEW_S < now }
        if (all.size >= MAX_KEPT) throw PackageException("TOO_MANY")
        val stored = p.copy(approved = p.acts == setOf(Act.LEER))
        all += stored; save(); return stored
    }

    private fun verify(envelope: JSONObject, key: PublicKey, now: Long, packageName: String? = null): ExecPackage {
        val bytes = runCatching { Base64.getUrlDecoder().decode(envelope.getString("pkg")) }.getOrNull() ?: throw PackageException("MALFORMED")
        if (bytes.size > MAX_BYTES) throw PackageException("TOO_LARGE")
        val sig = runCatching { Base64.getUrlDecoder().decode(envelope.getString("sig")) }.getOrNull() ?: throw PackageException("MALFORMED")
        val ok = runCatching { Signature.getInstance("SHA256withECDSA").run { initVerify(key); update(bytes); verify(sig) } }.getOrDefault(false)
        if (!ok) throw PackageException("BAD_SIGNATURE")
        val j = runCatching { JSONObject(String(bytes)) }.getOrNull() ?: throw PackageException("MALFORMED")
        val id = j.optString("id")
        val nbf = j.optLong("nbf"); val exp = j.optLong("exp")
        when {
            j.optInt("v") != 1 -> throw PackageException("UNSUPPORTED_VERSION")
            !ID.matches(id) -> throw PackageException("MALFORMED")
            j.optString("env") != ProviderState.envId -> throw PackageException("WRONG_ENVIRONMENT")
            packageName != null && j.optString("aud") != ProviderState.audience(packageName) -> throw PackageException("WRONG_DEVICE")
            exp <= nbf || exp - nbf > MAX_LIFE_S -> throw PackageException("BAD_DATES")
            now > exp + SKEW_S -> throw PackageException("EXPIRED")
        }
        val acts = j.optJSONArray("acts")?.let { a -> (0 until a.length()).mapNotNull { i -> Act.entries.firstOrNull { it.key == a.optString(i) } } }.orEmpty().toSet()
        if (acts.isEmpty()) throw PackageException("NO_ACTS")
        val tools = j.optJSONArray("tools")?.let { a -> (0 until minOf(a.length(), 100)).map { a.optString(it) }.filter { Regex("^[a-z][a-z0-9_]{0,63}\\*?$").matches(it) } }.orEmpty()
        val agent = j.optString("agent").takeIf { Regex("^(\\*|[A-Za-z0-9_-]{1,40})$").matches(it) } ?: throw PackageException("MALFORMED")
        return ExecPackage(id, agent, j.optString("label").take(80), acts, tools, nbf, exp, false, envelope.toString())
    }

    fun approve(id: String) = change(id) { it.copy(approved = true) }
    fun reject(id: String) { all.removeAll { it.id == id }; save() }
    fun revoke(id: String) = reject(id)
    fun clear() { all.clear(); save() }

    /** The act that covers [action] for [agent] under [packageId], or throws the reason. */
    fun authorize(packageId: String, agent: String, action: StoneAction, args: JSONObject, now: Long = nowS()): Act {
        val p = all.firstOrNull { it.id == packageId } ?: throw PackageException("NO_PACKAGE")
        if (!p.approved) throw PackageException("PACKAGE_PENDING")
        if (p.agent != "*" && p.agent != agent) throw PackageException("OTHER_AGENT")
        if (now < p.nbf - SKEW_S || now > p.exp + SKEW_S) throw PackageException("EXPIRED")
        if (!p.covers(action.toolName)) throw PackageException("TOOL_NOT_IN_PACKAGE")
        val act = actFor(action, args)
        if (act !in p.acts && !(act == Act.LEER && Act.REVISAR in p.acts)) throw PackageException("ACT_NOT_IN_PACKAGE")
        return act
    }

    /** Whether some approved package of [agent] could cover [action] at all (for tools/list). */
    fun lists(agent: String, action: StoneAction, now: Long = nowS()): Boolean = all.any { p ->
        p.approved && (p.agent == "*" || p.agent == agent) && now <= p.exp + SKEW_S && p.covers(action.toolName) &&
            (actFor(action, JSONObject()) in p.acts || (Act.REVISAR in p.acts && action.params.any { it.name == "dryRun" }))
    }

    fun actFor(a: StoneAction, args: JSONObject): Act = when {
        a.name == "metadata.undo" -> Act.REVERTIR
        a.risk == ActionRisk.READ -> Act.LEER
        // A preview counts as "revisar" only for tools that declare dryRun (the others would reject it anyway).
        args.optBoolean("dryRun") && a.params.any { it.name == "dryRun" } -> Act.REVISAR
        a.risk == ActionRisk.REVERSIBLE -> Act.ESCRIBIR
        else -> Act.MODIFICAR // PERSISTENT and CRITICAL (CRITICAL still asks on screen)
    }

    fun statusJson(): JSONArray = JSONArray(all.map { JSONObject().put("id", it.id).put("status", if (it.approved) "active" else "pending").put("exp", it.exp) })

    private fun change(id: String, transform: (ExecPackage) -> ExecPackage) {
        val i = all.indexOfFirst { it.id == id }; if (i >= 0) { all[i] = transform(all[i]); save() }
    }

    private fun save() {
        store?.setLook("hub.packages", JSONArray(all.map { JSONObject().put("envelope", it.envelope).put("approved", it.approved) }).toString())
    }

    private fun nowS() = System.currentTimeMillis() / 1000
}
