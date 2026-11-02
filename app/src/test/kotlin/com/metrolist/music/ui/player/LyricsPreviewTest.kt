package com.metrolist.music.ui.player

import org.junit.Assert.*
import org.junit.Test

class LyricsPreviewTest {
    @Test fun coverUsesAvailableHeightWithoutSubtractingHorizontalMarginsTwice() {
        val method = Class.forName("com.metrolist.music.ui.player.ThumbnailKt").declaredMethods
            .single { it.name.startsWith("calculateThumbnailDimensions-") && it.parameterCount == 5 }
        method.isAccessible = true
        val size = method.invoke(null, 390f, 326f, 32f, 8f, false) as ThumbnailDimensions
        assertEquals(326f, size.thumbnailSize.value, .1f)
    }

    @Test fun portraitCoverFitsReducedLyricsPane() {
        val method = Class.forName("com.metrolist.music.ui.player.ThumbnailKt").declaredMethods
            .single { it.name.startsWith("calculateThumbnailDimensions-") && it.parameterCount == 5 }
        method.isAccessible = true
        val size = method.invoke(null, 390f, 280f, 32f, 8f, false) as ThumbnailDimensions
        org.junit.Assert.assertTrue("Square cover must fit the available height", size.thumbnailSize.value <= 280f)
    }

    @Test fun coverLineFollowsPlaybackOffsetAndBackwardSeek() {
        val raw = "[00:01.00]First\n[00:03.00]Second\n[00:05.00]Third"
        assertNull(coverLyricsText(raw, 0, 0))
        assertEquals("Second", coverLyricsText(raw, 3500, 0))
        assertEquals("First", coverLyricsText(raw, 1500, 0))
        assertEquals("Second", coverLyricsText(raw, 2500, 500))
        assertNull(coverLyricsText("First\nSecond", 3500, 0))
        assertNull(coverLyricsText(null, 3500, 0))
    }

    @Test fun previewIncludesPreviousLineAndHighlightsCurrentLineAfterSeeking() {
        val raw = "[00:01.00]First\n[00:03.00]Second\n[00:05.00]Third"
        val current = lyricsPreviewWindow(raw, 3500)
        assertEquals(listOf("First", "Second", "Third"), current.lines)
        assertEquals(1, current.activeLine)
        assertEquals(1, lyricsPreviewWindow(raw, 5500).startIndex)
        assertEquals(0, lyricsPreviewWindow(raw, 1500).startIndex)
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
