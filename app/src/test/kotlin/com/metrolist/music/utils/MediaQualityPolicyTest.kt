package com.metrolist.music.utils

import androidx.datastore.preferences.core.mutablePreferencesOf
import com.metrolist.music.constants.*
import org.junit.Assert.*
import org.junit.Test

class MediaQualityPolicyTest {
    @Test fun canvasDefaultsToFullHdOnWifiWithoutIncreasingMobileDataUse() {
        val prefs = mutablePreferencesOf()
        assertEquals(1080, canvasVideoHeight(prefs, false))
        assertEquals(720, canvasVideoHeight(prefs, true))
        assertTrue(1080 in supportedCanvasHeights)
        prefs[CanvasWifiHeightKey] = 480
        prefs[CanvasMobileHeightKey] = 240
        assertEquals(480, canvasVideoHeight(prefs, false))
        assertEquals(240, canvasVideoHeight(prefs, true))
        prefs[CanvasWifiHeightKey] = 720
        assertEquals(720, canvasVideoHeight(prefs, false))
        prefs[CanvasWifiHeightKey] = 1080
        assertEquals(1080, canvasVideoHeight(prefs, false))
    }

    @Test fun canvasRejectsTheExtractorFallbackWhenEveryFormatExceedsTheSelectedLimit() {
        assertTrue(canvasVideoFitsQuality(240, 240))
        assertTrue(canvasVideoFitsQuality(480, 720))
        assertTrue(canvasVideoFitsQuality(1080, 1080))
        assertFalse(canvasVideoFitsQuality(360, 240))
        assertFalse(canvasVideoFitsQuality(1080, 720))
        assertFalse(canvasVideoFitsQuality(2160, 1080))
        assertFalse(canvasVideoFitsQuality(null, 720))
        assertFalse(canvasVideoFitsQuality(0, 720))
    }

    @Test fun streamingProfilesStayIndependentAndDownloadsKeepLegacyChoice() {
        val prefs = mutablePreferencesOf(AudioQualityKey to "LOW", StreamingWifiQualityKey to "HIGH", StreamingMobileQualityKey to "LOW")
        assertEquals(AudioQuality.HIGH, streamingAudioQuality(prefs, false))
        assertEquals(AudioQuality.LOW, streamingAudioQuality(prefs, true))
        prefs[AudioQualityKey] = "HIGH"
        assertEquals(AudioQuality.LOW, streamingAudioQuality(prefs, true))
        assertEquals("HIGH", prefs[AudioQualityKey])
    }
    @Test fun absentOrInvalidProfilesPreservePriorQualityIncludingOldAlias() {
        val prefs = mutablePreferencesOf(AudioQualityKey to "VERY_HIGH")
        assertEquals(AudioQuality.HIGH, streamingAudioQuality(prefs, false))
        prefs[StreamingWifiQualityKey] = "unknown"
        assertEquals(AudioQuality.HIGH, streamingAudioQuality(prefs, false))
        assertEquals(AudioQuality.AUTO, streamingAudioQuality(mutablePreferencesOf(), true))
    }
    @Test fun canvasVideoLimitUsesTheCurrentNetworkAndRejectsInvalidValues() {
        val prefs = mutablePreferencesOf(CanvasWifiHeightKey to 720, CanvasMobileHeightKey to 240)
        assertEquals(720, canvasVideoHeight(prefs, false))
        assertEquals(240, canvasVideoHeight(prefs, true))
        prefs[CanvasMobileHeightKey] = -1
        assertEquals(720, canvasVideoHeight(prefs, true))
        prefs[CanvasWifiHeightKey] = 2160
        assertEquals(1080, canvasVideoHeight(prefs, false))
    }
}
