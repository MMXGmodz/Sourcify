package com.metrolist.music.utils

import android.net.Uri
import androidx.media3.datasource.ByteArrayDataSource
import androidx.media3.datasource.DataSpec
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class DownloadNetworkGuardTest {
    @Test fun remoteReadsStopAfterPolicyChangesButLocalReadsRemainAvailable() {
        var allowed = true
        val factory = guardedDownloadSource({ ByteArrayDataSource(byteArrayOf(1,2,3)) }) { allowed }
        val source = factory.createDataSource()
        source.open(DataSpec(Uri.parse("https://example.com/test")))
        assertEquals(1, source.read(ByteArray(1),0,1))
        allowed = false
        assertThrows(IOException::class.java) { source.read(ByteArray(1),0,1) }
        source.close()
        val local = factory.createDataSource()
        local.open(DataSpec(Uri.parse("file:///private/local")))
        assertEquals(1, local.read(ByteArray(1),0,1)); local.close()
    }
    @Test fun policyIncludesConnectivityMeteringAndForeground() {
        assertTrue(downloadNetworkAllowed(true,true,false,true,true))
        assertFalse(downloadNetworkAllowed(false,true,true,true,true))
        assertFalse(downloadNetworkAllowed(true,false,false,true,false))
        assertTrue(downloadNetworkAllowed(false,false,true,true,false))
        assertFalse(downloadNetworkAllowed(true,true,true,false,false))
    }
}
