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
import com.zirakusa.stone.nativeapp.feature.library.components.FeedbackDetail
import com.zirakusa.stone.nativeapp.feature.library.components.LayoutSelector
import com.zirakusa.stone.nativeapp.feature.library.components.MascotPicker
import com.zirakusa.stone.nativeapp.feature.library.components.VideoLibraryLayout
import com.zirakusa.stone.nativeapp.feature.onboarding.OnboardingScreen
import com.zirakusa.stone.nativeapp.ui.theme.LauncherIcon
import com.zirakusa.stone.nativeapp.ui.theme.StonePalette
import com.zirakusa.stone.nativeapp.ui.theme.StoneTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Screenshots of the welcome, feedback form, mascot picker and icon-only layout switch. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "es-w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WelcomeAndFeedbackScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @Test fun welcomeLanguageStep() {
        compose.setContent { StoneTheme { OnboardingScreen("es", onLanguage = {}, onFinished = {}) } }
        onView(isRoot()).captureRoboImage("build/screenshots/stone-welcome-language.png")
    }

    @Test fun feedbackForm() {
        compose.setContent { StoneTheme { Surface(color = StonePalette.Surface) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp)) { FeedbackDetail(true) {} }
        } } }
        onView(isRoot()).captureRoboImage("build/screenshots/stone-feedback.png")
    }

    @Test fun mascotPickerAndLayoutSwitch() {
        compose.setContent { StoneTheme { Surface(color = StonePalette.Background) {
            Column(Modifier.padding(24.dp)) {
                LayoutSelector(VideoLibraryLayout.COMPACT, {})
                MascotPicker(LauncherIcon.PANDA) {}
            }
        } } }
        onView(isRoot()).captureRoboImage("build/screenshots/stone-mascots-layout.png")
    }
}
