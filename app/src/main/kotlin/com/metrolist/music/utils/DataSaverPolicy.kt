package com.metrolist.music.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.core.content.getSystemService
import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences
import com.metrolist.music.constants.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking

internal fun dataSaverEnabled(prefs: Preferences): Boolean = prefs[DataSaverKey] != false
internal fun mobileDownloadsAllowed(prefs: Preferences): Boolean = !dataSaverEnabled(prefs) && prefs[DownloadMobileAllowedKey] == true
internal fun backgroundDownloadsAllowed(prefs: Preferences): Boolean = prefs[DownloadBackgroundAllowedKey] == true

internal object StrictDataDefaultsMigration : DataMigration<Preferences> {
    override suspend fun shouldMigrate(currentData: Preferences) = currentData[StrictDataDefaultsMigratedKey] != true
    override suspend fun migrate(currentData: Preferences): Preferences = currentData.toMutablePreferences().apply {
        this[DataSaverKey] = true
        this[CanvasWifiOnlyKey] = true
        this[DownloadMobileAllowedKey] = false
        this[DownloadBackgroundAllowedKey] = false
        this[StrictDataDefaultsMigratedKey] = true
    }
    override suspend fun cleanUp() = Unit
}

internal data class DataConnection(val unmeteredLan: Boolean, val mobileQuality: Boolean)

internal class DataSaverPolicy private constructor(context: Context) {
    private val context = context.applicationContext
    private val manager = context.getSystemService<ConnectivityManager>()!!
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    fun currentConnection(): DataConnection {
        val caps = manager.getNetworkCapabilities(manager.activeNetwork)
        val lan = caps?.let { it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || it.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) } == true
        return DataConnection(allowRemoteCanvas(lan, manager.isActiveNetworkMetered) && caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) != true, networkUsesMobileQuality(caps, manager.isActiveNetworkMetered))
    }
    val connection: StateFlow<DataConnection> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { trySend(currentConnection()) }
            override fun onLost(network: Network) { trySend(currentConnection()) }
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) { trySend(currentConnection()) }
        }
        manager.registerDefaultNetworkCallback(callback)
        trySend(currentConnection())
        awaitClose { manager.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged().stateIn(scope, SharingStarted.Eagerly, currentConnection())

    val audioQuality = combine(context.dataStore.data, connection) { prefs, network ->
        streamingAudioQuality(prefs, network.mobileQuality)
    }.distinctUntilChanged().stateIn(scope, SharingStarted.Eagerly, runBlocking(Dispatchers.IO) {
        streamingAudioQuality(context.dataStore.data.first(), currentConnection().mobileQuality)
    })

    fun mayStreamVideo(): Boolean = currentConnection().unmeteredLan
    fun guardVideo(factory: androidx.media3.datasource.DataSource.Factory) = guardedDownloadSource(factory, ::mayStreamVideo)

    companion object {
        @Volatile private var instance: DataSaverPolicy? = null
        fun get(context: Context): DataSaverPolicy = instance ?: synchronized(this) {
            instance ?: DataSaverPolicy(context).also { instance = it }
        }
    }
}
