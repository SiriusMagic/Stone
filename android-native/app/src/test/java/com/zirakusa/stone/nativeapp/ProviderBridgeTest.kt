package com.zirakusa.stone.nativeapp

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Looper
import com.zirakusa.stone.nativeapp.automation.auth.AccessMode
import com.zirakusa.stone.nativeapp.automation.provider.GatewayGrant
import com.zirakusa.stone.nativeapp.automation.provider.ProviderBridge
import com.zirakusa.stone.nativeapp.automation.provider.ProviderService
import com.zirakusa.stone.nativeapp.automation.actions.ActionRisk
import com.zirakusa.stone.nativeapp.automation.actions.StoneAutomation
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/** Stone as a provider for Gateway Hub: identity from Android only, delegated rights capped locally, bounded messages. */
@RunWith(RobolectricTestRunner::class)
class ProviderBridgeTest {
    @After fun reset() { com.zirakusa.stone.nativeapp.automation.provider.ProviderState.allowAgain() }

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
        assertEquals("NOT_GATEWAY", ProviderBridge.callerRefusal(listOf("com.example.evil")) { true })
        assertEquals("NOT_GATEWAY", ProviderBridge.callerRefusal(emptyList()) { true })
        assertEquals("NOT_SAME_DEVELOPER", ProviderBridge.callerRefusal(listOf("com.zirakusa.gatewayhub")) { false })
        assertNull(ProviderBridge.callerRefusal(listOf("com.other.shareduid", "com.zirakusa.gatewayhub")) { it == "com.zirakusa.gatewayhub" })
    }

    @Test fun nothingToSwitchOnOnlyTheUsersDisconnect() {
        com.zirakusa.stone.nativeapp.automation.provider.ProviderState.load(
            com.zirakusa.stone.nativeapp.data.persistence.StoneSettingsStore(RuntimeEnvironment.getApplication()))
        assertNull(ProviderBridge.switchRefusal())            // Modo API and Redirección hub are off here: phase 2 needs neither
        com.zirakusa.stone.nativeapp.automation.provider.ProviderState.disconnect()
        assertEquals("DISCONNECTED_BY_USER", ProviderBridge.switchRefusal())
        com.zirakusa.stone.nativeapp.automation.provider.ProviderState.allowAgain()
        assertNull(ProviderBridge.switchRefusal())
    }

    @Test fun toolsListHasOnlyReadToolsAndOneProbe() {
        val reply = JSONObject(ProviderBridge.handle(rpc("tools/list"))!!)
        val tools = reply.getJSONObject("result").getJSONArray("tools")
        val names = (0 until tools.length()).map { tools.getJSONObject(it).getString("name") }
        assertTrue(names.isNotEmpty())
        assertTrue(names.all { StoneAutomation.find(it)!!.risk == ActionRisk.READ })
        assertTrue(StoneAutomation.actions.any { it.risk != ActionRisk.READ })   // there were tools to hide
        val probes = (0 until tools.length()).map { tools.getJSONObject(it) }.filter { it.optJSONObject("_meta")?.optBoolean(ProviderBridge.PROBE_META) == true }
        assertEquals(listOf("stone_help"), probes.map { it.getString("name") })
        val size = ProviderBridge.handle(rpc("tools/list"))!!.length
        println("BRIDGE_TOOLS_LIST_CHARS=$size")
        assertTrue("tools/list ($size chars) must fit one message", size < ProviderBridge.MAX_CHARS / 2)
    }

    @Test fun aChangeIsRefusedBeforeItRuns() {
        val write = StoneAutomation.actions.first { it.risk != ActionRisk.READ }
        val reply = JSONObject(ProviderBridge.handle(rpc("tools/call", JSONObject().put("name", write.toolName).put("arguments", JSONObject())))!!)
        assertEquals("READ_ONLY_BRIDGE", reply.getJSONObject("result").getJSONObject("structuredContent").getJSONObject("error").getString("code"))
    }

    @Test fun theProbeToolRunsThroughTheBridge() {
        val reply = offMain { ProviderBridge.handle(rpc("tools/call", JSONObject().put("name", "stone_help").put("arguments", JSONObject().put("query", "volume")))) }
        val result = JSONObject(reply!!).getJSONObject("result")
        assertFalse(result.toString(), result.getBoolean("isError"))
        assertEquals("1", JSONObject(ProviderBridge.handle(rpc("initialize", id = 1))!!).get("id").toString())
    }

    @Test fun badOrOversizedMessagesAreRefused() {
        assertEquals(-32600, JSONObject(ProviderBridge.handle("x".repeat(ProviderBridge.MAX_CHARS + 1))!!).getJSONObject("error").getInt("code"))
        assertEquals(-32600, JSONObject(ProviderBridge.handle("[]")!!).getJSONObject("error").getInt("code"))
        assertEquals(-32600, JSONObject(ProviderBridge.handle(null)!!).getJSONObject("error").getInt("code"))
        assertNull(ProviderBridge.handle("""{"jsonrpc":"2.0","method":"notifications/initialized"}"""))
    }

    private fun withGrant(method: String, grant: JSONObject, params: JSONObject = JSONObject()) =
        rpc(method, params.put("_meta", JSONObject().put(GatewayGrant.META, grant)))

    @Test fun gatewayCannotRaiseRightsAboveStonesCap() {
        for (mode in listOf("write", "full", "WRITE", "", "evil")) {
            assertEquals(AccessMode.READ, GatewayGrant.from(JSONObject(withGrant("tools/list", JSONObject().put("mode", mode)))).mode)
        }
        val write = StoneAutomation.actions.first { it.risk != ActionRisk.READ }
        val reply = JSONObject(ProviderBridge.handle(withGrant("tools/call", JSONObject().put("mode", "full"),
            JSONObject().put("name", write.toolName).put("arguments", JSONObject())))!!)
        assertEquals("READ_ONLY_BRIDGE", reply.getJSONObject("result").getJSONObject("structuredContent").getJSONObject("error").getString("code"))
    }

    @Test fun toolsGatewayBlocksAreNeitherListedNorRun() {
        val grant = JSONObject().put("blockedTools", org.json.JSONArray().put("player_state").put("NOT A NAME").put(5))
        val tools = JSONObject(ProviderBridge.handle(withGrant("tools/list", grant))!!).getJSONObject("result").getJSONArray("tools")
        val names = (0 until tools.length()).map { tools.getJSONObject(it).getString("name") }
        assertFalse("player_state" in names)
        assertTrue("stone_help" in names)
        val call = offMain { ProviderBridge.handle(withGrant("tools/call", grant, JSONObject().put("name", "player_state").put("arguments", JSONObject()))) }
        assertEquals("TOOL_DISABLED", JSONObject(call!!).getJSONObject("result").getJSONObject("structuredContent").getJSONObject("error").getString("code"))
        assertEquals(setOf("player_state"), GatewayGrant.from(JSONObject(withGrant("tools/list", grant))).disabledTools)
    }

    @Test fun malformedGrantsMeanNoExtraRights() {
        for (text in listOf(rpc("tools/list"), """{"id":1,"method":"tools/list","params":{"_meta":"x"}}""",
            """{"id":1,"method":"tools/list","params":{"_meta":{"${GatewayGrant.META}":[1]}}}""")) {
            val g = GatewayGrant.from(JSONObject(text))
            assertEquals(AccessMode.READ, g.mode); assertTrue(g.disabledTools.isEmpty()); assertEquals("gateway", g.id)
        }
        assertEquals("gateway:claude-1", GatewayGrant.from(JSONObject(withGrant("ping", JSONObject().put("agent", "claude-1")))).id)
        assertEquals("gateway", GatewayGrant.from(JSONObject(withGrant("ping", JSONObject().put("agent", "../../x")))).id)
    }

    @Test fun safeModeServesOnlyWhileStoneIsOnScreen() {
        val state = com.zirakusa.stone.nativeapp.automation.provider.ProviderState
        state.load(com.zirakusa.stone.nativeapp.data.persistence.StoneSettingsStore(RuntimeEnvironment.getApplication()))
        state.report("active", "safe", 0); state.visible = false
        assertEquals("SAFE_MODE_STONE_CLOSED", ProviderBridge.switchRefusal())
        state.visible = true
        assertNull(ProviderBridge.switchRefusal())
        state.report("active", "background", 0); state.visible = false
        assertNull(ProviderBridge.switchRefusal())                       // background mode: Stone closed is fine
    }

    @Test fun theServiceIsProtectedAndAnswersOnlyItsAction() {
        val context = RuntimeEnvironment.getApplication()
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SERVICES).services!!
            .single { it.name.endsWith("ProviderService") }
        assertTrue(info.exported)
        assertEquals("com.zirakusa.gatewayhub.permission.LINK", info.permission)
        val service = Robolectric.setupService(ProviderService::class.java)
        assertNull(service.onBind(Intent("something.else")))
        assertNotNull(service.onBind(Intent(ProviderBridge.ACTION)))
    }
}
