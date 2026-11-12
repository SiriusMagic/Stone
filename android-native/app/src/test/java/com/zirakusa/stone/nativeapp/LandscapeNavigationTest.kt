package com.zirakusa.stone.nativeapp

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.test.core.app.ApplicationProvider
import com.zirakusa.stone.core.*
import com.zirakusa.stone.nativeapp.feature.library.LibraryScreen
import com.zirakusa.stone.nativeapp.feature.library.LibraryState
import com.zirakusa.stone.nativeapp.feature.library.components.HorizontalLayoutSettings
import com.zirakusa.stone.nativeapp.feature.player.rememberPlayerSheetsState
import com.zirakusa.stone.nativeapp.ui.theme.StoneTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "es-rES-w891dp-h411dp-land-xhdpi")
class LandscapeNavigationTest {
    @get:Rule val compose = createComposeRule()
    private fun label(id: Int) = ApplicationProvider.getApplicationContext<Context>().getString(id)

    @Test fun searchScrollAndTabSurviveRotationAndVisitingFullPlayer() {
        val landscape = mutableStateOf(true)
        val showingLibrary = mutableStateOf(true)
        var playbackCommands = 0
        compose.setContent {
            val screens = rememberSaveableStateHolder()
            StoneTheme { if (showingLibrary.value) screens.SaveableStateProvider("library") {
                LibraryScreen(LibraryState(landscapeSongs, hasFolder = true), true, {}, {},
                    { _, _ -> playbackCommands++ }, layout = homeLayout(
                        if (landscape.value) 891f else 411f, landscape.value, false, false,
                        false, LandscapeLayoutMode.ALWAYS))
            } }
        }
        compose.onNodeWithContentDescription(label(R.string.search_toggle)).performClick()
        compose.onNodeWithContentDescription(label(R.string.search)).performTextInput("Song")
        compose.onNodeWithTag("library-list").performScrollToIndex(20)
        compose.onNodeWithText("Song 21").assertIsDisplayed()
        compose.runOnIdle { landscape.value = false }
        compose.onNodeWithContentDescription(label(R.string.search)).assertTextContains("Song")
        compose.runOnIdle { landscape.value = true }
        compose.onNodeWithText("Song 21").assertIsDisplayed()
        compose.runOnIdle { showingLibrary.value = false }
        compose.runOnIdle { showingLibrary.value = true }
        compose.onNodeWithText("Song 21").assertIsDisplayed()
        compose.onNodeWithContentDescription(label(R.string.search)).assertTextContains("Song")
        compose.onNodeWithText(label(R.string.folders)).performClick()
        compose.onNodeWithText("Music").performClick()
        compose.runOnIdle { showingLibrary.value = false }
        compose.runOnIdle { landscape.value = false; showingLibrary.value = true }
        compose.onNodeWithText(label(R.string.folders)).assertIsSelected()
        compose.onNodeWithText("Music").assertIsDisplayed()
        assertEquals(0, playbackCommands)
    }

    @Test fun preferenceChangesPresentationImmediatelyWithoutChangingPlayerNavigation() {
        val mode = mutableStateOf(LandscapeLayoutMode.ALWAYS)
        compose.setContent { StoneTheme { HorizontalLayoutSettings(mode.value) { mode.value = it } } }
        compose.onNodeWithText(label(R.string.horizontal_always)).assertIsSelected()
        compose.onNodeWithText(label(R.string.horizontal_library_only)).performClick().assertIsSelected()
        assertTrue(homeLayout(891f, true, true, false, false, mode.value).fullPlayer)
        assertTrue(homeLayout(891f, true, false, false, false, mode.value).dockedPlayer)
        compose.onNodeWithText(label(R.string.horizontal_always)).performClick().assertIsSelected()
        assertTrue(homeLayout(891f, true, true, false, false, mode.value).dockedPlayer)
    }

    @Test fun playerSheetsSurviveSavedStateRestoration() {
        val restoration = StateRestorationTester(compose)
        var sheets: com.zirakusa.stone.nativeapp.feature.player.PlayerSheetsState? = null
        restoration.setContent { sheets = rememberPlayerSheetsState() }
        compose.runOnIdle { sheets!!.queueVisible = true }
        restoration.emulateSavedInstanceStateRestore()
        compose.runOnIdle { assertTrue(sheets!!.queueVisible) }
    }
}
