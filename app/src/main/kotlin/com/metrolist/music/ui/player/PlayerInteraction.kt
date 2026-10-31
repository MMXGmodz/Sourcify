package com.metrolist.music.ui.player

internal fun lyricsSeekPosition(time: Long, offset: Int, synced: Boolean, enabled: Boolean, guest: Boolean): Long? =
    if (!synced || !enabled || guest || time < 0) null else (time - offset).coerceAtLeast(0)

internal fun compactHeaderProgress(scroll: Float, start: Float, distance: Float): Float =
    if (!scroll.isFinite() || !start.isFinite() || !distance.isFinite() || distance <= 0) 0f
    else ((scroll - start) / distance).coerceIn(0f, 1f)

internal fun lyricsReportedPosition(reported: Long, pending: Long?, elapsedSinceSeek: Long): Long =
    if (pending != null && elapsedSinceSeek in 0..1500 && kotlin.math.abs(reported - pending) > 750) pending else reported
