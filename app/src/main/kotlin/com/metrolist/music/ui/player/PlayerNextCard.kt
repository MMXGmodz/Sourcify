package com.metrolist.music.ui.player

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.extensions.metadata

internal fun nextQueueItem(player: androidx.media3.common.Player): androidx.media3.common.MediaItem? {
    val index = player.nextMediaItemIndex
    return if (index in 0 until player.mediaItemCount) player.getMediaItemAt(index) else null
}

@Composable
internal fun PlayerNextCard(onOpenQueue: () -> Unit) {
    val connection = LocalPlayerConnection.current ?: return
    val windows by connection.queueWindows.collectAsStateWithLifecycle()
    val index by connection.currentWindowIndex.collectAsStateWithLifecycle()
    val shuffle by connection.shuffleModeEnabled.collectAsStateWithLifecycle()
    val repeat by connection.repeatMode.collectAsStateWithLifecycle()
    val next = remember(windows, index, shuffle, repeat) {
        nextQueueItem(connection.player)?.metadata
    }
    Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
        shape = RoundedCornerShape(24.dp), color = androidx.compose.ui.graphics.Color(0xFF272727), contentColor = androidx.compose.ui.graphics.Color.White) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.ui_next), fontWeight = FontWeight.Bold)
                TextButton(onClick = onOpenQueue, contentPadding = PaddingValues(0.dp)) { Text(stringResource(R.string.ui_open_queue)) }
            }
            if (next != null) Row {
                AsyncImage(next.thumbnailUrl, null, Modifier.size(64.dp), contentScale = ContentScale.Crop)
                Column(Modifier.padding(start = 16.dp)) {
                    Text(next.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
                    Text(next.artists.joinToString(", ") { it.name }, style = MaterialTheme.typography.bodyMedium)
                }
            } else Text(stringResource(R.string.ui_queue_empty))
        }
    }
}

@Composable
internal fun QueueControl(icon: Int, label: String, active: Boolean, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = enabled, modifier = modifier, shape = RoundedCornerShape(14.dp),
        color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(vertical = 14.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(painterResource(icon), label)
            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}
