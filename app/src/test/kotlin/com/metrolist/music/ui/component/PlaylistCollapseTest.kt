package com.metrolist.music.ui.component

import org.junit.Assert.*
import org.junit.Test

class PlaylistCollapseTest {
    @Test fun titleTransfersContinuouslyAndReversesWhenScrollingBack() {
        val positions = listOf(180f, 108f, 90f, 72f, 0f)
        val frames = positions.map { playlistCollapseFrame(it, 240f, 96f, 36f, 58f) }
        assertEquals(0f, frames.first().titleAlpha, 0.001f)
        assertEquals(0.5f, frames[2].titleAlpha, 0.001f)
        assertEquals(1f, frames.last().titleAlpha, 0.001f)
        assertTrue(frames.zipWithNext().all { (a, b) -> a.titleAlpha <= b.titleAlpha })
        positions.reversed().forEachIndexed { i, y ->
            assertEquals(frames.reversed()[i], playlistCollapseFrame(y, 240f, 96f, 36f, 58f))
        }
    }

    @Test fun playButtonDocksWithoutPositionJumpAndStaysAtTheBarEdge() {
        val approaching = playlistCollapseFrame(0f, 68f, 96f, 36f, 58f)
        val arriving = playlistCollapseFrame(0f, 67f, 96f, 36f, 58f)
        val scrolled = playlistCollapseFrame(-900f, -700f, 96f, 36f, 58f)
        assertFalse(approaching.docked)
        assertTrue(arriving.docked)
        assertEquals(67f, arriving.playTop, 0.001f)
        assertEquals(arriving.playTop, scrolled.playTop, 0.001f)
    }

    @Test fun unmeasuredOrEmptyPlaylistNeverShowsFloatingControls() {
        val frame = playlistCollapseFrame(null, null, 96f, 36f, 58f)
        assertEquals(0f, frame.titleAlpha, 0.001f)
        assertFalse(frame.docked)
    }
}
