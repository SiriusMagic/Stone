package com.zirakusa.stone.core

import kotlin.test.*

class LibraryTest {
    @Test fun `lrc supports offsets fractions and repeated timestamps`() {
        val parsed = LyricsParser.parse("[offset:100]\n[00:01.25][00:02.500] Hola\nSin tiempo")
        assertEquals(listOf("Hola", "Sin tiempo"), parsed.plain)
        assertEquals(listOf(1350L, 2600L), parsed.synced.map { it.timeMs })
        assertEquals(0, LyricsParser.activeIndex(parsed.synced, 2000))
    }
    @Test fun `lrc export keeps sorted millisecond timestamps`() {
        val value = LyricsParser.toLrc(listOf(LyricLine(2_500, "Dos"), LyricLine(1_250, "Uno")))
        assertEquals("[00:01.250]Uno\n[00:02.500]Dos", value)
    }
    @Test fun `lrc text stored as plain lyrics is promoted to synchronized lines`() {
        val song = Song("content://track/lrc", "Sol", "Ana", "Mar", "Stone", 200_000, 500, 123,
            lyrics = listOf("[03:06.26]Oh, necesito un amor, ahora mi corazón", "lo está pensando"))
        val document = song.lyricsDocument()
        assertEquals(2, document.plain.size)
        assertEquals(1, document.synced.size)
        assertEquals(186_260L, document.synced.single().timeMs)
        assertEquals("Oh, necesito un amor, ahora mi corazón", document.synced.single().text)
    }

    @Test fun `plain lyrics get zero timestamps while timed lyrics stay unchanged`() {
        val plain = LyricsParser.parse("Primera línea\nSegunda línea")
        assertEquals(listOf(0L, 0L), plain.synced.map { it.timeMs })
        val timed = LyricsParser.parse("[00:01.250] Conservada")
        assertEquals(listOf(1_250L), timed.synced.map { it.timeMs })
    }
    @Test fun `rapid resumes and manual requests cannot overlap`() {
        val policy = RefreshPolicy(100)
        assertTrue(policy.tryStart(false, 1000))
        assertFalse(policy.tryStart(true, 1001))
        policy.finish(true, 1002)
        assertFalse(policy.tryStart(false, 1101))
        assertTrue(policy.tryStart(false, 1102))
    }

    @Test fun `manual refresh bypasses cooldown but failures do not delay retries`() {
        val policy = RefreshPolicy(100)
        assertTrue(policy.tryStart(false, 0))
        policy.finish(true, 0)
        assertTrue(policy.tryStart(true, 1))
        policy.finish(false, 2)
        assertTrue(policy.tryStart(false, 100))
        policy.finish(false, 101)
        assertTrue(policy.tryStart(false, 102))
    }

    @Test fun `new process and clock rollback permit refresh`() {
        val policy = RefreshPolicy()
        assertTrue(policy.tryStart(false, 100))
        policy.finish(true, 100)
        assertTrue(policy.tryStart(false, 50))
        assertTrue(RefreshPolicy().tryStart(false, 101))
    }

    private val song = Song("content://track/1", "Sol", "Ana", "Mar", "Stone", 1000, 500, 123)

    @Test fun `reuse metadata only with known unchanged file fingerprint`() {
        assertTrue(song.canReuse(500, 123))
        assertFalse(song.canReuse(501, 123))
        assertFalse(song.canReuse(500, 124))
        assertFalse(song.canReuse(500, 0))
        assertFalse(song.canReuse(0, 123))
    }

    @Test fun `search preserves identity and matches all library fields`() {
        assertTrue(song.matches(" ANA "))
        assertTrue(song.matches("mar"))
        assertTrue(song.matches("stone"))
        assertFalse(song.matches("luna"))
        assertEquals("local:content://track/1", song.id)
    }
}
