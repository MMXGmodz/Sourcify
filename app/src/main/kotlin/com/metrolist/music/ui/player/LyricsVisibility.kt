package com.metrolist.music.ui.player

import android.content.Context
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.metrolist.music.R
import com.metrolist.music.constants.HiddenSongLyricsKey
import com.metrolist.music.ui.component.Material3MenuGroup
import com.metrolist.music.ui.component.Material3MenuItemData
import com.metrolist.music.utils.rememberPreference
import com.metrolist.music.utils.safeDataStoreEdit
import kotlinx.coroutines.launch

@Composable
internal fun rememberSongLyricsVisible(songId: String): Boolean {
    val hidden by rememberPreference(HiddenSongLyricsKey, emptySet<String>())
    return songId !in hidden
}

internal suspend fun setSongLyricsVisible(context: Context, songId: String, visible: Boolean): Boolean =
    context.safeDataStoreEdit { preferences ->
        val hidden = preferences[HiddenSongLyricsKey].orEmpty()
        preferences[HiddenSongLyricsKey] = if (visible) hidden - songId else hidden + songId
    }

@Composable
internal fun LyricsVisibilityAction(songId: String, onDismiss: () -> Unit = {}) {
    val visible = rememberSongLyricsVisible(songId)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    Material3MenuGroup(items = listOf(Material3MenuItemData(
        title = { Text(stringResource(if (visible) R.string.s16_hide_song_lyrics else R.string.s16_show_song_lyrics)) },
        description = { Text(stringResource(R.string.s16_lyrics_visibility_description)) },
        icon = { Icon(painterResource(R.drawable.lyrics), null) },
        onClick = { scope.launch { if (setSongLyricsVisible(context, songId, !visible)) onDismiss() } },
    )))
}
