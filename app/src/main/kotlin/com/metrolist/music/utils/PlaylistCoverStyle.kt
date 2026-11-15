package com.metrolist.music.utils

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.key
import androidx.datastore.preferences.core.booleanPreferencesKey

internal fun playlistCoverStyleKey(playlistId: String) =
    booleanPreferencesKey("playlist_classic_cover_$playlistId")

@Composable
internal fun rememberClassicPlaylistCover(
    playlistId: String,
    defaultValue: Boolean = false,
): MutableState<Boolean> = key(playlistId, defaultValue) {
    rememberPreference(playlistCoverStyleKey(playlistId), defaultValue)
}

internal suspend fun setClassicPlaylistCover(context: Context, playlistId: String, classic: Boolean): Boolean =
    context.safeDataStoreEdit { it[playlistCoverStyleKey(playlistId)] = classic }
