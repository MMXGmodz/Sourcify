package com.metrolist.music.ui.menu

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.metrolist.music.*
import com.metrolist.music.R
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.ui.component.*
import com.metrolist.music.ui.utils.ShowMediaInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun LocalAudioMenu(metadata: MediaMetadata, onDismiss: () -> Unit, onInfo: (() -> Unit)? = null) {
    val database = LocalDatabase.current
    val connection = LocalPlayerConnection.current ?: return
    val scope = rememberCoroutineScope()
    val song by database.song(metadata.id).collectAsStateWithLifecycle(initialValue = null)
    val volume by connection.service.playerVolume.collectAsStateWithLifecycle()
    var details by remember { mutableStateOf(false) }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(metadata.title, style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.local_audio_network_notice), style = MaterialTheme.typography.bodySmall)
        RenameSongAction(metadata, onDismiss)
        PlayerSleepTimerAction()
        SongCanvasAction(metadata.id)
        VolumeSlider(value = volume, onValueChange = { connection.service.playerVolume.value = it }, modifier = Modifier.fillMaxWidth())
        Material3MenuGroup(items = listOf(
            Material3MenuItemData(title = { Text(stringResource(R.string.details)) }, icon = { Icon(painterResource(R.drawable.info), null) },
                onClick = { if (onInfo != null) { onInfo(); onDismiss() } else details = true }),
            Material3MenuItemData(title = { Text(stringResource(if (song?.song?.liked == true) R.string.tv_unfavorite else R.string.tv_favorite)) },
                icon = { Icon(painterResource(R.drawable.favorite), null) }, onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            val value = song?.song ?: metadata.toSongEntity()
                            if (song == null) database.insert(metadata)
                            database.update(value.toggleLike(syncToYouTube = false))
                        }
                        onDismiss()
                    }
                }),
            Material3MenuItemData(title = { Text(stringResource(R.string.play_next)) }, icon = { Icon(painterResource(R.drawable.queue_music), null) },
                onClick = { connection.playNext(metadata.toMediaItem()); onDismiss() }),
            Material3MenuItemData(title = { Text(stringResource(R.string.add_to_queue)) }, icon = { Icon(painterResource(R.drawable.queue_music), null) },
                onClick = { connection.addToQueue(metadata.toMediaItem()); onDismiss() }),
        ))
    }
    if (details) DefaultDialog(onDismiss = { details = false }) { ShowMediaInfo(metadata.id) }
}
