package com.metrolist.music.ui.player

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadService
import com.metrolist.music.LocalDatabase
import com.metrolist.music.LocalDownloadUtil
import com.metrolist.music.R
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.playback.ExoDownloadService
import com.metrolist.music.ui.component.Material3MenuGroup
import com.metrolist.music.ui.component.Material3MenuItemData
import com.metrolist.music.ui.component.SongEditAction
import com.metrolist.music.ui.menu.AddToPlaylistDialog
import com.metrolist.music.ui.menu.DownloadExtrasAction
import com.metrolist.music.ui.menu.MusicVideoAction
import com.metrolist.music.ui.menu.SongCanvasAction

@Composable
internal fun PlayerQuickActions(song: MediaMetadata, onDismiss: () -> Unit, onCanvasSettings: () -> Unit) {
    val database = LocalDatabase.current
    val downloads = LocalDownloadUtil.current
    val context = LocalContext.current
    val download by downloads.getDownload(song.id).collectAsStateWithLifecycle(initialValue = null)
    var playlist by remember(song.id) { mutableStateOf(false) }
    AddToPlaylistDialog(isVisible = playlist, onGetSong = {
        database.withTransaction { insert(song) }
        listOf(song.id)
    }, onGetSongIds = { listOf(song.id) }, onDismiss = { playlist = false })
    SongEditAction(song, onDismiss)
    LyricsVisibilityAction(song.id)
    MusicVideoAction(song.id, onDismiss)
    SongCanvasAction(song.id)
    Material3MenuGroup(listOf(Material3MenuItemData(
        title = { Text(stringResource(R.string.sourcify_canvas)) },
        icon = { Icon(painterResource(R.drawable.slow_motion_video), null) },
        onClick = onCanvasSettings,
    ), Material3MenuItemData(
        title = { Text(stringResource(when (download?.state) {
            Download.STATE_COMPLETED -> R.string.remove_download
            Download.STATE_QUEUED, Download.STATE_DOWNLOADING -> R.string.downloading
            else -> R.string.action_download
        })) },
        icon = { Icon(painterResource(R.drawable.download), null) },
        onClick = {
            when (download?.state) {
                Download.STATE_COMPLETED, Download.STATE_QUEUED, Download.STATE_DOWNLOADING ->
                    DownloadService.sendRemoveDownload(context, ExoDownloadService::class.java, song.id, false)
                else -> downloads.download(song)
            }
        },
    ), Material3MenuItemData(
        title = { Text(stringResource(R.string.add_to_playlist)) },
        icon = { Icon(painterResource(R.drawable.playlist_add), null) },
        onClick = { playlist = true },
    )))
    DownloadExtrasAction(song.id)
}
