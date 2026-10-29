package com.metrolist.music.utils

import org.junit.Assert.*
import org.junit.Test

class CanvasNetworkPolicyTest {
    @Test fun wifiRestrictionExcludesCellularAndMeteredWifiButAllowsLanForTv() {
        assertTrue(allowRemoteCanvas(false, false, true))
        assertTrue(allowRemoteCanvas(true, true, false))
        assertFalse(allowRemoteCanvas(true, false, false))
        assertFalse(allowRemoteCanvas(true, true, true))
        assertFalse(allowRemoteCanvas(true, false, true))
    }
}
