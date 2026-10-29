package com.metrolist.music.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CanvasVideoLayoutTest {
    @Test
    fun brightnessCanShowUndimmedVideoAndClampsInvalidPreferences() {
        assertEquals(0f, canvasDimAlpha(1f), 0.001f)
        assertEquals(0.85f, canvasDimAlpha(-1f), 0.001f)
        assertEquals(0f, canvasDimAlpha(2f), 0.001f)
        assertEquals(0.35f, canvasDimAlpha(Float.NaN), 0.001f)
    }

    @Test
    fun transientUnknownDimensionsAtLoopBoundaryKeepLastAspectRatio() {
        val valid = androidx.media3.common.VideoSize(1920, 1080)
        val stable = stableCanvasVideoSize(valid, androidx.media3.common.VideoSize.UNKNOWN)
        assertEquals(valid, stable)
        assertEquals(canvasFrameSize(360, 800, 1920f, 1080f),
            canvasFrameSize(360, 800, stable.width.toFloat(), stable.height.toFloat()))
        val portrait = androidx.media3.common.VideoSize(1080, 1920)
        assertEquals(portrait, stableCanvasVideoSize(stable, portrait))
    }

    @Test
    fun invalidGeometryCannotCreateAnOversizedTexture() {
        assertEquals(360 to 800, canvasFrameSize(360, 800, Float.NaN, 1080f))
        assertEquals(360 to 800, canvasFrameSize(360, 800, Float.POSITIVE_INFINITY, 1080f))
        assertEquals(canvasFrameSize(360, 800, 1920f, 1080f), canvasFrameSize(360, 800, 1920f, 1080f, Float.NaN))
    }

    @Test
    fun fillsPortraitLandscapeAndSquareScreensWithoutStretchingVideo() {
        for ((screenWidth, screenHeight) in listOf(360 to 800, 800 to 360, 600 to 600)) {
            for ((videoWidth, videoHeight) in listOf(1920f to 1080f, 1080f to 1920f, 1000f to 1000f)) {
                val (width, height) = canvasFrameSize(screenWidth, screenHeight, videoWidth, videoHeight)
                assertTrue(width >= screenWidth && height >= screenHeight)
                assertEquals(videoWidth / videoHeight, width.toFloat() / height, 0.005f)
            }
        }
    }

    @Test
    fun zoomRemovesEncodedCinemaBarsAndCanBeReduced() {
        val (width, height) = canvasFrameSize(360, 800, 1920f, 1080f)
        val cinemaContentHeight = height * (1920f / 2.39f) / 1080f
        assertTrue(cinemaContentHeight >= 800)
        val reduced = canvasFrameSize(360, 800, 1920f, 1080f, 1f)
        assertEquals(800, reduced.second)
        assertTrue(width > reduced.first)
        assertEquals(360 to 800, canvasFrameSize(360, 800, 0f, 0f))
    }
}
