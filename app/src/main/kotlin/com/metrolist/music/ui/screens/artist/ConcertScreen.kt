package com.metrolist.music.ui.screens.artist

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.metrolist.music.LocalDatabase
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.R
import com.metrolist.music.utils.ArtistBlocks
import com.metrolist.music.utils.ArtistConcert
import com.metrolist.music.utils.ArtistConcerts
import kotlinx.coroutines.CancellationException
import java.time.LocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConcertScreen(nav: NavController, artistName: String) {
 val database = LocalDatabase.current
 val context = LocalContext.current
 val uri = LocalUriHandler.current
 val blocks by remember { ArtistBlocks.observe(context) }.collectAsStateWithLifecycle(initialValue = emptyMap())
 val top by remember { database.mostPlayedArtists(LocalDateTime.now().minusDays(90), limit = 10) }.collectAsStateWithLifecycle(initialValue = emptyList())
 val artists = top.filterNot { it.artist.id in blocks }.map { it.artist.name }.distinct()
 var selected by remember(artistName) { mutableStateOf(artistName) }
 var results by remember { mutableStateOf<List<ArtistConcert>>(emptyList()) }
 var loading by remember { mutableStateOf(false) }
 var failed by remember { mutableStateOf(false) }
 var retry by remember { mutableIntStateOf(0) }
 LaunchedEffect(artists) { if (selected.isBlank() && artists.isNotEmpty()) selected = artists.first() }
 LaunchedEffect(selected, retry) {
  results = emptyList(); failed = false
  if (selected.isNotBlank()) {
   loading = true
   try { results = ArtistConcerts.find(selected) }
   catch (cancelled: CancellationException) { throw cancelled }
   catch (_: Exception) { failed = true }
   finally { loading = false }
  }
 }
 Column(Modifier.fillMaxSize().windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))) {
  TopAppBar(title = { Text(stringResource(R.string.s80_concerts)) }, navigationIcon = { TextButton(onClick = { nav.navigateUp() }) { Text("‹") } })
  LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
   if (artists.isNotEmpty()) item { Text(stringResource(R.string.s80_from_history)); LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    items(artists) { artist -> FilterChip(selected = selected == artist, onClick = { selected = artist }, label = { Text(artist) }) }
   } }
   item { OutlinedTextField(value = selected, onValueChange = { selected = it }, label = { Text(stringResource(R.string.s80_artist_search)) }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
   if (selected.isNotBlank()) {
    item { Text(selected, style = MaterialTheme.typography.titleLarge) }
    item { Text(stringResource(R.string.s80_musicbrainz_note), style = MaterialTheme.typography.bodySmall) }
    if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
    if (failed) item { Text(stringResource(R.string.s80_concert_error)); TextButton(onClick = { retry++ }) { Text(stringResource(R.string.retry)) } }
    if (!loading && !failed && results.isEmpty()) item { Text(stringResource(R.string.s80_no_concerts)) }
    items(results, key = { it.id }) { concert ->
     OutlinedCard(onClick = { uri.openUri(concert.url) }) {
      Column(Modifier.fillMaxWidth().padding(16.dp)) { Text(concert.title, style = MaterialTheme.typography.titleMedium); Text(concert.date); if (concert.place.isNotBlank()) Text(concert.place) }
     }
    }
    item { TextButton(onClick = { uri.openUri("https://www.bandsintown.com/search?searchTerm=${Uri.encode(selected)}") }) { Text(stringResource(R.string.s80_bandsintown)) } }
    item { TextButton(onClick = { uri.openUri("https://www.eventim.de/search/?searchterm=${Uri.encode(selected)}") }) { Text(stringResource(R.string.s80_eventim)) } }
   }
  }
 }
}
