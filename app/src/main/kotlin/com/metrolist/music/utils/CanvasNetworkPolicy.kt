package com.metrolist.music.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.core.content.getSystemService
import androidx.datastore.preferences.core.Preferences
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import com.metrolist.music.constants.CanvasWifiOnlyKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import java.io.IOException

internal fun allowRemoteCanvas(wifiOnly: Boolean, wifiOrEthernet: Boolean, metered: Boolean): Boolean =
    !wifiOnly || (wifiOrEthernet && !metered)

internal data class CanvasNetworkProfile(val remoteAllowed: Boolean, val maxVideoHeight: Int)

internal fun canvasNetworkProfile(prefs: Preferences, unmeteredLan: Boolean, mobileQuality: Boolean): CanvasNetworkProfile =
    CanvasNetworkProfile(prefs[CanvasWifiOnlyKey] != true || unmeteredLan, canvasVideoHeight(prefs, mobileQuality))

internal class CanvasNetworkPolicy private constructor(private val context: Context) {
    private val manager = context.getSystemService<ConnectivityManager>()!!
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private data class NetworkState(val unmeteredLan: Boolean, val mobileQuality: Boolean)
    private fun networkState(): NetworkState {
        val caps = manager.getNetworkCapabilities(manager.activeNetwork)
        val metered = manager.isActiveNetworkMetered
        val lan = caps?.let {
            it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || it.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        } == true
        return NetworkState(allowRemoteCanvas(true, lan, metered), networkUsesMobileQuality(caps, metered))
    }
    private val initialProfile = runBlocking(Dispatchers.IO) {
        val network = networkState()
        canvasNetworkProfile(context.dataStore.data.first(), network.unmeteredLan, network.mobileQuality)
    }
    val profile: StateFlow<CanvasNetworkProfile> = combine(
        context.dataStore.data,
        callbackFlow {
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) { trySend(networkState()) }
                override fun onLost(network: Network) { trySend(networkState()) }
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) { trySend(networkState()) }
            }
            manager.registerDefaultNetworkCallback(callback)
            trySend(networkState())
            awaitClose { manager.unregisterNetworkCallback(callback) }
        }.distinctUntilChanged(),
    ) { prefs, network -> canvasNetworkProfile(prefs, network.unmeteredLan, network.mobileQuality) }
        .distinctUntilChanged().stateIn(scope, SharingStarted.Eagerly, initialProfile)

    val remoteAllowed: StateFlow<Boolean> = profile.map { it.remoteAllowed }.distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, initialProfile.remoteAllowed)

    fun mayLoadRemote(): Boolean = !context.dataStore.get(CanvasWifiOnlyKey, false) || networkState().unmeteredLan

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
                if (remote && !profile.value.remoteAllowed) throw IOException("Canvas is restricted to unmetered Wi-Fi or Ethernet")
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
