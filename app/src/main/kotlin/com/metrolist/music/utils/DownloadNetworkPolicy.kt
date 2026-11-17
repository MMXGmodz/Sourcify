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

internal class DownloadNetworkPolicy private constructor(private val context: Context) {
    private val manager = context.getSystemService<ConnectivityManager>()!!
    private val lifecycle = ProcessLifecycleOwner.get().lifecycle
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private fun connection() = (manager.getNetworkCapabilities(manager.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true) to networkUsesMobileQuality(manager)
    private val initialPrefs = runBlocking(Dispatchers.IO) { context.dataStore.data.first() }
    @Volatile private var mobileAllowed = mobileDownloadsAllowed(initialPrefs)
    @Volatile private var backgroundAllowed = backgroundDownloadsAllowed(initialPrefs)
    val allowed: StateFlow<Boolean> = combine(context.dataStore.data, lifecycle.currentStateFlow, callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { trySend(connection()) }
            override fun onLost(network: Network) { trySend(connection()) }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) { trySend(connection()) }
        }
        manager.registerDefaultNetworkCallback(callback)
        trySend(connection())
        awaitClose { manager.unregisterNetworkCallback(callback) }
    }) { prefs, state, network ->
        mobileAllowed = mobileDownloadsAllowed(prefs)
        backgroundAllowed = backgroundDownloadsAllowed(prefs)
        downloadNetworkAllowed(mobileAllowed, backgroundAllowed, state.isAtLeast(Lifecycle.State.STARTED), network.first, network.second)
    }.stateIn(scope, SharingStarted.Eagerly, mayDownload())

    suspend fun <T> runAllowed(block: suspend CoroutineScope.() -> T): T? {
        if (!mayDownload()) return null
        return runDownloadWork(allowed, block)
    }
    fun mayDownload(): Boolean {
        val network = connection()
        return downloadNetworkAllowed(mobileAllowed, backgroundAllowed, lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED), network.first, network.second)
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
