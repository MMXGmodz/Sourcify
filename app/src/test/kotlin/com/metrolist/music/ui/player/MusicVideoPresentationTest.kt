package com.metrolist.music.ui.player

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.ui.unit.dp
import com.metrolist.music.constants.MusicVideoQuality
import com.metrolist.music.playback.MusicVideoSession
import com.metrolist.music.playback.musicVideoAspectRatio
import com.metrolist.music.playback.musicVideoQualityHeight
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class MusicVideoPresentationTest {
    @Test fun fullscreenModesRestoreTheExistingOrientationAfterEveryClose() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            val activity = controller.get()
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER
            val insets = androidx.core.view.WindowCompat.getInsetsController(activity.window, activity.window.decorView)
            insets.isAppearanceLightStatusBars = true
            for (mode in MusicVideoFullscreen.entries) {
                MusicVideoFullscreenWindow(activity, mode).use {
                    assertFalse(insets.isAppearanceLightStatusBars)
                    assertEquals(if (mode == MusicVideoFullscreen.LANDSCAPE) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        else ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT, activity.requestedOrientation)
                }
                assertEquals(ActivityInfo.SCREEN_ORIENTATION_USER, activity.requestedOrientation)
                assertTrue(insets.isAppearanceLightStatusBars)
            }
        } finally { controller.pause().stop().destroy() }
    }

    @Test fun leavingBeforeThePlaybackServiceIsReadyDoesNotEnterPip() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            val state = MusicVideoPresentationState()
            assertFalse(controller.get().startMusicVideoPip(null, state, true))
            assertFalse(state.inPip)
            assertFalse(controller.get().isInPictureInPictureMode)
        } finally { controller.pause().stop().destroy() }
    }

    @Test fun fullscreenAndPipAlwaysSelectASingleSurfaceAndRestoreBrowsingOwner() {
        val state = MusicVideoPresentationState()
        fun owner() = MusicVideoDisplayRole.entries.filter(state::owns).single()
        assertEquals(MusicVideoDisplayRole.INLINE, owner())
        state.browsing = true
        assertEquals(MusicVideoDisplayRole.BROWSE, owner())
        state.fullscreen = MusicVideoFullscreen.PORTRAIT
        assertEquals(MusicVideoDisplayRole.OVERLAY, owner())
        state.inPip = true
        state.fullscreen = null
        assertEquals(MusicVideoDisplayRole.OVERLAY, owner())
        state.inPip = false
        assertEquals(MusicVideoDisplayRole.BROWSE, owner())
        state.browsing = false
        assertEquals(MusicVideoDisplayRole.INLINE, owner())
    }

    @Test fun pipRequiresActiveVideoPlaybackAndFitsAndroidAspectLimits() {
        assertTrue(musicVideoPipEligible(true, true, true, true))
        for (disabled in 0..3) {
            val flags = MutableList(4) { true }.also { it[disabled] = false }
            assertFalse(musicVideoPipEligible(flags[0], flags[1], flags[2], flags[3]))
        }
        for (ratio in listOf(Float.NaN, Float.POSITIVE_INFINITY, -1f, .1f, 10f, 16f / 9f)) {
            assertTrue(musicVideoPipRatio(ratio).toFloat() in (1f / 2.39f - .001f)..2.39f)
        }
    }

    @Test fun decodedAspectReplacesSquarePlaceholderAndQualityReloadPreservesItsGeometry() {
        val session = MusicVideoSession()
        val first = session.begin("song")
        session.ready(first, "song")
        session.videoSize(1920, 1080)
        assertEquals(16f / 9f, session.state.value.aspectRatio, .001f)
        val reload = session.begin("song")
        assertEquals(1920, session.state.value.videoWidth)
        assertFalse(session.ready(first, "song"))
        session.ready(reload, "song")
        session.videoSize(1080, 1920)
        assertEquals(9f / 16f, session.state.value.aspectRatio, .001f)
        session.audio("song")
        assertEquals(0, session.state.value.videoWidth)
        assertEquals(16f / 9f, musicVideoAspectRatio(0, 0), .001f)
        assertEquals(104.dp, videoMiniPlayerHeight(1f))
        assertEquals(0.dp, videoMiniPlayerExtraHeight(false, true, 1f))
    }

    @Test fun automaticQualityHonorsNetworkPolicyAndExplicitVideoQualityCanSelect1080() {
        assertEquals(360, musicVideoQualityHeight(MusicVideoQuality.AUTO, 360))
        assertEquals(720, musicVideoQualityHeight(MusicVideoQuality.AUTO, 720))
        assertEquals(1080, musicVideoQualityHeight(MusicVideoQuality.P1080, 360))
        assertEquals(480, musicVideoQualityHeight(MusicVideoQuality.P480, 720))
    }
}
