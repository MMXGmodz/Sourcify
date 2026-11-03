package com.metrolist.music.utils

import androidx.datastore.preferences.core.Preferences
import com.metrolist.music.constants.*

internal fun readAudioQuality(value: String?): AudioQuality? =
    if (value == "VERY_HIGH") AudioQuality.HIGH else AudioQuality.entries.find { it.name == value }

internal fun streamingAudioQuality(prefs: Preferences, metered: Boolean): AudioQuality =
    readAudioQuality(prefs[if (metered) StreamingMobileQualityKey else StreamingWifiQualityKey])
        ?: readAudioQuality(prefs[AudioQualityKey]) ?: AudioQuality.AUTO

internal val supportedCanvasHeights = listOf(240, 360, 480, 720)
internal fun normalizedCanvasHeight(height: Int?): Int = height?.takeIf { it in supportedCanvasHeights } ?: 720
internal fun canvasVideoHeight(prefs: Preferences, metered: Boolean): Int =
    normalizedCanvasHeight(prefs[if (metered) CanvasMobileHeightKey else CanvasWifiHeightKey])

internal fun canvasVideoFitsQuality(height: Int?, maxHeight: Int): Boolean =
    height != null && height in 1..maxHeight

internal fun networkUsesMobileQuality(manager: android.net.ConnectivityManager): Boolean =
    networkUsesMobileQuality(manager.getNetworkCapabilities(manager.activeNetwork), manager.isActiveNetworkMetered)

internal fun networkUsesMobileQuality(capabilities: android.net.NetworkCapabilities?, metered: Boolean): Boolean =
    capabilities?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) != true &&
        (capabilities?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) == true || metered)
