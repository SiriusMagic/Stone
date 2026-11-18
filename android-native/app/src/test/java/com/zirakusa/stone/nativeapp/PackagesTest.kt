package com.zirakusa.stone.nativeapp

import android.os.Looper
import com.zirakusa.stone.nativeapp.automation.actions.ActionRisk
import com.zirakusa.stone.nativeapp.automation.actions.StoneAutomation
import com.zirakusa.stone.nativeapp.automation.provider.Act
import com.zirakusa.stone.nativeapp.automation.provider.Cancellation
import com.zirakusa.stone.nativeapp.automation.provider.GatewayGrant
import com.zirakusa.stone.nativeapp.automation.provider.PackageException
import com.zirakusa.stone.nativeapp.automation.provider.Packages
import com.zirakusa.stone.nativeapp.automation.provider.ProviderBridge
import com.zirakusa.stone.nativeapp.automation.provider.ProviderState
import com.zirakusa.stone.nativeapp.data.persistence.StoneSettingsStore
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64

/** Execution packages: signed once by the owner's Gateway, checked on arrival, looked up on every call. */
@RunWith(RobolectricTestRunner::class)
class PackagesTest {
    private val app = RuntimeEnvironment.getApplication()
    private val env = "env_test0123456789"
    private val keys: KeyPair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
    private val now = System.currentTimeMillis() / 1000
    private lateinit var aud: String

    @Before fun link() {
        ProviderState.load(StoneSettingsStore(app)); ProviderState.allowAgain()
        assertTrue(ProviderState.link(env, Base64.getEncoder().encodeToString(keys.public.encoded)))
        aud = ProviderState.audience(BuildConfig.APPLICATION_ID)
    }
    @After fun reset() { Packages.clear() }

    private fun pkg(id: String, acts: List<String>, agent: String = "a1", tools: List<String> = emptyList(), signer: KeyPair = keys,
        audience: String = aud, environment: String = env, exp: Long = now + 3600, nbf: Long = now - 10): JSONObject {
        val bytes = JSONObject().put("v", 1).put("id", id).put("env", environment).put("aud", audience).put("agent", agent)
            .put("label", "Prueba").put("acts", JSONArray(acts)).put("tools", JSONArray(tools)).put("nbf", nbf).put("exp", exp)
            .toString().toByteArray()
        val sig = Signature.getInstance("SHA256withECDSA").run { initSign(signer.private); update(bytes); sign() }
        return JSONObject().put("pkg", Base64.getUrlEncoder().encodeToString(bytes)).put("sig", Base64.getUrlEncoder().encodeToString(sig))
    }
    private fun code(block: () -> Unit) = (runCatching(block).exceptionOrNull() as PackageException).code
    private val read = StoneAutomation.actions.first { it.risk == ActionRisk.READ }
    private val reversible = StoneAutomation.actions.first { it.risk == ActionRisk.REVERSIBLE }
    private val persistent = StoneAutomation.actions.first { it.risk == ActionRisk.PERSISTENT }

    @Test fun readingIsActiveAtOnceTheRestWaitsForTheUser() {
        assertTrue(Packages.install(pkg("pkg_read00001", listOf("leer")), BuildConfig.APPLICATION_ID).approved)
        val write = Packages.install(pkg("pkg_write0001", listOf("escribir", "modificar")), BuildConfig.APPLICATION_ID)
        assertFalse(write.approved)
        assertEquals("PACKAGE_PENDING", code { Packages.authorize(write.id, "a1", reversible, JSONObject()) })
        Packages.approve(write.id)
        assertEquals(Act.ESCRIBIR, Packages.authorize(write.id, "a1", reversible, JSONObject()))
        assertEquals(Act.MODIFICAR, Packages.authorize(write.id, "a1", persistent, JSONObject()))
        assertEquals("ACT_NOT_IN_PACKAGE", code { Packages.authorize("pkg_read00001", "a1", reversible, JSONObject()) })
    }

    @Test fun forgedOrMisdirectedPackagesAreRefused() {
        val other = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
        val id = BuildConfig.APPLICATION_ID
        assertEquals("BAD_SIGNATURE", code { Packages.install(pkg("pkg_forged001", listOf("leer"), signer = other), id) })
        val tampered = pkg("pkg_tamper01", listOf("leer")).let { e ->
            val j = JSONObject(String(Base64.getUrlDecoder().decode(e.getString("pkg")))).put("acts", JSONArray(listOf("modificar")))
            e.put("pkg", Base64.getUrlEncoder().encodeToString(j.toString().toByteArray()))
        }
        assertEquals("BAD_SIGNATURE", code { Packages.install(tampered, id) })
        assertEquals("WRONG_DEVICE", code { Packages.install(pkg("pkg_device01", listOf("leer"), audience = "$id#inst_other"), id) })
        assertEquals("WRONG_ENVIRONMENT", code { Packages.install(pkg("pkg_env00001", listOf("leer"), environment = "env_otherotherx"), id) })
        assertEquals("EXPIRED", code { Packages.install(pkg("pkg_old00001", listOf("leer"), nbf = now - 7200, exp = now - 3600), id) })
        assertTrue(Packages.all.isEmpty())
    }

    @Test fun agentsToolsAndReviewAreEnforced() {
        val p = Packages.install(pkg("pkg_scope001", listOf("revisar"), agent = "claude", tools = listOf(persistent.toolName.substringBefore('_') + "_*")), BuildConfig.APPLICATION_ID)
        Packages.approve(p.id)
        assertEquals("OTHER_AGENT", code { Packages.authorize(p.id, "chatgpt", persistent, JSONObject().put("dryRun", true)) })
        assertEquals(Act.REVISAR, Packages.authorize(p.id, "claude", persistent, JSONObject().put("dryRun", true)))
        assertEquals("ACT_NOT_IN_PACKAGE", code { Packages.authorize(p.id, "claude", persistent, JSONObject()) })  // a real change is not a review
        val critical = StoneAutomation.actions.first { it.risk == ActionRisk.CRITICAL }
        assertEquals(Act.MODIFICAR, Packages.actFor(critical, JSONObject().put("dryRun", true)))   // no dryRun param: not a review
        assertEquals("NO_PACKAGE", code { Packages.authorize("pkg_missing01", "claude", read, JSONObject()) })
    }

    @Test fun storedPackagesAreVerifiedAgainOnLoad() {
        Packages.install(pkg("pkg_keep0001", listOf("leer")), BuildConfig.APPLICATION_ID)
        val store = StoneSettingsStore(app)
        val saved = JSONArray(store.look("hub.packages", "[]"))
        assertEquals(1, saved.length())
        // Someone edits the stored package to grant more: the signature no longer matches and it is dropped.
        val envelope = JSONObject(saved.getJSONObject(0).getString("envelope"))
        val j = JSONObject(String(Base64.getUrlDecoder().decode(envelope.getString("pkg")))).put("acts", JSONArray(listOf("modificar")))
        envelope.put("pkg", Base64.getUrlEncoder().encodeToString(j.toString().toByteArray()))
        store.setLook("hub.packages", JSONArray().put(JSONObject().put("envelope", envelope.toString()).put("approved", true)).toString())
        ProviderState.load(store)
        assertTrue(Packages.all.isEmpty())
    }

    private fun rpc(method: String, params: JSONObject) =
        JSONObject().put("jsonrpc", "2.0").put("id", 9).put("method", method).put("params", params).toString()
    private fun grant(packageId: String, agent: String = "a1") = JSONObject().put(GatewayGrant.META, JSONObject().put("agent", agent).put("package", packageId))
    private fun <T> offMain(block: () -> T): T {
        var result: Result<T>? = null
        val worker = Thread { result = runCatching(block) }.apply { start() }
        while (worker.isAlive) { shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(5) }
        return result!!.getOrThrow()
    }

    @Test fun theBridgeListsAndRunsOnlyWhatPackagesAllow() {
        Packages.install(pkg("pkg_bridge01", listOf("leer"), tools = listOf("stone_help")), BuildConfig.APPLICATION_ID)
        val list = JSONObject(ProviderBridge.handle(rpc("tools/list", JSONObject().put("_meta", grant("pkg_bridge01"))))!!)
        val names = list.getJSONObject("result").getJSONArray("tools").let { a -> (0 until a.length()).map { a.getJSONObject(it).getString("name") } }
        assertEquals(listOf("stone_help"), names)
        val ok = offMain { ProviderBridge.handle(rpc("tools/call", JSONObject().put("name", "stone_help").put("arguments", JSONObject()).put("_meta", grant("pkg_bridge01")))) }
        assertFalse(JSONObject(ok!!).getJSONObject("result").getBoolean("isError"))
        val denied = ProviderBridge.handle(rpc("tools/call", JSONObject().put("name", reversible.toolName).put("arguments", JSONObject()).put("_meta", grant("pkg_bridge01"))))
        assertEquals("TOOL_NOT_IN_PACKAGE", JSONObject(denied!!).getJSONObject("result").getJSONObject("structuredContent").getJSONObject("error").getString("code"))
        Packages.install(pkg("pkg_bridge02", listOf("leer")), BuildConfig.APPLICATION_ID)     // every tool, reading only
        val change = ProviderBridge.handle(rpc("tools/call", JSONObject().put("name", reversible.toolName).put("arguments", JSONObject()).put("_meta", grant("pkg_bridge02"))))
        assertEquals("ACT_NOT_IN_PACKAGE", JSONObject(change!!).getJSONObject("result").getJSONObject("structuredContent").getJSONObject("error").getString("code"))
    }

    @Test fun linkOfferAndRevokeOverTheContract() {
        val link = JSONObject(ProviderBridge.handle(rpc("gatewayhub/link", JSONObject().put("env", env).put("publicKey", Base64.getEncoder().encodeToString(keys.public.encoded))))!!)
        assertEquals(ProviderState.instanceId, link.getJSONObject("result").getString("instance"))
        val offered = JSONObject(ProviderBridge.handle(rpc("gatewayhub/packages/offer", JSONObject().put("packages", JSONArray().put(pkg("pkg_offer001", listOf("modificar"))).put(JSONObject().put("pkg", "x")))))!!)
        val results = offered.getJSONObject("result").getJSONArray("packages")
        assertEquals("pending", results.getJSONObject(0).getString("status"))
        assertEquals("MALFORMED", results.getJSONObject(1).getString("error"))
        ProviderBridge.handle(rpc("gatewayhub/packages/revoke", JSONObject().put("id", "pkg_offer001")))
        assertTrue(Packages.all.none { it.id == "pkg_offer001" })
        ProviderBridge.handle(rpc("gatewayhub/state", JSONObject().put("environment", "paused").put("mode", "background")))
        assertEquals("paused", ProviderState.environment); assertEquals("background", ProviderState.mode)
        ProviderState.disconnect()
        assertTrue(Packages.all.isEmpty())
        assertEquals(-32001, JSONObject(ProviderBridge.handle(rpc("gatewayhub/link", JSONObject().put("env", env).put("publicKey", Base64.getEncoder().encodeToString(keys.public.encoded))))!!).getJSONObject("error").getInt("code"))
    }

    @Test fun cancellationNamesOnlyRealRequests() {
        assertEquals("7", Cancellation.target("""{"jsonrpc":"2.0","method":"notifications/cancelled","params":{"requestId":7,"reason":"user"}}"""))
        assertNull(Cancellation.target("""{"jsonrpc":"2.0","id":1,"method":"tools/call"}"""))
        assertEquals("1", Cancellation.key("""{"jsonrpc":"2.0","id":1,"method":"tools/call"}"""))
        assertNull(Cancellation.key("""{"jsonrpc":"2.0","id":1,"method":"tools/list"}"""))
        assertNull(Cancellation.target("not json"))
    }
}
