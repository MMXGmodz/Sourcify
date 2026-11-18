package com.metrolist.music.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.*
import org.junit.Test

class SongBackdropTest {
    @Test fun grayscaleCoverKeepsNeutralControlsRatherThanAddingBlue() {
        val accent = artworkAccent(Color.Gray, true)
        assertEquals(accent.red, accent.green, 0.001f)
        assertEquals(accent.red, accent.blue, 0.001f)
    }
    @Test fun redCoverKeepsRedAccentsInBothThemes() {
        for (dark in listOf(true, false)) {
            val accent = artworkAccent(Color.Red, dark)
            assertTrue(accent.red > accent.blue)
            assertTrue(accent.red > accent.green)
        }
    }

    @Test
    fun historyRetainsTheCoverHueWithAMutedContinuousBackdrop() {
        for (base in listOf(Color.Black, Color.White)) {
            val normal = songBackdropColors(Color.Red, base)
            val muted = songBackdropColors(Color.Red, base, 0.3f)
            assertEquals(base, muted.last())
            assertTrue(kotlin.math.abs(muted.first().green - base.green) < kotlin.math.abs(normal.first().green - base.green)
                || kotlin.math.abs(muted.first().red - base.red) < kotlin.math.abs(normal.first().red - base.red))
            assertTrue(muted.first().red > muted.first().blue)
        }
    }

    @Test
    fun backdropUsesCoverHueAndFadesIntoOneContinuousBase() {
        val red = songBackdropColors(Color.Red, Color.Black)
        val blue = songBackdropColors(Color.Blue, Color.Black)
        assertTrue(red[0].red > red[0].blue)
        assertTrue(blue[0].blue > blue[0].red)
        assertTrue(red[0].red > red[1].red)
        assertEquals(Color.Black, red.last())
        assertEquals(Color.Black, blue.last())
    }
}
