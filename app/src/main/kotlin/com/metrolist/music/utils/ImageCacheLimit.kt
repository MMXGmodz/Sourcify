package com.metrolist.music.utils

internal fun imageCacheLimitBytes(mebibytes: Int): Long = when {
    mebibytes == -1 -> Long.MAX_VALUE
    mebibytes <= 0 -> 0L
    else -> mebibytes * 1024 * 1024L
}
