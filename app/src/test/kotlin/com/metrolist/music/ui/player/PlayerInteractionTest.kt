package com.metrolist.music.ui.player

import org.junit.Assert.*
import org.junit.Test

class PlayerInteractionTest {
    @Test fun castFeedbackHoldsTheChosenLineUntilTheRemotePositionArrivesOrTimesOut() {
        assertEquals(10_000L, lyricsReportedPosition(1_000, 10_000, 100))
        assertEquals(10_100L, lyricsReportedPosition(10_100, 10_000, 600))
        assertEquals(1_000L, lyricsReportedPosition(1_000, 10_000, 1600))
        assertEquals(1_000L, lyricsReportedPosition(1_000, null, 100))
    }

    @Test fun lyricSeekingHonorsOffsetAndClampsBeforeStart() {
        assertEquals(9_500L, lyricsSeekPosition(10_000, 500, true, true, false))
        assertEquals(10_500L, lyricsSeekPosition(10_000, -500, true, true, false))
        assertEquals(0L, lyricsSeekPosition(200, 500, true, true, false))
    }
    @Test fun untimedLyricsDisabledClicksAndGuestsCannotSeek() {
        assertNull(lyricsSeekPosition(1_000, 0, false, true, false))
        assertNull(lyricsSeekPosition(1_000, 0, true, false, false))
        assertNull(lyricsSeekPosition(1_000, 0, true, true, true))
        assertNull(lyricsSeekPosition(-1, 0, true, true, false))
    }
    @Test fun compactHeaderEntersGraduallyAndClampsOverscroll() {
        assertEquals(0f, compactHeaderProgress(0f, 500f, 100f), 0f)
        assertEquals(0f, compactHeaderProgress(-20f, 500f, 100f), 0f)
        assertEquals(.5f, compactHeaderProgress(550f, 500f, 100f), .001f)
        assertEquals(1f, compactHeaderProgress(700f, 500f, 100f), 0f)
        assertEquals(0f, compactHeaderProgress(Float.NaN, 500f, 100f), 0f)
    }
}
