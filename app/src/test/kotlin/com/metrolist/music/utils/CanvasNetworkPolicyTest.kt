package com.metrolist.music.utils

import org.junit.Assert.*
import org.junit.Test

class CanvasNetworkPolicyTest {
    @Test fun networkChangesPublishPermissionAndSelectedQualityTogether() {
        val prefs = androidx.datastore.preferences.core.mutablePreferencesOf(
            com.metrolist.music.constants.CanvasWifiHeightKey to 720,
            com.metrolist.music.constants.CanvasMobileHeightKey to 240,
        )
        assertEquals(CanvasNetworkProfile(true, 720), canvasNetworkProfile(prefs, true, false))
        assertEquals(CanvasNetworkProfile(true, 240), canvasNetworkProfile(prefs, false, true))
        prefs[com.metrolist.music.constants.CanvasWifiOnlyKey] = true
        assertEquals(CanvasNetworkProfile(false, 240), canvasNetworkProfile(prefs, false, true))
        assertEquals(CanvasNetworkProfile(true, 720), canvasNetworkProfile(prefs, true, false))
    }

    @Test fun wifiRestrictionExcludesCellularAndMeteredWifiButAllowsLanForTv() {
        assertTrue(allowRemoteCanvas(false, false, true))
        assertTrue(allowRemoteCanvas(true, true, false))
        assertFalse(allowRemoteCanvas(true, false, false))
        assertFalse(allowRemoteCanvas(true, true, true))
        assertFalse(allowRemoteCanvas(true, false, true))
    }
}
