package com.metrolist.music.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.core.content.getSystemService
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import java.io.IOException

internal fun downloadNetworkAllowed(mobile: Boolean, background: Boolean, foreground: Boolean, validated: Boolean, metered: Boolean): Boolean =
    validated && (mobile || !metered) && (background || foreground)

internal fun guardedDownloadSource(upstream: DataSource.Factory, allowed: () -> Boolean): DataSource.Factory = DataSource.Factory {
    val source = upstream.createDataSource()
    object : DataSource by source {
        private var remote = false
        override fun open(dataSpec: DataSpec): Long {
            remote = dataSpec.uri.scheme in setOf("http", "https")
            checkPolicy()
            return source.open(dataSpec)
        }
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int { checkPolicy(); return source.read(buffer, offset, length) }
        private fun checkPolicy() { if (remote && !allowed()) throw IOException("Download paused by data/background settings") }
    }
}

internal fun offlineDataSyncAllowed(enabled: Boolean, validated: Boolean, wifi: Boolean, metered: Boolean): Boolean =
    enabled && validated && wifi && !metered

internal class DownloadNetworkPolicy private constructor(private val context: Context) {
    private val manager = context.getSystemService<ConnectivityManager>()!!
    private val lifecycle = ProcessLifecycleOwner.get().lifecycle
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private data class Connection(val validated: Boolean, val metered: Boolean, val wifi: Boolean)
    private fun connection(): Connection {
        val caps = manager.getNetworkCapabilities(manager.activeNetwork)
        return Connection(caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true,
            networkUsesMobileQuality(manager), caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true)
    }
    private val initialPrefs = runBlocking(Dispatchers.IO) { context.dataStore.data.first() }
    @Volatile private var mobileAllowed = mobileDownloadsAllowed(initialPrefs)
    @Volatile private var backgroundAllowed = backgroundDownloadsAllowed(initialPrefs)
    @Volatile private var offlineSyncEnabled = initialPrefs[com.metrolist.music.constants.AutoOfflineDataWifiKey] == true
    private val network = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { trySend(connection()) }
            override fun onLost(network: Network) { trySend(connection()) }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) { trySend(connection()) }
        }
        manager.registerDefaultNetworkCallback(callback)
        trySend(connection())
        awaitClose { manager.unregisterNetworkCallback(callback) }
    }.stateIn(scope, SharingStarted.Eagerly, connection())
    val allowed: StateFlow<Boolean> = combine(context.dataStore.data, lifecycle.currentStateFlow, network) { prefs, state, network ->
        mobileAllowed = mobileDownloadsAllowed(prefs)
        backgroundAllowed = backgroundDownloadsAllowed(prefs)
        downloadNetworkAllowed(mobileAllowed, backgroundAllowed, state.isAtLeast(Lifecycle.State.STARTED), network.validated, network.metered)
    }.stateIn(scope, SharingStarted.Eagerly, mayDownload())
    val offlineSyncAllowed: StateFlow<Boolean> = combine(context.dataStore.data, network) { prefs, network ->
        offlineSyncEnabled = prefs[com.metrolist.music.constants.AutoOfflineDataWifiKey] == true
        offlineDataSyncAllowed(offlineSyncEnabled, network.validated, network.wifi, network.metered)
    }.stateIn(scope, SharingStarted.Eagerly, maySyncOfflineData())

    fun maySyncOfflineData(): Boolean {
        val network = connection()
        return offlineDataSyncAllowed(offlineSyncEnabled, network.validated, network.wifi, network.metered)
    }
    suspend fun <T> runOfflineSync(block: suspend CoroutineScope.() -> T): T? {
        if (!maySyncOfflineData()) return null
        return runDownloadWork(offlineSyncAllowed, block)
    }

    suspend fun <T> runAllowed(block: suspend CoroutineScope.() -> T): T? {
        if (!mayDownload()) return null
        return runDownloadWork(allowed, block)
    }
    fun mayDownload(): Boolean {
        val network = connection()
        return downloadNetworkAllowed(mobileAllowed, backgroundAllowed, lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED), network.validated, network.metered)
    }
    fun guard(factory: DataSource.Factory) = guardedDownloadSource(factory, ::mayDownload)
    companion object {
        @Volatile private var instance: DownloadNetworkPolicy? = null
        fun get(context: Context): DownloadNetworkPolicy = instance ?: synchronized(this) {
            instance ?: DownloadNetworkPolicy(context.applicationContext).also { instance = it }
        }
    }
}

private class DownloadPolicyPause : CancellationException("Download policy paused optional work")

internal suspend fun <T> runDownloadWork(allowed: StateFlow<Boolean>, block: suspend CoroutineScope.() -> T): T? = coroutineScope {
        if (!allowed.value) return@coroutineScope null
        val work = async(block = block)
        val watcher = launch { allowed.first { !it }; work.cancel(DownloadPolicyPause()) }
        try { work.await() } catch (error: CancellationException) {
            if (!currentCoroutineContext().isActive || error !is DownloadPolicyPause) throw error
            null
        } finally { watcher.cancel() }
    }
