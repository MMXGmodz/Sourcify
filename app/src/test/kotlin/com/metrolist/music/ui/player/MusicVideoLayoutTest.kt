package com.metrolist.music.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

class MusicVideoLayoutTest {
    @Test fun landscapeVideoFitsCoverWithoutCropping() {
        assertEquals(400 to 225, musicVideoFrameSize(400, 400, 1920f, 1080f))
        assertEquals(225 to 400, musicVideoFrameSize(400, 400, 1080f, 1920f))
        assertEquals(400 to 400, musicVideoFrameSize(400, 400, Float.NaN, 1080f))
    }
}
