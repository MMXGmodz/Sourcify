package com.metrolist.music.utils

import androidx.media3.exoplayer.scheduler.Requirements

internal const val DOWNLOAD_POLICY_STOP_REASON = 21327
internal fun downloadNetworkRequirements(mobileAllowed: Boolean): Int =
    if (mobileAllowed) Requirements.NETWORK else Requirements.NETWORK or Requirements.NETWORK_UNMETERED

internal fun downloadPolicyStopReason(current: Int, backgroundAllowed: Boolean, foreground: Boolean): Int = when {
    !backgroundAllowed && !foreground && current == 0 -> DOWNLOAD_POLICY_STOP_REASON
    (backgroundAllowed || foreground) && current == DOWNLOAD_POLICY_STOP_REASON -> 0
    else -> current
}

internal data class StorageUsage(val total: Long, val free: Long, val downloads: Long, val cache: Long, val other: Long)
internal fun storageUsage(total: Long, free: Long, downloads: Long, cache: Long): StorageUsage {
    val capacity = total.coerceAtLeast(0)
    val available = free.coerceIn(0, capacity)
    val used = capacity - available
    val saved = downloads.coerceIn(0, used)
    val cached = cache.coerceIn(0, used - saved)
    return StorageUsage(capacity, available, saved, cached, used - saved - cached)
}
