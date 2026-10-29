package com.metrolist.music.ui.player

import org.junit.Assert.*
import org.junit.Test

class LyricsPreviewTest {
    @Test fun previewIncludesPreviousLineAndHighlightsCurrentLineAfterSeeking() {
        val raw = "[00:01.00]First\n[00:03.00]Second\n[00:05.00]Third"
        val current = lyricsPreviewWindow(raw, 3500)
        assertEquals(listOf("First", "Second", "Third"), current.lines)
        assertEquals(1, current.activeLine)
        assertEquals(-1, lyricsPreviewWindow(raw, 0).activeLine)
        assertEquals(0, lyricsPreviewWindow(raw, 1500).activeLine)
    }

    @Test fun tracksCurrentLineBeforeStartDuringPlaybackAndAfterSeekingBack() {
        val times = listOf(1_000L, 3_000L, 5_000L)
        assertEquals(-1, activeLyricsLine(times, 0))
        assertEquals(1, activeLyricsLine(times, 3_500))
        assertEquals(0, activeLyricsLine(times, 1_500))
        assertEquals(2, activeLyricsLine(times, 99_000))
    }

    @Test fun syncedPreviewShowsCurrentLineAndFollowingLinesWithoutTimestampTags() {
        val raw = "[00:01.00]First\n[00:03.00]Second\n[00:05.00]Third"
        assertEquals(listOf("Second", "Third"), lyricsPreviewLines(raw, 3500))
    }
    @Test fun plainLyricsRemainReadableRatherThanLosingAllButLastLine() {
        assertEquals(listOf("First", "Second"), lyricsPreviewLines("First\nSecond", 30_000))
        assertTrue(lyricsPreviewLines(null, 0).isEmpty())
    }
}
