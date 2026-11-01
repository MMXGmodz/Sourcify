package com.metrolist.music.utils

import androidx.media3.exoplayer.scheduler.Requirements
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test

class DownloadPolicyTest {
    @Test fun aQuickForegroundReturnDoesNotTurnAPolicyPauseIntoOwnerCancellation() = runBlocking {
        val allowed = MutableStateFlow(true)
        val entered = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val work = async {
            runDownloadWork(allowed) {
                entered.complete(Unit)
                try { awaitCancellation() } finally {
                    withContext(NonCancellable) { cancelled.complete(Unit); release.await() }
                }
            }
        }
        entered.await(); allowed.value = false; cancelled.await()
        allowed.value = true; release.complete(Unit)
        assertNull(work.await())
        Unit
    }

    @Test fun mobileSwitchControlsTheActualNetworkRequirement() {
        assertEquals(Requirements.NETWORK, downloadNetworkRequirements(true))
        assertTrue(downloadNetworkRequirements(false) and Requirements.NETWORK_UNMETERED != 0)
    }
    @Test fun backgroundPauseResumesOnlyStopsOwnedByThisPolicy() {
        val reason = downloadPolicyStopReason(0, false, false)
        assertTrue(reason != 0)
        assertEquals(0, downloadPolicyStopReason(reason, false, true))
        assertEquals(0, downloadPolicyStopReason(reason, true, false))
        assertEquals(987, downloadPolicyStopReason(987, false, false))
        assertEquals(987, downloadPolicyStopReason(987, true, true))
    }
    @Test fun storageBreakdownCannotExceedTheActualDeviceVolume() {
        val normal = storageUsage(1000, 300, 200, 100)
        assertEquals(400L, normal.other)
        assertEquals(1000L, normal.other + normal.downloads + normal.cache + normal.free)
        val raced = storageUsage(1000, 1100, Long.MAX_VALUE, Long.MAX_VALUE)
        assertEquals(0L, raced.downloads)
        assertEquals(0L, raced.cache)
        assertEquals(1000L, raced.free)
    }
}
