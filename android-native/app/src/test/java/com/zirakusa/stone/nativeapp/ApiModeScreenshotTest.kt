package com.zirakusa.stone.nativeapp

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.zirakusa.stone.nativeapp.automation.AccessMode
import com.zirakusa.stone.nativeapp.automation.ApiMode
import com.zirakusa.stone.nativeapp.automation.ApiToken
import com.zirakusa.stone.nativeapp.automation.ApiTokens
import com.zirakusa.stone.nativeapp.automation.RequestStats
import com.zirakusa.stone.nativeapp.feature.library.components.ApiModeSettings
import com.zirakusa.stone.nativeapp.ui.theme.StonePalette
import com.zirakusa.stone.nativeapp.ui.theme.StoneTheme
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Screenshots of every tab of Ajustes › Modo API. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "es-w411dp-h2400dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ApiModeScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @After fun reset() { ApiMode.relayUrl = ""; ApiTokens.replaceForTests(emptyList()); RequestStats.clearForTests() }

    /** About 40 minutes of traffic from two agents, a few failures and one wrong token. */
    private fun traffic() {
        RequestStats.clearForTests()
        val now = System.currentTimeMillis()
        for (minute in 0 until 40) repeat((minute * 7) % 9 + if (minute in 30..33) 12 else 0) { call ->
            val token = if (call % 3 == 0) "b" else "a"
            val kind = if (call == 4) RequestStats.FAILED else if (call == 6) RequestStats.DENIED else RequestStats.OK
            RequestStats.record(token, kind, now - minute * 60_000L)
        }
        RequestStats.record("", RequestStats.REJECTED, now)
    }

    @Test fun everyTab() {
        ApiMode.relayUrl = "https://stone-relay.example.workers.dev"
        ApiTokens.replaceForTests(listOf(
            ApiToken("a", "Claude Principal", "2a9f00000000000000000000000000000000000000d3e7", AccessMode.WRITE, 1_767_000_000_000, 1_767_100_000_000),
            ApiToken("b", "ChatGPT", "7b1c000000000000000000000000000000000000091a0", AccessMode.READ, 1_767_000_000_000)))
        traffic()
        compose.setContent { StoneTheme { Surface(color = StonePalette.Surface) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) { ApiModeSettings() }
        } } }
        onView(isRoot()).captureRoboImage("build/screenshots/stone-api-general.png")
        listOf("Herramientas" to "tools", "Seguridad" to "security", "Enlace" to "link", "Actividad" to "activity").forEach { (tab, file) ->
            compose.onAllNodesWithText(tab).onFirst().performClick()
            compose.waitForIdle()
            onView(isRoot()).captureRoboImage("build/screenshots/stone-api-$file.png")
        }
    }

    @Test fun tokenEditorWithItsOwnAnalytics() {
        traffic()
        ApiTokens.replaceForTests(listOf(ApiToken("a", "ChatGPT", "7b1c000000000000000000000000000000000000091a0",
            AccessMode.FULL, 1_767_000_000_000, disabledTools = setOf("playlist_delete", "metadata_undo"))))
        compose.setContent { StoneTheme { Surface(color = StonePalette.Surface) {
            com.zirakusa.stone.nativeapp.feature.library.components.ApiTokenEditor(ApiTokens.tokens.first()) {}
        } } }
        compose.waitForIdle()
        compose.onAllNodesWithText("playlist").onFirst().performClick()
        compose.waitForIdle()
        // The editor is its own full-screen window: capture that window, not the activity behind it.
        compose.onNode(androidx.compose.ui.test.isDialog()).captureRoboImage("build/screenshots/stone-api-token-editor.png")
    }

    @Test fun audioLevels() {
        compose.setContent { StoneTheme { Surface(color = StonePalette.Surface) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
                com.zirakusa.stone.nativeapp.feature.library.components.AudioLevelSettings(null, null)
            }
        } } }
        onView(isRoot()).captureRoboImage("build/screenshots/stone-audio-levels.png")
    }
}
