package com.zirakusa.stone.core

import kotlin.test.*

class HomeLayoutTest {
    @Test fun `rotation respects both presentation preferences without changing navigation state`() {
        for (mode in LandscapeLayoutMode.entries) {
            assertTrue(homeLayout(411f, false, true, false, false, mode).fullPlayer)
            assertTrue(homeLayout(891f, true, false, false, false, mode).dockedPlayer)
            val rotated = homeLayout(891f, true, true, false, false, mode)
            assertEquals(mode == LandscapeLayoutMode.LIBRARY_ONLY, rotated.fullPlayer)
            assertEquals(mode == LandscapeLayoutMode.ALWAYS, rotated.dockedPlayer)
        }
    }

    @Test fun `videos and pip keep a single dedicated player`() {
        assertTrue(homeLayout(891f, true, true, true, false, LandscapeLayoutMode.ALWAYS).fullPlayer)
        assertTrue(homeLayout(891f, true, false, true, true, LandscapeLayoutMode.ALWAYS).fullPlayer)
        assertTrue(homeLayout(891f, true, false, true, false, LandscapeLayoutMode.ALWAYS).dockedPlayer)
    }

    @Test fun `compact windows have reachable library and mini player`() {
        val narrow = homeLayout(560f, true, false, false, false, LandscapeLayoutMode.ALWAYS)
        assertEquals(64f, narrow.sidebarWidthDp)
        assertFalse(narrow.fullPlayer)
        assertFalse(narrow.dockedPlayer)
        assertTrue(narrow.showMiniPlayer)
        assertTrue(homeLayout(560f, true, true, false, false, LandscapeLayoutMode.ALWAYS).fullPlayer)
    }

    @Test fun `pane sizes respect available width and minimum readable sizes`() {
        for (width in listOf(600f, 640f, 720f, 891f, 1199f, 1200f, 1600f)) {
            val layout = homeLayout(width, true, false, false, false, LandscapeLayoutMode.ALWAYS)
            assertTrue(layout.playerWidthDp >= 240f)
            assertTrue(width - layout.sidebarWidthDp - 16f - layout.playerWidthDp >= 280f)
            assertFalse(layout.showMiniPlayer)
        }
        assertEquals(160f, homeLayout(1280f, true, false, false, false, LandscapeLayoutMode.ALWAYS).sidebarWidthDp)
    }

    @Test fun `missing and unknown settings use the default`() {
        assertEquals(LandscapeLayoutMode.ALWAYS, LandscapeLayoutMode.fromKey(""))
        assertEquals(LandscapeLayoutMode.ALWAYS, LandscapeLayoutMode.fromKey("future_value"))
        assertEquals(LandscapeLayoutMode.LIBRARY_ONLY, LandscapeLayoutMode.fromKey("library_only"))
    }
}
