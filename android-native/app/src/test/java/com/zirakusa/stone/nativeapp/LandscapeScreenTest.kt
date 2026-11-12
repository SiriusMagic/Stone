package com.zirakusa.stone.nativeapp

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.zirakusa.stone.core.LandscapeLayoutMode
import com.zirakusa.stone.core.homeLayout
import com.zirakusa.stone.nativeapp.data.persistence.StoneSettingsStore
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "es-rES-w891dp-h411dp-land-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LandscapeScreenTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private fun label(id: Int) = context.getString(id)

    private fun checkThreeZones(name: String) {
        val rail = compose.onNodeWithTag("home-sidebar").fetchSemanticsNode().boundsInRoot
        val library = compose.onNodeWithTag("home-library").fetchSemanticsNode().boundsInRoot
        val player = compose.onNodeWithTag("home-player").fetchSemanticsNode().boundsInRoot
        assertTrue(rail.right < library.left && library.right < player.left)
        compose.onAllNodesWithContentDescription(label(R.string.pause)).assertCountEquals(1)
        compose.onNodeWithContentDescription(label(R.string.pause)).assertIsDisplayed()
        compose.onNodeWithContentDescription(label(R.string.settings)).assertIsDisplayed()
        compose.onNodeWithTag("compact-player-title").assertIsDisplayed()
        compose.onNodeWithTag("compact-player-artist").assertIsDisplayed()
        compose.onNodeWithText("Song 02").assertIsDisplayed()
        compose.onRoot().captureRoboImage("build/screenshots/$name.png")
    }

    @Test fun phoneHasThreeZonesAndAllControlsDispatchOnlyTheirAction() {
        val events = mutableListOf<String>()
        compose.setContent { LandscapeTestContent(event = events::add) }
        checkThreeZones("stone-landscape-phone")
        listOf(R.string.pause to "toggle", R.string.previous to "previous", R.string.next to "next",
            R.string.shuffle to "shuffle", R.string.repeat to "repeat", R.string.favorite to "favorite",
            R.string.more_options to "options", R.string.lyrics to "lyrics", R.string.queue to "queue",
            R.string.open_player to "open", R.string.choose_folder to "folder", R.string.settings to "settings"
        ).forEach { (id, event) ->
            events.clear()
            compose.onNodeWithContentDescription(label(id)).performClick()
            assertEquals(listOf(event), events)
        }
        compose.onNodeWithContentDescription(label(R.string.equalizer)).performScrollTo().performClick()
        assertEquals("equalizer", events.last())
    }

    @Test @Config(qualifiers = "es-rES-w640dp-h360dp-land-xhdpi")
    fun smallLandscapePhoneKeepsPlaybackAndNavigationAccessible() {
        compose.setContent { LandscapeTestContent() }
        checkThreeZones("stone-landscape-small")
    }

    @Test @Config(qualifiers = "es-rES-w1280dp-h800dp-land-xhdpi")
    fun tabletShowsRailLabels() {
        compose.setContent { LandscapeTestContent() }
        checkThreeZones("stone-landscape-tablet")
        compose.onNodeWithText(label(R.string.home)).assertIsDisplayed()
        compose.onNodeWithText(label(R.string.equalizer)).assertIsDisplayed()
    }

    @Test @Config(qualifiers = "es-rES-w560dp-h320dp-land-xhdpi")
    fun narrowMultiWindowUsesOnlyOneMiniPlayer() {
        compose.setContent { LandscapeTestContent() }
        compose.onNodeWithTag("home-sidebar").assertIsDisplayed()
        compose.onNodeWithTag("home-player").assertDoesNotExist()
        compose.onAllNodesWithContentDescription(label(R.string.pause)).assertCountEquals(1)
        compose.onNodeWithContentDescription(label(R.string.pause)).assertIsDisplayed()
        compose.onRoot().captureRoboImage("build/screenshots/stone-landscape-multiwindow.png")
    }

    @Test fun fontScalingAndSideInsetsKeepControlsWithinBounds() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.5f)) {
                LandscapeTestContent(layout = homeLayout(795f, true, false, false, false,
                    LandscapeLayoutMode.ALWAYS), inset = 48)
            }
        }
        checkThreeZones("stone-landscape-large-font-insets")
    }

    @Test fun emptyLibraryDoesNotCreatePlaybackControls() {
        compose.setContent { LandscapeTestContent(songs = emptyList()) }
        compose.onNodeWithText(label(R.string.no_songs)).assertIsDisplayed()
        compose.onNodeWithText(label(R.string.choose_song_to_play)).assertIsDisplayed()
        compose.onNodeWithContentDescription(label(R.string.pause)).assertDoesNotExist()
        compose.onRoot().captureRoboImage("build/screenshots/stone-landscape-empty.png")
    }

    @Test fun customizedRailRespectsVisibilityAndOrder() {
        val store = StoneSettingsStore(context)
        store.setShortcutProfile("custom")
        store.setShortcutVisible("scan", false)
        store.setShortcutOrder(listOf("sort", "search"))
        compose.setContent { LandscapeTestContent(store = store) }
        compose.onNodeWithContentDescription("Escanear").assertDoesNotExist()
        compose.onNodeWithContentDescription(label(R.string.sleep_timer)).assertDoesNotExist()
        val sort = compose.onNodeWithContentDescription(label(R.string.sort)).fetchSemanticsNode().boundsInRoot
        val search = compose.onNodeWithContentDescription(label(R.string.search_toggle)).fetchSemanticsNode().boundsInRoot
        assertTrue(sort.top < search.top)
        compose.onNodeWithContentDescription(label(R.string.equalizer)).performScrollTo().assertIsDisplayed()
    }

    @Test fun videoCoverOffersOpeningTheDedicatedVideoPlayer() {
        val events = mutableListOf<String>()
        compose.setContent { LandscapeTestContent(songs = listOf(landscapeSongs[0].copy(isVideo = true)),
            event = events::add) }
        compose.onNodeWithContentDescription(label(R.string.open_video)).performClick()
        assertEquals(listOf("open"), events)
    }
}
