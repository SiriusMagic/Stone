package com.zirakusa.stone.nativeapp

import android.content.Context
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.zirakusa.stone.core.Song
import com.zirakusa.stone.nativeapp.feature.library.LibraryScreen
import com.zirakusa.stone.nativeapp.feature.library.LibraryState
import com.zirakusa.stone.nativeapp.feature.player.MiniPlayer
import com.zirakusa.stone.nativeapp.playback.PlaybackState
import com.zirakusa.stone.nativeapp.playback.QueueEntry
import com.zirakusa.stone.nativeapp.ui.theme.StoneTheme
import com.zirakusa.stone.nativeapp.ui.theme.StonePalette
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LibraryScreenTest {
    @get:Rule val compose = createComposeRule()

    private fun text(id: Int): String =
        ApplicationProvider.getApplicationContext<Context>().getString(id)

    @Test fun emptyLibraryMatchesStoneAndOffersRealFolderActions() {
        var folderRequests = 0
        compose.setContent {
            StoneTheme { Surface(color = StonePalette.Background) { LibraryScreen(LibraryState(), true, { folderRequests++ }, {}, { _, _ -> }, Modifier.fillMaxSize()) } }
        }
        compose.onNodeWithText(text(R.string.songs)).assertIsSelected()
        compose.onNodeWithContentDescription(text(R.string.search)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.no_songs)).assertIsDisplayed()
        onView(isRoot()).captureRoboImage("build/screenshots/stone-home-empty.png")
        compose.onNodeWithContentDescription(text(R.string.choose_folder)).performClick()
        compose.onNodeWithText(text(R.string.scan_folders)).assertDoesNotExist()
        assertEquals(1, folderRequests)
    }

    @Test fun videosCanChooseAnotherFolderEvenWhenLibraryAlreadyHasOne() {
        var folderRequests = 0
        compose.setContent { StoneTheme { Surface(color = StonePalette.Background) {
            LibraryScreen(LibraryState(hasFolder = true), true, { folderRequests++ }, {}, { _, _ -> },
                Modifier.fillMaxSize())
        } } }
        compose.onNodeWithText(text(R.string.videos)).performClick()
        compose.onNodeWithContentDescription(text(R.string.choose_video_folder)).performClick()
        compose.onNodeWithText(text(R.string.choose_video_folder)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.scan_video_folders)).assertDoesNotExist()
        assertEquals(1, folderRequests)
    }

    @Test fun searchPreservesPlaybackQueueAndCanBeClosed() {
        val songs = listOf(Song("content://fixture/1", "After Dark", "Mr.Kitty", "Time", "Music", 1000, 1, 1),
            Song("content://fixture/2", "Courtesy Call", "Thousand Foot Krutch", "The End", "Music", 2000, 1, 1))
        var queue = emptyList<Song>()
        compose.setContent {
            StoneTheme { Surface(color = StonePalette.Background) { LibraryScreen(LibraryState(songs, hasFolder = true), true, {}, {},
                { _, tracks -> queue = tracks }, Modifier.fillMaxSize()) } }
        }
        compose.onRoot().captureRoboImage("build/screenshots/stone-home-songs.png")
        compose.onNodeWithContentDescription(text(R.string.search_toggle)).performClick()
        compose.onNodeWithContentDescription(text(R.string.search)).performTextInput("Mr.Kitty")
        compose.onNodeWithText("Courtesy Call").assertDoesNotExist()
        compose.onNodeWithText("After Dark").performClick()
        assertEquals(listOf(songs.first()), queue)
        compose.onNodeWithContentDescription(text(R.string.search_toggle)).performClick()
        compose.onNodeWithText("Courtesy Call").assertIsDisplayed()
    }

    @Test fun sortOffersMostPlayedAndLyrics() {
        compose.setContent { StoneTheme { Surface(color = StonePalette.Background) {
            LibraryScreen(LibraryState(), true, {}, {}, { _, _ -> }, Modifier.fillMaxSize())
        } } }
        compose.onNodeWithContentDescription(text(R.string.sort)).performClick()
        compose.onNodeWithText(text(R.string.sort_most_played)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.sort_lyrics)).assertExists()
    }

    @Test fun miniPlayerToggleDoesNotOpenPlayer() {
        var toggles = 0
        var opens = 0
        val song = Song("content://fixture/1", "After Dark", "Mr.Kitty", "Time", "Music", 1000, 1, 1)
        val player = PlaybackState(ready = true, title = song.title, artist = song.artist,
            queue = listOf(QueueEntry(song.id, song.title, song.artist)), index = 0, currentId = song.id)
        compose.setContent { StoneTheme {
            Scaffold(bottomBar = { MiniPlayer(player, { opens++ }, { toggles++ }) }) { insets ->
                LibraryScreen(LibraryState(listOf(song), hasFolder = true), true, {}, {}, { _, _ -> },
                    Modifier.fillMaxSize().padding(insets), currentId = song.id)
            }
        } }
        compose.onRoot().captureRoboImage("build/screenshots/stone-home-miniplayer.png")
        compose.onNodeWithContentDescription(text(R.string.play)).performClick()
        assertEquals(1, toggles)
        assertEquals(0, opens)
        compose.onAllNodesWithText("After Dark")[1].performClick()
        assertEquals(1, opens)
    }

    @Test @Config(qualifiers = "w360dp-h780dp-xhdpi")
    fun compactPhoneKeepsHeaderActionsAccessible() {
        var settingsRequests = 0
        compose.setContent { StoneTheme { Surface(color = StonePalette.Background) {
            LibraryScreen(LibraryState(), true, {}, {}, { _, _ -> }, Modifier.fillMaxSize(),
                onOpenSettings = { settingsRequests++ })
        } } }
        compose.onNodeWithContentDescription(text(R.string.settings)).assertIsDisplayed().performClick()
        assertEquals(1, settingsRequests)
    }
}
