package com.metrolist.music.playback

import androidx.media3.datasource.cache.Cache

/** Returns whether the current stream should reload with the newly selected quality. */
internal fun prepareAudioQualityChange(
    mediaId: String,
    playerCache: Cache,
    downloadCache: Cache,
): Boolean {
    // Saved and in-progress downloads retain their original encoded audio.
    if (downloadCache.getCachedSpans(mediaId).isNotEmpty()) return false
    playerCache.removeResource(mediaId)
    return true
}
