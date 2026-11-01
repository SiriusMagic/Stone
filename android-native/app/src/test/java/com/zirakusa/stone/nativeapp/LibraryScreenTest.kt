package com.zirakusa.stone.nativeapp

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
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

    @Test fun emptyLibraryMatchesStoneAndOffersRealFolderActions() {
        var folderRequests = 0
        compose.setContent {
            StoneTheme { Surface(color = StonePalette.Background) { LibraryScreen(LibraryState(), true, { folderRequests++ }, {}, { _, _ -> }, Modifier.fillMaxSize()) } }
        }
        compose.onNodeWithText("Canciones").assertIsSelected()
        compose.onNodeWithContentDescription("Buscar canciones, artistas, álbumes…").assertDoesNotExist()
        compose.onNodeWithText("No hay canciones todavía").assertIsDisplayed()
        onView(isRoot()).captureRoboImage("build/screenshots/stone-home-empty.png")
        compose.onNodeWithText("Elegir carpeta Stone").performClick()
        compose.onNodeWithText("Escanear carpetas").performClick()
        assertEquals(2, folderRequests)
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
        compose.onNodeWithContentDescription("Buscar").performClick()
        compose.onNodeWithContentDescription("Buscar canciones, artistas, álbumes…").performTextInput("Mr.Kitty")
        compose.onNodeWithText("Courtesy Call").assertDoesNotExist()
        compose.onNodeWithText("After Dark").performClick()
        assertEquals(listOf(songs.first()), queue)
        compose.onNodeWithContentDescription("Buscar").performClick()
        compose.onNodeWithText("Courtesy Call").assertIsDisplayed()
    }

    @Test fun pendingDownloadDoesNotPretendToDownload() {
        compose.setContent { StoneTheme { Surface(color = StonePalette.Background) {
            LibraryScreen(LibraryState(), true, {}, {}, { _, _ -> }, Modifier.fillMaxSize())
        } } }
        compose.onNodeWithContentDescription("Descargas").performClick()
        compose.onNodeWithText("Las descargas todavía no están disponibles en esta versión de prueba.").assertIsDisplayed()
        compose.onNodeWithText("Cerrar").performClick()
        compose.onNodeWithText("No hay canciones todavía").assertIsDisplayed()
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
        compose.onNodeWithContentDescription("Reproducir").performClick()
        assertEquals(1, toggles)
        assertEquals(0, opens)
        compose.onNodeWithContentDescription("A continuación").performClick()
        assertEquals(1, opens)
    }

    @Test @Config(qualifiers = "w360dp-h780dp-xhdpi")
    fun compactPhoneKeepsHeaderActionsAccessible() {
        compose.setContent { StoneTheme { Surface(color = StonePalette.Background) {
            LibraryScreen(LibraryState(), true, {}, {}, { _, _ -> }, Modifier.fillMaxSize())
        } } }
        compose.onNodeWithContentDescription("Ajustes").assertIsDisplayed().performClick()
        compose.onNodeWithText("Los demás ajustes de Stone aún están pendientes en esta versión de prueba.").assertIsDisplayed()
    }
}
