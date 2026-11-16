package com.zirakusa.stone.nativeapp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box
import com.github.takahirom.roborazzi.captureRoboImage
import com.zirakusa.stone.core.LyricCursor
import com.zirakusa.stone.core.LyricTimeline
import com.zirakusa.stone.core.LyricsParser
import com.zirakusa.stone.nativeapp.feature.player.LyricStrip
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Compact lyric window: vertical slide on a new phrase, jumps on seeks, reset on song change. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "es-rES-w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LyricStripTest {
    @get:Rule val compose = createComposeRule()
    private val lines = LyricTimeline(LyricsParser.parse(
        "[00:05.00]Primera frase\n[00:08.50]Segunda frase\n[00:12.20]Tercera frase\n[00:15.00]Cuarta frase")).lines

    private var song by mutableStateOf("a")
    private var cursor by mutableStateOf(LyricCursor(0))

    private fun show() = compose.setContent {
        Box(Modifier.width(260.dp).height(40.dp).clipToBounds().background(Color(0xFF242633)).testTag("window")) {
            LyricStrip(song, lines, cursor)
        }
    }
    private fun advance(ms: Long) {
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        compose.mainClock.advanceTimeBy(ms)
        shadowOf(android.os.Looper.getMainLooper()).idle()
        compose.waitForIdle()
    }
    private fun top(text: String) = compose.onNodeWithText(text).fetchSemanticsNode().positionInRoot.y
    private fun windowTop() = compose.onNodeWithTag("window").fetchSemanticsNode().positionInRoot.y

    @Test fun `next phrase rises from below while the current one leaves upwards`() {
        show()
        compose.onNodeWithText("Primera frase").assertExists()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { cursor = LyricCursor(1) }
        advance(96)
        val leaving = top("Primera frase"); val entering = top("Segunda frase")
        assertTrue("old phrase moves up", leaving < windowTop())
        assertTrue("new phrase still below its place", entering > windowTop())
        compose.onNodeWithTag("window").captureRoboImage("build/screenshots/lyrics-strip-sliding.png")
        advance(700)
        compose.onNodeWithTag("window").captureRoboImage("build/screenshots/lyrics-strip-settled.png")
        val settled = top("Segunda frase")
        advance(500)
        assertEquals("animation finished in ~650 ms", settled, top("Segunda frase"), .5f)
    }

    @Test fun `seeking far and changing song jump straight to the right line`() {
        show()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { cursor = LyricCursor(3) }
        advance(32)
        compose.onNodeWithText("Cuarta frase").assertExists()
        compose.onNodeWithText("Primera frase").assertDoesNotExist()
        val placed = top("Cuarta frase")
        compose.runOnIdle { song = "b"; cursor = LyricCursor(1) }
        advance(32)
        assertEquals(placed, top("Segunda frase"), .5f)
    }
}
