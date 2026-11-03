package com.metrolist.music.utils

import androidx.datastore.preferences.core.mutablePreferencesOf
import com.metrolist.music.constants.*
import org.junit.Assert.*
import org.junit.Test

class MediaQualityPolicyTest {
    @Test fun canvasRejectsTheExtractorFallbackWhenEveryFormatExceedsTheSelectedLimit() {
        assertTrue(canvasVideoFitsQuality(240, 240))
        assertTrue(canvasVideoFitsQuality(480, 720))
        assertFalse(canvasVideoFitsQuality(360, 240))
        assertFalse(canvasVideoFitsQuality(1080, 720))
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
        assertEquals(720, canvasVideoHeight(prefs, false))
    }
}
