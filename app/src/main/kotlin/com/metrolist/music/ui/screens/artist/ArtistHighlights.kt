package com.metrolist.music.ui.screens.artist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.metrolist.innertube.models.*
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.db.entities.Song
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.models.toMediaMetadata
import com.metrolist.music.playback.queues.ListQueue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ArtistHighlights(name: String, liked: List<Song>, latest: YTItem?, guest: Boolean, nav: NavController) {
 val connection = LocalPlayerConnection.current ?: return
 var showLiked by remember { mutableStateOf(false) }
 ArtistHighlightRows(name, liked.size, latest?.title, latest?.thumbnail, latest is SongItem, latest is AlbumItem || !guest,
  onLatest = { when (latest) {
   is AlbumItem -> nav.navigate("album/${latest.id}")
   is SongItem -> connection.playQueue(ListQueue(name, listOf(latest.toMediaItem())))
   else -> Unit
  } }, onLiked = { showLiked = true })
 if (showLiked) ModalBottomSheet(onDismissRequest = { showLiked = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
  Text(stringResource(R.string.s80_your_favorites), Modifier.padding(24.dp), style = MaterialTheme.typography.titleLarge)
  LazyColumn(Modifier.fillMaxWidth().heightIn(max = 500.dp).navigationBarsPadding()) {
   itemsIndexed(liked, key = { _, it -> it.id }) { index, song ->
    ListItem(modifier = Modifier.clickable(enabled = !guest) {
     connection.playQueue(ListQueue(name, liked.map { it.toMediaItem() }, startIndex = index)); showLiked = false
    }, headlineContent = { Text(song.song.title) }, supportingContent = { Text(name) },
     leadingContent = { AsyncImage(song.song.thumbnailUrl, null, Modifier.size(48.dp)) })
   }
  }
 }
}

@Composable
internal fun ArtistHighlightRows(name: String, likedCount: Int, latestTitle: String?, latestThumbnail: String?,
 latestIsSong: Boolean, latestEnabled: Boolean, onLatest: () -> Unit, onLiked: () -> Unit) {
 Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
  if (latestTitle != null) Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant) {
   ListItem(modifier = Modifier.clickable(enabled = latestEnabled, onClick = onLatest),
    headlineContent = { Text(stringResource(if (latestIsSong) R.string.s80_new_song else R.string.s80_latest_release)) },
    supportingContent = { Text(latestTitle) },
    leadingContent = { AsyncImage(latestThumbnail, null, Modifier.size(48.dp), contentScale = ContentScale.Crop) },
    trailingContent = { Text("›") })
  }
  if (likedCount > 0) ListItem(modifier = Modifier.clickable(onClick = onLiked),
   headlineContent = { Text(stringResource(R.string.s80_your_favorites)) },
   supportingContent = { Text(stringResource(R.string.s80_liked_count, likedCount, name)) },
   leadingContent = { Icon(painterResource(R.drawable.favorite), null, tint = androidx.compose.ui.graphics.Color(0xFF1ED760)) },
   trailingContent = { Text("›") })
 }
}
