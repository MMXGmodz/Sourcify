package com.metrolist.music.ui.player

import android.view.View
import androidx.media3.common.VideoSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class MusicVideoFrameTest {
    private fun frame() = MusicVideoFrame(RuntimeEnvironment.getApplication())

    private fun MusicVideoFrame.measureAndLayout(width: Int, height: Int) {
        measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        layout(0, 0, width, height)
    }

    private fun MusicVideoFrame.assertTexture(width: Int, height: Int, left: Int, top: Int) {
        assertEquals(width, texture.measuredWidth)
        assertEquals(height, texture.measuredHeight)
        assertEquals(left, texture.left)
        assertEquals(top, texture.top)
        assertEquals(left + width, texture.right)
        assertEquals(top + height, texture.bottom)
    }

    @Test fun firstPortraitLayoutMeasuresLandscapeVideoAtItsNaturalAspect() {
        val frame = frame()
        frame.updateSize(VideoSize(1920, 1080))
        frame.measureAndLayout(360, 800)
        frame.assertTexture(360, 202, 0, 299)
        assertFalse("A second layout must not be needed to fit the video", frame.isLayoutRequested)
    }

    @Test fun lateVideoDimensionsRequestAndFitTheNextLayout() {
        val frame = frame()
        frame.measureAndLayout(360, 800)
        frame.assertTexture(360, 800, 0, 0)
        frame.updateSize(VideoSize(1920, 1080))
        assertTrue(frame.isLayoutRequested)
        frame.measureAndLayout(360, 800)
        frame.assertTexture(360, 202, 0, 299)
        frame.updateSize(VideoSize.UNKNOWN)
        frame.measureAndLayout(360, 800)
        frame.assertTexture(360, 202, 0, 299)
    }

    @Test fun repeatedOrientationChangesFitAndCenterTheMeasuredTextureOnEachPass() {
        val frame = frame()
        frame.updateSize(VideoSize(1920, 1080))
        repeat(3) {
            frame.measureAndLayout(360, 800)
            frame.assertTexture(360, 202, 0, 299)
            frame.measureAndLayout(800, 360)
            frame.assertTexture(640, 360, 80, 0)
            frame.measureAndLayout(600, 600)
            frame.assertTexture(600, 337, 0, 131)
        }
        frame.updateSize(VideoSize(1080, 1920))
        frame.measureAndLayout(800, 360)
        frame.assertTexture(202, 360, 299, 0)
    }

    @Test fun nonsquarePixelsUseDisplayAspectAndInvalidGeometryKeepsLastValidSize() {
        val frame = frame()
        frame.updateSize(VideoSize(720, 576, 2f))
        frame.measureAndLayout(360, 800)
        frame.assertTexture(360, 144, 0, 328)
        for (ratio in listOf(Float.NaN, Float.POSITIVE_INFINITY, 0f, -1f)) {
            frame.updateSize(VideoSize(1920, 1080, ratio))
            frame.measureAndLayout(360, 800)
            frame.assertTexture(360, 144, 0, 328)
        }
    }

    @Test fun invalidInitialGeometryUsesTheViewportUntilAValidSizeArrives() {
        val frame = frame()
        frame.updateSize(VideoSize(1920, 1080, Float.NaN))
        frame.measureAndLayout(360, 800)
        frame.assertTexture(360, 800, 0, 0)
        frame.updateSize(VideoSize(1920, 1080))
        frame.measureAndLayout(360, 800)
        frame.assertTexture(360, 202, 0, 299)
    }
}
