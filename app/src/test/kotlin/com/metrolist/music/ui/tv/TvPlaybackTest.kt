package com.metrolist.music.ui.tv

import android.view.KeyEvent
import org.junit.Assert.*
import org.junit.Test

class TvPlaybackTest {
    @Test fun remoteMediaKeysMapToTheSharedPlayerButDpadCenterStaysWithFocusedButton() {
        assertEquals(TvMediaAction.TOGGLE, tvMediaAction(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
        assertEquals(TvMediaAction.NEXT, tvMediaAction(KeyEvent.KEYCODE_MEDIA_NEXT))
        assertEquals(TvMediaAction.PREVIOUS, tvMediaAction(KeyEvent.KEYCODE_MEDIA_PREVIOUS))
        assertEquals(TvMediaAction.PLAY, tvMediaAction(KeyEvent.KEYCODE_MEDIA_PLAY))
        assertEquals(TvMediaAction.PAUSE, tvMediaAction(KeyEvent.KEYCODE_MEDIA_PAUSE))
        assertEquals(TvMediaAction.STOP, tvMediaAction(KeyEvent.KEYCODE_MEDIA_STOP))
        assertNull(tvMediaAction(KeyEvent.KEYCODE_DPAD_CENTER))
        assertNull(tvMediaAction(KeyEvent.KEYCODE_BACK))
    }
    @Test fun seekingCannotLeaveTheCurrentTrack() {
        assertEquals(0L, tvSeekPosition(5000, 120000, -10000))
        assertEquals(120000L, tvSeekPosition(119000, 120000, 10000))
        assertEquals(25000L, tvSeekPosition(15000, 120000, 10000))
    }
    @Test fun anUnknownDurationDoesNotInventASeekableRange() {
        assertEquals(1000L, tvSeekPosition(1000, -9223372036854775807L, 10000))
        assertEquals(0L, tvSeekPosition(-100, 0, -10000))
    }
}
