package com.metrolist.music.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.metrolist.innertube.models.SongItem
import com.metrolist.music.R
import com.metrolist.music.playback.musicVideoId
import com.metrolist.music.utils.joinByBullet
import com.metrolist.music.utils.makeTimeString

@Composable
internal fun VideoSearchResult(video: SongItem, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var useFallback by remember(video.id, video.thumbnail) { mutableStateOf(video.thumbnail.isBlank()) }
    val fallback = remember(video.id) {
        musicVideoId(video.id)?.takeIf { it == video.id }?.let { "https://i.ytimg.com/vi/$it/hqdefault.jpg" }
    }
    ListItem(
        headlineContent = { Text(video.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(joinByBullet(video.artists.joinToString { it.name }, makeTimeString(video.duration?.times(1000L))),
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        },
        leadingContent = {
            Box(Modifier.size(width = 112.dp, height = 63.dp).clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.slow_motion_video), contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(28.dp))
                AsyncImage(model = if (useFallback) fallback else video.thumbnail, contentDescription = null,
                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                    onError = { if (!useFallback) useFallback = true })
            }
        },
        modifier = modifier.clickable(enabled = enabled, onClick = onClick),
    )
}
