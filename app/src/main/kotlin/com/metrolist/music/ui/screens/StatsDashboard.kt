package com.metrolist.music.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.metrolist.music.R

@Composable
internal fun StatsDashboard(minutes: Long, artist: String?, artistImage: String?, song: String?, songImage: String?,
    covers: List<String>, onArtist: () -> Unit, onSong: () -> Unit, onHistory: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.ui_stats_period), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(stringResource(R.string.ui_minutes), onHistory) {
                    Text(minutes.toString(), fontSize = 48.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Text(stringResource(R.string.history), style = MaterialTheme.typography.bodySmall)
                }
                StatTile(stringResource(R.string.ui_top_artist), onArtist) {
                    Text(artist ?: stringResource(R.string.ui_stats_empty), fontWeight = FontWeight.Bold)
                    AsyncImage(artistImage, null, Modifier.fillMaxWidth().aspectRatio(1f).clip(CircleShape), contentScale = ContentScale.Crop)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(stringResource(R.string.ui_top_songs), onSong) {
                    Row { covers.take(3).forEach { cover -> AsyncImage(cover, null, Modifier.weight(1f).aspectRatio(0.7f), contentScale = ContentScale.Crop) } }
                    Text(stringResource(R.string.ui_stats_personal), style = MaterialTheme.typography.bodySmall)
                }
                StatTile(stringResource(R.string.ui_top_song), onSong) {
                    Text(song ?: stringResource(R.string.ui_stats_empty), fontWeight = FontWeight.Bold)
                    AsyncImage(songImage, null, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                }
            }
        }
    }
}

@Composable
private fun StatTile(title: String, onClick: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(12.dp), color = androidx.compose.ui.graphics.Color(0xFF222222), contentColor = androidx.compose.ui.graphics.Color.White) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, color = androidx.compose.ui.graphics.Color.LightGray, style = MaterialTheme.typography.bodyMedium)
            content()
        }
    }
}
