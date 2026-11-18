package com.zirakusa.stone.nativeapp

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.zirakusa.stone.nativeapp.feature.settings.api.ApiModeSettings
import com.zirakusa.stone.nativeapp.ui.theme.StonePalette
import com.zirakusa.stone.nativeapp.ui.theme.StoneTheme
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Screenshots of Ajustes › Gateway Hub and nearby settings. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "es-w411dp-h2400dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ApiModeScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @After fun reset() { com.zirakusa.stone.nativeapp.automation.provider.Packages.clear() }

    /** Ajustes › Gateway Hub: the link Gateway reports, packages (one waiting for approval) and the activity. */
    @Test fun gatewayHubPage() {
        val state = com.zirakusa.stone.nativeapp.automation.provider.ProviderState
        state.load(com.zirakusa.stone.nativeapp.data.persistence.StoneSettingsStore(org.robolectric.RuntimeEnvironment.getApplication()))
        state.allowAgain()
        val keys = java.security.KeyPairGenerator.getInstance("EC").apply { initialize(java.security.spec.ECGenParameterSpec("secp256r1")) }.generateKeyPair()
        state.link("env_screenshot0001", java.util.Base64.getEncoder().encodeToString(keys.public.encoded))
        state.report("active", "safe", System.currentTimeMillis())
        val now = System.currentTimeMillis() / 1000
        fun offer(id: String, label: String, agent: String, acts: List<String>) {
            val bytes = org.json.JSONObject().put("v", 1).put("id", id).put("env", "env_screenshot0001").put("aud", state.audience(BuildConfig.APPLICATION_ID))
                .put("agent", agent).put("label", label).put("acts", org.json.JSONArray(acts)).put("tools", org.json.JSONArray()).put("nbf", now - 60).put("exp", now + 30 * 86_400).toString().toByteArray()
            val sig = java.security.Signature.getInstance("SHA256withECDSA").run { initSign(keys.private); update(bytes); sign() }
            com.zirakusa.stone.nativeapp.automation.provider.Packages.install(org.json.JSONObject().put("pkg", java.util.Base64.getUrlEncoder().encodeToString(bytes))
                .put("sig", java.util.Base64.getUrlEncoder().encodeToString(sig)), BuildConfig.APPLICATION_ID)
        }
        offer("pkg_screenread01", "Lectura", "*", listOf("leer"))
        offer("pkg_screentags01", "Claude · ordenar etiquetas", "a1b2c3d4e5f6", listOf("revisar", "modificar", "revertir"))
        compose.setContent { StoneTheme { Surface(color = StonePalette.Surface) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) { ApiModeSettings() }
        } } }
        onView(isRoot()).captureRoboImage("build/screenshots/stone-gateway-hub.png")
    }

    @Test fun audioLevels() {
        compose.setContent { StoneTheme { Surface(color = StonePalette.Surface) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
                com.zirakusa.stone.nativeapp.feature.settings.AudioLevelSettings(null, null)
            }
        } } }
        onView(isRoot()).captureRoboImage("build/screenshots/stone-audio-levels.png")
    }
}
