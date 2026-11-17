package com.metrolist.music.utils

import android.app.Application
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import com.metrolist.music.constants.*
import com.metrolist.music.playback.OfflineDataSyncScheduler
import com.metrolist.music.playback.OfflineDataSyncService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetworkCapabilities
import org.robolectric.shadows.ShadowNetworkInfo

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class OfflineDataSyncTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Suppress("DEPRECATION")
    private fun connect(wifi: Boolean) {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val shadow = shadowOf(manager)
        shadow.setActiveNetworkInfo(ShadowNetworkInfo.newInstance(NetworkInfo.DetailedState.CONNECTED,
            if (wifi) ConnectivityManager.TYPE_WIFI else ConnectivityManager.TYPE_MOBILE, 0, true, true))
        val caps = ShadowNetworkCapabilities.newInstance()
        shadowOf(caps).addTransportType(if (wifi) NetworkCapabilities.TRANSPORT_WIFI else NetworkCapabilities.TRANSPORT_CELLULAR)
        shadowOf(caps).addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        val network = requireNotNull(manager.activeNetwork)
        shadow.setNetworkCapabilities(network, caps)
        shadow.networkCallbacks.toList().forEach { it.onCapabilitiesChanged(network, caps) }
    }

    @Test fun offlineSyncRequiresOptInAndValidatedUnmeteredWifi() {
        assertTrue(offlineDataSyncAllowed(true, true, true, false))
        assertFalse(offlineDataSyncAllowed(false, true, true, false))
        assertFalse(offlineDataSyncAllowed(true, false, true, false))
        assertFalse("Unmetered cellular is still not Wi-Fi", offlineDataSyncAllowed(true, true, false, false))
        assertFalse("A metered hotspot must not use the allowance", offlineDataSyncAllowed(true, true, true, true))
    }

    @Test fun schedulerPersistsWifiConstraintsAndDisablingCancelsOnlyThisJob() {
        val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
        val service = context.packageManager.getServiceInfo(ComponentName(context, OfflineDataSyncService::class.java), 0)
        assertEquals("android.permission.BIND_JOB_SERVICE", service.permission)
        OfflineDataSyncScheduler.configure(context, true)
        val job = requireNotNull(scheduler.getPendingJob(OfflineDataSyncScheduler.JOB_ID))
        assertTrue(job.isPersisted)
        assertTrue(job.isPeriodic)
        assertTrue(job.isRequireBatteryNotLow)
        assertTrue(job.isRequireStorageNotLow)
        assertTrue(requireNotNull(job.requiredNetwork).hasTransport(NetworkCapabilities.TRANSPORT_WIFI))
        assertTrue(requireNotNull(job.requiredNetwork).hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED))
        OfflineDataSyncScheduler.configure(context, true)
        assertEquals(1, scheduler.allPendingJobs.size)
        scheduler.schedule(android.app.job.JobInfo.Builder(1234, job.service).setMinimumLatency(60_000).build())
        OfflineDataSyncScheduler.configure(context, false)
        assertNull(scheduler.getPendingJob(OfflineDataSyncScheduler.JOB_ID))
        assertNotNull(scheduler.getPendingJob(1234))
    }

    @Test fun activeWifiSyncStopsOnMobileAndOptOutWithoutAllowingBackgroundSongDownloads() = runBlocking {
        val original = context.dataStore.data.first()
        var created: DownloadNetworkPolicy? = null
        try {
            context.dataStore.edit {
                it[AutoOfflineDataWifiKey] = false
                it[DataSaverKey] = true
                it[DownloadMobileAllowedKey] = false
                it[DownloadBackgroundAllowedKey] = false
            }
            connect(wifi = true)
            // Robolectric resets callbacks between tests, while production's singleton survives in the sandbox.
            val policy = DownloadNetworkPolicy::class.java.getDeclaredConstructor(Context::class.java)
                .apply { isAccessible = true }.newInstance(context).also { created = it }
            assertFalse(policy.maySyncOfflineData())
            context.dataStore.edit { it[AutoOfflineDataWifiKey] = true }
            withTimeout(5000) {
                policy.offlineSyncAllowed.first { it }
                val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                while (shadowOf(manager).networkCallbacks.isEmpty()) delay(10)
            }
            assertTrue(policy.maySyncOfflineData())
            assertFalse("Wi-Fi completion must not enable background audio downloads", policy.mayDownload())
            suspend fun start(): Deferred<Boolean?> {
                val entered = CompletableDeferred<Unit>()
                val task = async<Boolean?> {
                    policy.runOfflineSync<Boolean> {
                        entered.complete(Unit)
                        awaitCancellation()
                    }
                }
                withTimeout(5000) { entered.await() }
                return task
            }
            val onWifi = start()
            connect(wifi = false)
            assertFalse("Reads must see mobile immediately", policy.maySyncOfflineData())
            assertNull(withTimeout(5000) { onWifi.await() })
            connect(wifi = true)
            withTimeout(5000) { policy.offlineSyncAllowed.first { it } }
            val reconnected = start()
            context.dataStore.edit { it[AutoOfflineDataWifiKey] = false }
            assertNull(withTimeout(5000) { reconnected.await() })
            assertFalse(policy.maySyncOfflineData())
            assertFalse(policy.mayDownload())
        } finally {
            created?.let { policy ->
                val scope = org.robolectric.util.ReflectionHelpers.getField<CoroutineScope>(policy, "scope")
                scope.cancel()
                scope.coroutineContext[Job]?.join()
            }
            context.dataStore.edit { prefs ->
                fun restore(key: androidx.datastore.preferences.core.Preferences.Key<Boolean>) {
                    original[key]?.let { prefs[key] = it } ?: prefs.remove(key)
                }
                restore(AutoOfflineDataWifiKey); restore(DataSaverKey)
                restore(DownloadMobileAllowedKey); restore(DownloadBackgroundAllowedKey)
            }
        }
    }
}
