package com.metrolist.music.ui.menu

import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.metrolist.music.extensions.currentMetadata
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.playback.MusicVideoMode
import com.metrolist.music.ui.component.Material3MenuGroup
import com.metrolist.music.ui.component.Material3MenuItemData

@Composable
internal fun MusicVideoAction(songId: String, onDismiss: () -> Unit) {
    val connection = LocalPlayerConnection.current ?: return
    val state by connection.service.musicVideoState.collectAsStateWithLifecycle()
    if (connection.player.currentMediaItem?.mediaId != songId || connection.player.currentMetadata?.isEpisode == true) return
    val isAudio = state.mode == MusicVideoMode.AUDIO
    Material3MenuGroup(listOf(Material3MenuItemData(
        title = { Text(stringResource(if (isAudio) R.string.sourcify_switch_to_video else R.string.sourcify_switch_to_audio)) },
        icon = { Icon(painterResource(R.drawable.slow_motion_video), null) },
        onClick = {
            if (isAudio) connection.service.switchToMusicVideo() else connection.service.switchToAudio()
            onDismiss()
        },
    )))
}
