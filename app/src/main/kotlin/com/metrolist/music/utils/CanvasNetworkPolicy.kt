package com.metrolist.music.utils

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.media3.datasource.DataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.*

internal fun allowRemoteCanvas(wifiOrEthernet: Boolean, metered: Boolean): Boolean =
    wifiOrEthernet && !metered

internal data class CanvasNetworkProfile(val remoteAllowed: Boolean, val maxVideoHeight: Int)

internal fun canvasNetworkProfile(prefs: Preferences, unmeteredLan: Boolean, mobileQuality: Boolean): CanvasNetworkProfile =
    CanvasNetworkProfile(unmeteredLan, canvasVideoHeight(prefs, mobileQuality))

internal class CanvasNetworkPolicy private constructor(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val dataSaver = DataSaverPolicy.get(context)
    private val initialProfile = runBlocking(Dispatchers.IO) {
        val network = dataSaver.currentConnection()
        canvasNetworkProfile(context.dataStore.data.first(), network.unmeteredLan, network.mobileQuality)
    }
    val profile: StateFlow<CanvasNetworkProfile> = combine(
        context.dataStore.data, dataSaver.connection,
    ) { prefs, network -> canvasNetworkProfile(prefs, network.unmeteredLan, network.mobileQuality) }
        .distinctUntilChanged().stateIn(scope, SharingStarted.Eagerly, initialProfile)

    val remoteAllowed: StateFlow<Boolean> = profile.map { it.remoteAllowed }.distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, initialProfile.remoteAllowed)

    fun mayLoadRemote(): Boolean = dataSaver.currentConnection().unmeteredLan

    fun guard(upstream: DataSource.Factory): DataSource.Factory = guardedDownloadSource(upstream, ::mayLoadRemote)

    companion object {
        @Volatile private var instance: CanvasNetworkPolicy? = null
        fun get(context: Context): CanvasNetworkPolicy = instance ?: synchronized(this) {
            instance ?: CanvasNetworkPolicy(context.applicationContext).also { instance = it }
        }
    }
}
