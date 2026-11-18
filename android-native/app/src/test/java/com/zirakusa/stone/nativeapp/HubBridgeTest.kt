package com.zirakusa.stone.nativeapp

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Looper
import com.zirakusa.stone.nativeapp.automation.ApiMode
import com.zirakusa.stone.nativeapp.automation.HubBridge
import com.zirakusa.stone.nativeapp.automation.HubBridgeService
import com.zirakusa.stone.nativeapp.automation.actions.ActionRisk
import com.zirakusa.stone.nativeapp.automation.actions.StoneAutomation
import com.zirakusa.stone.nativeapp.automation.auth.ApiTokens
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/** Gateway Hub local bridge: identity from Android only, read-only on list and call, bounded messages. */
@RunWith(RobolectricTestRunner::class)
class HubBridgeTest {
    @After fun reset() { ApiMode.allowHubRedirect(false); ApiMode.enable(false); ApiTokens.replaceForTests(emptyList()) }

    private fun rpc(method: String, params: JSONObject = JSONObject(), id: Int = 1) =
        JSONObject().put("jsonrpc", "2.0").put("id", id).put("method", method).put("params", params).toString()

    /** Runs [block] off the main thread while the main looper keeps going (tools run on Dispatchers.Main). */
    private fun <T> offMain(block: () -> T): T {
        var result: Result<T>? = null
        val worker = Thread { result = runCatching(block) }.apply { start() }
        while (worker.isAlive) { shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(5) }
        return result!!.getOrThrow()
    }

    @Test fun onlyGatewaySignedLikeStoneIsAccepted() {
        assertEquals("NOT_GATEWAY", HubBridge.callerRefusal(listOf("com.example.evil")) { true })
        assertEquals("NOT_GATEWAY", HubBridge.callerRefusal(emptyList()) { true })
        assertEquals("NOT_SAME_DEVELOPER", HubBridge.callerRefusal(listOf("com.zirakusa.gatewayhub")) { false })
        assertNull(HubBridge.callerRefusal(listOf("com.other.shareduid", "com.zirakusa.gatewayhub")) { it == "com.zirakusa.gatewayhub" })
    }

    @Test fun theUsersSwitchesStillDecide() {
        assertEquals("REDIRECT_OFF", HubBridge.switchRefusal())
        ApiMode.allowHubRedirect(true)
        assertEquals("API_OFF", HubBridge.switchRefusal())   // documented dependency, kept in phase 1
        ApiMode.enable(true)
        assertNull(HubBridge.switchRefusal())
    }

    @Test fun toolsListHasOnlyReadToolsAndOneProbe() {
        val reply = JSONObject(HubBridge.handle(rpc("tools/list"))!!)
        val tools = reply.getJSONObject("result").getJSONArray("tools")
        val names = (0 until tools.length()).map { tools.getJSONObject(it).getString("name") }
        assertTrue(names.isNotEmpty())
        assertTrue(names.all { StoneAutomation.find(it)!!.risk == ActionRisk.READ })
        assertTrue(StoneAutomation.actions.any { it.risk != ActionRisk.READ })   // there were tools to hide
        val probes = (0 until tools.length()).map { tools.getJSONObject(it) }.filter { it.optJSONObject("_meta")?.optBoolean(HubBridge.PROBE_META) == true }
        assertEquals(listOf("stone_help"), probes.map { it.getString("name") })
        val size = HubBridge.handle(rpc("tools/list"))!!.length
        println("BRIDGE_TOOLS_LIST_CHARS=$size")
        assertTrue("tools/list ($size chars) must fit one message", size < HubBridge.MAX_CHARS / 2)
    }

    @Test fun aChangeIsRefusedBeforeItRuns() {
        val write = StoneAutomation.actions.first { it.risk != ActionRisk.READ }
        val reply = JSONObject(HubBridge.handle(rpc("tools/call", JSONObject().put("name", write.toolName).put("arguments", JSONObject())))!!)
        assertEquals("READ_ONLY_BRIDGE", reply.getJSONObject("result").getJSONObject("structuredContent").getJSONObject("error").getString("code"))
    }

    @Test fun theProbeToolRunsThroughTheBridge() {
        val reply = offMain { HubBridge.handle(rpc("tools/call", JSONObject().put("name", "stone_help").put("arguments", JSONObject().put("query", "volume")))) }
        val result = JSONObject(reply!!).getJSONObject("result")
        assertFalse(result.toString(), result.getBoolean("isError"))
        assertEquals("1", JSONObject(HubBridge.handle(rpc("initialize", id = 1))!!).get("id").toString())
    }

    @Test fun badOrOversizedMessagesAreRefused() {
        assertEquals(-32600, JSONObject(HubBridge.handle("x".repeat(HubBridge.MAX_CHARS + 1))!!).getJSONObject("error").getInt("code"))
        assertEquals(-32600, JSONObject(HubBridge.handle("[]")!!).getJSONObject("error").getInt("code"))
        assertEquals(-32600, JSONObject(HubBridge.handle(null)!!).getJSONObject("error").getInt("code"))
        assertNull(HubBridge.handle("""{"jsonrpc":"2.0","method":"notifications/initialized"}"""))
    }

    @Test fun aPausedHubTokenStopsTheBridge() {
        ApiTokens.setPaused(ApiMode.hubToken().id, true)
        assertEquals(-32001, JSONObject(HubBridge.handle(rpc("tools/list"))!!).getJSONObject("error").getInt("code"))
    }

    @Test fun theServiceIsProtectedAndAnswersOnlyItsAction() {
        val context = RuntimeEnvironment.getApplication()
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SERVICES).services!!
            .single { it.name.endsWith("HubBridgeService") }
        assertTrue(info.exported)
        assertEquals("com.zirakusa.gatewayhub.permission.LINK", info.permission)
        val service = Robolectric.setupService(HubBridgeService::class.java)
        assertNull(service.onBind(Intent("something.else")))
        assertNotNull(service.onBind(Intent(HubBridge.ACTION)))
    }
}
