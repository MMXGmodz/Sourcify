package com.metrolist.music.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.pages.ArtistPage
import com.metrolist.music.LocalListenTogetherManager
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.constants.HideExplicitKey
import com.metrolist.music.constants.HideVideoSongsKey
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.playback.queues.ListQueue
import com.metrolist.music.ui.component.YouTubeListItem
import com.metrolist.music.utils.rememberPreference
import kotlinx.coroutines.CancellationException

private data class DiscoverResult(val songs: List<SongItem>, val failed: Boolean = false)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlayerDiscoverSheet(song: MediaMetadata, artist: ArtistPage?, kind: DiscoverKind, onDismiss: () -> Unit) {
    val connection = LocalPlayerConnection.current ?: return
    val context = LocalContext.current
    val manager = LocalListenTogetherManager.current
    val canPlay = manager?.let { !it.isInRoom || it.isHost } ?: true
    val current by connection.mediaMetadata.collectAsStateWithLifecycle()
    val playing by connection.isEffectivelyPlaying.collectAsStateWithLifecycle()
    val hideExplicit by rememberPreference(HideExplicitKey, false)
    val hideVideo by rememberPreference(HideVideoSongsKey, false)
    var attempt by remember(song.id, kind) { mutableIntStateOf(0) }
    val result by produceState<DiscoverResult?>(null, song.id, kind, attempt) {
        value = null
        value = try { DiscoverResult(PlayerDiscoveryRepository.load(song, artist, kind, force = attempt > 0)) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { DiscoverResult(emptyList(), failed = true) }
    }
    val songs = remember(result, hideExplicit, hideVideo, current?.id) {
        filterDiscoverCandidates(result?.songs.orEmpty(), setOfNotNull(song.id, current?.id), hideExplicit, hideVideo)
    }
    val title = stringResource(R.string.player_similar_artist,
        if (kind == DiscoverKind.ARTIST) artist?.artist?.title ?: song.artists.firstOrNull()?.name.orEmpty() else song.title)
    fun play(items: List<SongItem>, index: Int = 0) {
        if (canPlay && items.isNotEmpty()) {
            try {
                connection.playQueue(ListQueue(title, items.map { it.toMediaItem() }, startIndex = index))
                onDismiss()
            } catch (_: Exception) {
                Toast.makeText(context, R.string.player_discover_play_error, Toast.LENGTH_LONG).show()
            }
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFF202020), contentColor = Color.White) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.9f)) {
            Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) { Icon(painterResource(R.drawable.close), stringResource(R.string.close)) }
            }
            Text(stringResource(R.string.player_discover_hint), color = Color.White.copy(alpha = 0.65f), modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp))
            if (result == null) {
                Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else if (songs.isEmpty()) {
                Text(stringResource(if (result?.failed == true) R.string.player_discover_error else R.string.player_discover_empty), modifier = Modifier.padding(24.dp))
                TextButton(onClick = { attempt++ }, modifier = Modifier.padding(horizontal = 16.dp)) { Text(stringResource(R.string.retry)) }
            } else {
                Row(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { play(songs) }, enabled = canPlay) { Text(stringResource(R.string.play_all)) }
                    OutlinedButton(onClick = { play(songs.shuffled()) }, enabled = canPlay) { Text(stringResource(R.string.shuffle)) }
                }
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 32.dp)) {
                    itemsIndexed(songs, key = { _, item -> item.id }) { index, item ->
                        YouTubeListItem(item, isActive = item.id == current?.id, isPlaying = playing, isSwipeable = false,
                            modifier = Modifier.clickable(enabled = canPlay) { play(songs, index) })
                    }
                }
            }
        }
    }
}
