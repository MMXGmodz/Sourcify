package com.metrolist.music.ui.screens.library

import com.metrolist.music.db.entities.PlaylistEntity

internal const val CACHED_LIBRARY_PLAYLIST_ID = "LP_CACHED"
internal const val TOP_LIBRARY_PLAYLIST_ID = "LP_TOP"
internal const val UPLOADED_LIBRARY_PLAYLIST_ID = "LP_UPLOADED"

fun libraryPlaylistRoute(id: String, topSize: Int = 50): String? = when (id) {
    PlaylistEntity.LIKED_PLAYLIST_ID -> "auto_playlist/liked"
    PlaylistEntity.DOWNLOADED_PLAYLIST_ID -> "auto_playlist/downloaded"
    CACHED_LIBRARY_PLAYLIST_ID -> "cache_playlist/cached"
    TOP_LIBRARY_PLAYLIST_ID -> "top_playlist/$topSize"
    UPLOADED_LIBRARY_PLAYLIST_ID -> "auto_playlist/uploaded"
    else -> null
}

internal fun <T> List<T>.pinnedPlaylistsFirst(
    pinnedIds: Set<String>,
    playlistId: (T) -> String?,
): List<T> = sortedBy { if (playlistId(it) in pinnedIds) 0 else 1 }
