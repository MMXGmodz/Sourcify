package com.metrolist.music.utils

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.datastore.preferences.core.stringPreferencesKey

internal fun playlistColorKey(playlistId: String) = stringPreferencesKey("playlist_main_color_" + when (playlistId) {
    "LP_LIKED" -> "auto:liked"
    "LP_DOWNLOADED" -> "auto:downloaded"
    "LP_TOP" -> "auto:top"
    "LP_CACHED" -> "auto:cache"
    "LP_UPLOADED" -> "auto:uploaded"
    else -> playlistId.removePrefix("VL")
})
internal fun readPlaylistColor(value: String?): Int? = value?.toIntOrNull()?.or(0xFF000000.toInt())

@Composable
internal fun rememberPlaylistColor(playlistId: String): Int? = key(playlistId) {
    readPlaylistColor(rememberPreference(playlistColorKey(playlistId), "").value)
}

internal suspend fun setPlaylistColor(context: Context, playlistId: String, color: Int?): Boolean =
    context.safeDataStoreEdit { prefs ->
        if (color == null) prefs.remove(playlistColorKey(playlistId))
        else prefs[playlistColorKey(playlistId)] = (color or 0xFF000000.toInt()).toString()
    }
