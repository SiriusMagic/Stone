package com.zirakusa.stone.core

import kotlin.test.*

class LyricTimelineTest {
    private val sample = LyricTimeline(LyricsParser.parse(
        "[ar:Stone]\n[00:05.00]Primera frase\n[00:08.50]Segunda frase\n[00:12.20]Tercera frase"))

    @Test fun `line follows the playback position including seeks backwards and forwards`() {
        assertEquals(-1, sample.cursorAt(0).line)
        assertEquals(0, sample.cursorAt(5_000).line)
        assertEquals(1, sample.cursorAt(8_499 + 1).line)
        assertEquals(2, sample.cursorAt(200_000).line)
        assertEquals(0, sample.cursorAt(6_000).line) // seek back
        assertEquals(1, sample.cursorAt(10_000).line) // seek forward again
    }

    @Test fun `next change is the next timestamp so no fixed timer is needed`() {
        assertEquals(5_000L, sample.nextChangeAfter(0))
        assertEquals(8_500L, sample.nextChangeAfter(5_000))
        assertNull(sample.nextChangeAfter(12_200))
    }

    @Test fun `same position gives an equal cursor so the animation does not rerun`() {
        assertEquals(sample.cursorAt(6_000), sample.cursorAt(7_000))
    }

    @Test fun `duplicated timestamps resolve to the last line and exact copies are removed`() {
        val doc = LyricsParser.parse("[00:01.00]Uno\n[00:01.00]Uno\n[00:01.00]One\n[00:03.00]Dos")
        assertEquals(listOf("Uno", "One", "Dos"), doc.synced.map { it.text })
        assertEquals(1, LyricTimeline(doc).cursorAt(1_500).line)
        assertEquals(3_000L, LyricTimeline(doc).nextChangeAfter(1_000))
    }

    @Test fun `empty malformed and unsynchronized lyrics have no timeline`() {
        assertFalse(LyricTimeline(LyricsParser.parse("")).isTimed)
        assertFalse(LyricTimeline(LyricsParser.parse("[00:xx]roto\n[ti:Nada]")).isTimed)
        val plain = LyricTimeline(LyricsParser.parse("Solo texto\nSin tiempos"))
        assertFalse(plain.isTimed)
        assertEquals(LyricCursor.None, plain.cursorAt(10_000))
    }

    @Test fun `blank timestamps become one gap so the last phrase does not linger`() {
        val doc = LyricsParser.parse("[00:00.00]\n[00:01.00]Hola\n[00:04.00]\n[00:04.50]\n[00:06.00]Adiós")
        assertEquals(listOf("Hola", "", "Adiós"), doc.synced.map { it.text })
        assertEquals(listOf("Hola", "Adiós"), doc.plain)
    }

    @Test fun `enhanced lrc keeps word timings and clean text`() {
        val doc = LyricsParser.parse("[00:10.00]<00:10.00>Hola <00:10.60>mundo <00:11.20>bonito")
        val line = doc.synced.single()
        assertEquals("Hola mundo bonito", line.text)
        assertEquals(listOf(10_000L, 10_600L, 11_200L), line.words.map { it.timeMs })
        val timeline = LyricTimeline(doc)
        assertEquals(LyricCursor(0, 1), timeline.cursorAt(10_700))
        assertEquals(11_200L, timeline.nextChangeAfter(10_700))
        assertEquals("[00:10.000]<00:10.000>Hola <00:10.600>mundo <00:11.200>bonito", LyricsParser.toLrc(doc.synced))
    }
}
