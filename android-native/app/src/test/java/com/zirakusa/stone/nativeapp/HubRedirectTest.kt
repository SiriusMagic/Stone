package com.zirakusa.stone.nativeapp

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.zirakusa.stone.nativeapp.automation.ApiMode
import com.zirakusa.stone.nativeapp.automation.HubLink
import com.zirakusa.stone.nativeapp.automation.auth.AccessMode
import com.zirakusa.stone.nativeapp.automation.auth.ApiToken
import com.zirakusa.stone.nativeapp.automation.auth.ApiTokens
import com.zirakusa.stone.nativeapp.automation.auth.TokenKind
import com.zirakusa.stone.nativeapp.automation.server.McpServer
import com.zirakusa.stone.nativeapp.data.persistence.SecretBox
import com.zirakusa.stone.nativeapp.data.persistence.StoneSettingsStore
import com.zirakusa.stone.nativeapp.feature.settings.McpByArka
import com.zirakusa.stone.nativeapp.ui.theme.StonePalette
import com.zirakusa.stone.nativeapp.ui.theme.StoneTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Arka runs Stone's Modo MCP: the public link on and off, the status, Lucía's tools on the phone, and the Ajustes page. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "es-w411dp-h2400dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HubRedirectTest {
    @get:Rule val compose = createComposeRule()

    @After fun reset() {
        ApiMode.enable(false)
        ApiMode.allowLocal(false)
        if (ApiMode.remote) ApiMode.turnRemote(false)
        McpServer.stop(); ApiMode.relayUrl = ""; ApiTokens.replaceForTests(emptyList())
    }

    @Test fun kindAndUseSurviveSaving() {
        val token = ApiToken("a", "Arka", "stn_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", AccessMode.FULL, 1, kind = TokenKind.REDIRECT, usage = "Arka")
        val back = ApiToken.parse(ApiToken.serialize(listOf(token))).single()
        assertEquals(TokenKind.REDIRECT, back.kind); assertEquals("Arka", back.usage)
        // Tokens saved by older versions have no type: they are LLM tokens.
        assertEquals(TokenKind.LLM, ApiToken.parse("""[{"id":"x","secret":"s","mode":"write"}]""").single().kind)
    }

    /** Only the old hub token survives an update (the hub's link keeps working); the user's own tokens go. */
    @Test fun onlyTheHubTokenIsKept() {
        val store = StoneSettingsStore(RuntimeEnvironment.getApplication())
        val hub = ApiToken("h", "Gateway Hub", "stn_" + "h".repeat(40), AccessMode.FULL, 1, kind = TokenKind.REDIRECT)
        val own = ApiToken("o", "Claude", "stn_" + "o".repeat(40), AccessMode.READ, 1, kind = TokenKind.LLM)
        store.setLook("api.tokens", SecretBox.seal(ApiToken.serialize(listOf(own, hub))))
        ApiTokens.load(store)
        assertEquals(listOf(hub.secret), ApiTokens.tokens.map { it.secret })
        assertEquals(hub.secret, ApiTokens.arka().secret)
    }

    @Test fun arkaTurnsThePublicLinkOnAndOff() {
        ApiMode.relayUrl = "https://stone-relay.example.workers.dev"
        assertFalse(ApiMode.remote); assertTrue(ApiTokens.served().isEmpty())
        assertEquals("NOT_SAME_DEVELOPER", HubLink.connect(false).getString(HubLink.KEY_ERROR))
        assertEquals("REDIRECT_OFF", HubLink.answer(true).getString(HubLink.KEY_ERROR))
        val answer = HubLink.connect(true)
        assertTrue(ApiMode.remote); assertEquals(ApiMode.hubLink(), answer.getString(HubLink.KEY_LINK))
        val arka = ApiTokens.arka()
        assertEquals(AccessMode.FULL, arka.mode); assertEquals(listOf(arka), ApiTokens.served())
        assertTrue(ApiMode.hubLink().endsWith("?token=${arka.secret}"))
        assertTrue(HubLink.status(true).getBoolean("remote"))
        assertEquals("NOT_SAME_DEVELOPER", HubLink.disconnect(false).getString(HubLink.KEY_ERROR))
        assertFalse(HubLink.disconnect(true).getBoolean("remote"))
        assertFalse(ApiMode.remote); assertTrue(ApiTokens.served().isEmpty())
        assertNull(HubLink.status(true).getString(HubLink.KEY_ERROR))
    }

    /** Lucía runs Stone's tools on the phone, with or without the public link. */
    @Test fun luciaRunsStoneTools() {
        val list = """{"jsonrpc":"2.0","id":3,"method":"tools/list"}"""
        assertEquals("NOT_SAME_DEVELOPER", HubLink.tool(false, list).getString(HubLink.KEY_ERROR))
        assertEquals("BAD_REQUEST", HubLink.tool(true, "").getString(HubLink.KEY_ERROR))
        assertEquals("LOCAL_DISABLED", HubLink.tool(true, list).getString(HubLink.KEY_ERROR))
        ApiMode.allowLocal(true)
        val answer = org.json.JSONObject(HubLink.tool(true, list).getString("response")!!)
        assertEquals(3, answer.getInt("id"))
        assertTrue(answer.getJSONObject("result").getJSONArray("tools").toString().contains("player_set_shuffle"))
    }

    @Test fun trustedReadProbeKeepsTheRequestId() {
        val trace = "7ff7419e-069c-4450-be51-305779a066f4"
        val request = """{"jsonrpc":"2.0","id":"$trace","method":"ping"}"""
        ApiMode.turnRemote(false)
        ApiMode.allowLocal(true)
        val answer = org.json.JSONObject(HubLink.tool(true, request).getString("response")!!)
        assertEquals(trace, answer.getString("id"))
        val hosted = com.zirakusa.stone.nativeapp.automation.server.McpTrace.last
        assertEquals(trace, hosted?.id)
        assertEquals("OK", hosted?.code)
    }

    @Test fun redirectDefaultsOnAndPreservesAnExplicitChoice() {
        ApiMode.relayUrl = ""
        val store = StoneSettingsStore(RuntimeEnvironment.getApplication())
        store.setLook("api.hubRedirect", "")
        ApiMode.load(store)
        assertTrue(ApiMode.remote)
        assertFalse(ApiMode.enabled)
        assertFalse(ApiMode.localMcp)
        assertTrue(ApiMode.active)
        ApiMode.turnRemote(false)
        ApiMode.allowLocal(true)
        ApiMode.load(store)
        assertFalse(ApiMode.remote)
        assertFalse(ApiMode.active)
        assertTrue(ApiMode.localMcp)
    }

    @Test fun settingsRestoreIndependentControls() {
        compose.setContent { StoneTheme { Surface(color = StonePalette.Surface) {
            Column(Modifier.padding(16.dp)) { McpByArka() }
        } } }
        compose.waitForIdle()
        compose.onNodeWithText("Abrir Arka").assertExists()
        compose.onNodeWithText("Activar MCP").assertExists()
        compose.onNodeWithText("Permitir MCP local").assertExists()
        compose.onNodeWithText("Redireccionar a Hub").assertExists()
        assertTrue(compose.onAllNodesWithText("Crear token").fetchSemanticsNodes().isEmpty())
        assertTrue(compose.onAllNodesWithText("Analítica").fetchSemanticsNodes().isEmpty())
        onView(isRoot()).captureRoboImage("build/screenshots/stone-api-by-arka.png")
    }
}
