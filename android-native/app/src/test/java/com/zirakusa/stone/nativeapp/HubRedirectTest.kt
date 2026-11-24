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
import com.zirakusa.stone.nativeapp.feature.settings.api.ApiModeSettings
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
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Token type (LLM / Hub), its use text, and «Redireccionar a Hub». */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "es-w411dp-h2400dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HubRedirectTest {
    @get:Rule val compose = createComposeRule()
    private val own = ApiToken("a", "Claude", "stn_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", AccessMode.READ, 1, kind = TokenKind.HUB,
        usage = "Para el hub de casa")

    @After fun reset() {
        if (ApiMode.hubRedirect) ApiMode.redirectToHub(false)
        McpServer.stop(); ApiMode.relayUrl = ""; ApiTokens.replaceForTests(emptyList())
    }

    @Test fun kindAndUseSurviveSaving() {
        val back = ApiToken.parse(ApiToken.serialize(listOf(own))).single()
        assertEquals(TokenKind.HUB, back.kind)
        assertEquals("Para el hub de casa", back.usage)
        // Tokens saved by older versions have no type: they are LLM tokens.
        assertEquals(TokenKind.LLM, ApiToken.parse("""[{"id":"x","secret":"s","mode":"write"}]""").single().kind)
        assertEquals(ApiToken.USAGE_MAX, ApiTokens.create("x", AccessMode.READ, usage = "a".repeat(500)).usage.length)
    }

    @Test fun redirectServesOnlyTheHubTokenAndGivesBackTheUsersOnes() {
        ApiMode.relayUrl = "https://stone-relay.example.workers.dev"
        ApiTokens.replaceForTests(listOf(own))
        assertFalse(ApiMode.active)
        ApiMode.redirectToHub(true)
        assertTrue("the hub runs MCP without «Activar MCP»", ApiMode.active)
        val hub = ApiTokens.tokens.single { it.kind == TokenKind.REDIRECT }
        assertEquals(AccessMode.FULL, hub.mode)
        assertEquals(listOf(hub), ApiTokens.served(true))
        assertEquals(listOf(own), ApiTokens.listed)
        assertTrue(ApiMode.hubLink().endsWith("?token=${hub.secret}"))
        ApiMode.redirectToHub(false)
        assertEquals(listOf(own), ApiTokens.served(false))
        assertFalse(ApiMode.active)
    }

    @Test fun onlyTheSameDeveloperGetsTheLinkAndOnlyWhenRedirecting() {
        ApiMode.relayUrl = "https://stone-relay.example.workers.dev"
        assertEquals("NOT_SAME_DEVELOPER", HubLink.answer(false).getString(HubLink.KEY_ERROR))
        assertEquals("REDIRECT_OFF", HubLink.answer(true).getString(HubLink.KEY_ERROR))
        ApiMode.redirectToHub(true)
        assertEquals("NOT_SAME_DEVELOPER", HubLink.answer(false).getString(HubLink.KEY_ERROR))
        val answer = HubLink.answer(true)
        assertNull(answer.getString(HubLink.KEY_ERROR))
        assertEquals(ApiMode.hubLink(), answer.getString(HubLink.KEY_LINK))
    }

    /** «Conectar todas» in Gateway Hub: only the same developer turns the redirect on, and gets the link at once. */
    @Test fun connectTurnsTheRedirectOn() {
        ApiMode.relayUrl = "https://stone-relay.example.workers.dev"
        assertEquals("NOT_SAME_DEVELOPER", HubLink.connect(false).getString(HubLink.KEY_ERROR))
        assertFalse(ApiMode.hubRedirect)
        val answer = HubLink.connect(true)
        assertTrue(ApiMode.hubRedirect); assertEquals(ApiMode.hubLink(), answer.getString(HubLink.KEY_LINK))
    }

    /** Lucía runs Stone's tools on the phone, only with «Redireccionar a Hub» on. */
    @Test fun luciaRunsStoneTools() {
        val list = """{"jsonrpc":"2.0","id":3,"method":"tools/list"}"""
        assertEquals("REDIRECT_OFF", HubLink.tool(true, list).getString(HubLink.KEY_ERROR))
        assertEquals("NOT_SAME_DEVELOPER", HubLink.tool(false, list).getString(HubLink.KEY_ERROR))
        ApiMode.redirectToHub(true)
        val answer = org.json.JSONObject(HubLink.tool(true, list).getString("response")!!)
        assertEquals(3, answer.getInt("id"))
        assertTrue(answer.getJSONObject("result").getJSONArray("tools").toString().contains("player_set_shuffle"))
    }

    @Test fun redirectScreenHidesTokensAndAnalytics() {
        ApiMode.relayUrl = "https://stone-relay.example.workers.dev"
        ApiTokens.replaceForTests(listOf(own))
        ApiMode.redirectToHub(true)
        compose.setContent { StoneTheme { Surface(color = StonePalette.Surface) {
            Column(Modifier.padding(16.dp)) { ApiModeSettings() }
        } } }
        compose.waitForIdle()
        compose.onNodeWithText("Ir al Hub").assertExists()
        assertTrue(compose.onAllNodesWithText("Crear token").fetchSemanticsNodes().isEmpty())
        assertTrue(compose.onAllNodesWithText("Analítica").fetchSemanticsNodes().isEmpty())
        assertTrue(compose.onAllNodesWithText("Claude").fetchSemanticsNodes().isEmpty())
        onView(isRoot()).captureRoboImage("build/screenshots/stone-api-hub-redirect.png")
    }

    @Test fun createTokenAsksWhatItConnects() {
        ApiMode.relayUrl = "https://stone-relay.example.workers.dev"
        compose.setContent { StoneTheme { Surface(color = StonePalette.Surface) {
            com.zirakusa.stone.nativeapp.feature.settings.api.ApiTokenEditor(null, { _, _ -> }) {}
        } } }
        compose.waitForIdle()
        compose.onNodeWithText("¿Qué vas a conectar?").assertExists()
        compose.onNodeWithText("Uso del token").assertExists()
        compose.onNode(androidx.compose.ui.test.isDialog()).captureRoboImage("build/screenshots/stone-api-create-kind.png")
    }
}
