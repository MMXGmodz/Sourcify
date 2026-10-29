package com.metrolist.music.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.core.content.getSystemService
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import com.metrolist.music.constants.CanvasWifiOnlyKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import java.io.IOException

internal fun allowRemoteCanvas(wifiOnly: Boolean, wifiOrEthernet: Boolean, metered: Boolean): Boolean =
    !wifiOnly || (wifiOrEthernet && !metered)

internal class CanvasNetworkPolicy private constructor(private val context: Context) {
    private val manager = context.getSystemService<ConnectivityManager>()!!
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private fun unmeteredLan(): Boolean {
        val caps = manager.getNetworkCapabilities(manager.activeNetwork)
        return allowRemoteCanvas(true, caps?.let {
            it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || it.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        } == true, manager.isActiveNetworkMetered)
    }
    val remoteAllowed: StateFlow<Boolean> = combine(
        context.dataStore.data.map { it[CanvasWifiOnlyKey] ?: false }.distinctUntilChanged(),
        callbackFlow {
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) { trySend(unmeteredLan()) }
                override fun onLost(network: Network) { trySend(unmeteredLan()) }
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) { trySend(unmeteredLan()) }
            }
            manager.registerDefaultNetworkCallback(callback)
            trySend(unmeteredLan())
            awaitClose { manager.unregisterNetworkCallback(callback) }
        }.distinctUntilChanged(),
    ) { wifiOnly, lan -> !wifiOnly || lan }.stateIn(scope, SharingStarted.Eagerly, mayLoadRemote())

    fun mayLoadRemote(): Boolean = !context.dataStore.get(CanvasWifiOnlyKey, false) || unmeteredLan()

    fun guard(upstream: DataSource.Factory): DataSource.Factory = DataSource.Factory {
        val source = upstream.createDataSource()
        object : DataSource by source {
            private var remote = false
            override fun open(dataSpec: DataSpec): Long {
                remote = dataSpec.uri.scheme in setOf("http", "https")
                if (remote && !mayLoadRemote()) throw IOException("Canvas is restricted to unmetered Wi-Fi or Ethernet")
                return source.open(dataSpec)
            }
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                checkAccess()
                return source.read(buffer, offset, length)
            }
            private fun checkAccess() {
                if (remote && !remoteAllowed.value) throw IOException("Canvas is restricted to unmetered Wi-Fi or Ethernet")
            }
        }
    }

    companion object {
        @Volatile private var instance: CanvasNetworkPolicy? = null
        fun get(context: Context): CanvasNetworkPolicy = instance ?: synchronized(this) {
            instance ?: CanvasNetworkPolicy(context.applicationContext).also { instance = it }
        }
    }
}
