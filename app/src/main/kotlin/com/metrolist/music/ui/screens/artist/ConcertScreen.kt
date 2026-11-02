package com.metrolist.music.ui.screens.artist

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.metrolist.music.LocalDatabase
import com.metrolist.music.LocalArtistNameAliases
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.R
import com.metrolist.music.utils.ArtistBlocks
import com.metrolist.music.utils.ConcertPreferences
import com.metrolist.music.utils.rememberPreference
import com.metrolist.music.utils.concertProviderArtistName
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConcertScreen(nav: NavController, artistName: String) {
    val database = LocalDatabase.current
    val context = LocalContext.current
    val uri = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val blocks by remember { ArtistBlocks.observe(context) }.collectAsStateWithLifecycle(initialValue = emptyMap())
    val top by remember { database.mostPlayedArtists(LocalDateTime.now().minusDays(90), limit = 10) }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val aliases = LocalArtistNameAliases.current
    val artists = remember(top, blocks, aliases) {
        top.filterNot { it.artist.id in blocks }.mapNotNull { item ->
            val artist = item.artist
            concertProviderArtistName(artist.id, artist.name, artist, aliases).takeIf(String::isNotBlank)?.let { artist.copy(name = it) }
        }.distinctBy { it.id }
    }
    var selected by rememberSaveable(artistName) { mutableStateOf(artistName) }
    var retry by remember { mutableIntStateOf(0) }
    val state = rememberConcerts(selected, retry = retry)
    val cachedThumbnail by produceState<String?>(initialValue = null, selected) {
        value = null
        value = if (selected.isBlank()) null else withContext(Dispatchers.IO) { database.artistByName(selected)?.thumbnailUrl }
    }
    var city by rememberPreference(ConcertPreferences.cityKey, "")
    val saved by rememberPreference(ConcertPreferences.savedKey, emptySet<String>())
    var savedOnly by rememberSaveable { mutableStateOf(false) }
    var editCity by remember { mutableStateOf(false) }
    var cityDraft by remember { mutableStateOf("") }
    LaunchedEffect(artists) { if (selected.isBlank() && artists.isNotEmpty()) selected = artists.first().name }
    val visibleEvents = state.events.filter { !savedOnly || it.id in saved }
    val localEvents = visibleEvents.filter { it.matchesCity(city) }
    val otherEvents = visibleEvents.filterNot { it.matchesCity(city) }
    if (editCity) AlertDialog(
        onDismissRequest = { editCity = false },
        title = { Text(stringResource(R.string.s83_choose_location)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.s83_manual_location_note))
            OutlinedTextField(cityDraft, { cityDraft = it }, label = { Text(stringResource(R.string.s83_concert_city)) }, singleLine = true)
        } },
        confirmButton = { TextButton(onClick = { city = cityDraft.trim(); editCity = false }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = { editCity = false }) { Text(stringResource(R.string.cancel)) } },
    )
    Column(Modifier.fillMaxSize().windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))) {
        TopAppBar(title = { Text(stringResource(R.string.s83_events_tab)) }, navigationIcon = {
            IconButton(onClick = { nav.navigateUp() }) { Icon(painterResource(R.drawable.arrow_back), contentDescription = stringResource(R.string.back)) }
        })
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (selected.isNotBlank()) item {
                val thumbnail = artists.firstOrNull { it.name == selected }?.thumbnailUrl ?: cachedThumbnail
                ConcertArtistHeader(selected, thumbnail)
            }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { cityDraft = city; editCity = true }, modifier = Modifier.weight(1f)) {
                        Icon(painterResource(R.drawable.location_on), null, Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(city.ifBlank { stringResource(R.string.s83_choose_location) })
                    }
                    FilterChip(selected = savedOnly, onClick = { savedOnly = !savedOnly }, label = { Text(stringResource(R.string.s83_saved_events)) },
                        leadingIcon = { Icon(painterResource(if (savedOnly) R.drawable.check else R.drawable.add_circle), null, Modifier.size(18.dp)) })
                }
                if (city.isBlank()) Text(stringResource(R.string.s83_manual_location_note), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (artists.isNotEmpty() && artistName.isBlank()) item {
                Text(stringResource(R.string.s80_from_history), style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(artists, key = { it.id }) { artist ->
                        FilterChip(selected = selected == artist.name, onClick = { selected = artist.name }, label = { Text(artist.name) })
                    }
                }
            }
            if (artistName.isBlank()) item {
                OutlinedTextField(selected, { selected = it }, label = { Text(stringResource(R.string.s80_artist_search)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
            if (selected.isNotBlank()) {
                if (state.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                if (state.failed) item {
                    Text(stringResource(R.string.s80_concert_error))
                    TextButton(onClick = { retry++ }) { Text(stringResource(R.string.retry)) }
                }
                if (!state.loading && !state.failed) {
                    if (city.isNotBlank()) item {
                        Text(stringResource(R.string.s83_events_in_city, city), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        if (localEvents.isEmpty()) Text(stringResource(R.string.s83_no_city_events), style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
                    }
                    items(localEvents, key = { it.id }) { concert ->
                        ConcertRow(concert, concert.id in saved, { scope.launch { ConcertPreferences.setSaved(context, concert.id, concert.id !in saved) } })
                    }
                    if (city.isNotBlank() && otherEvents.isNotEmpty()) item {
                        Text(stringResource(R.string.s83_other_locations), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                    items(otherEvents, key = { it.id }) { concert ->
                        ConcertRow(concert, concert.id in saved, { scope.launch { ConcertPreferences.setSaved(context, concert.id, concert.id !in saved) } })
                    }
                    if (visibleEvents.isEmpty()) item {
                        Text(stringResource(if (savedOnly) R.string.s83_no_saved_events else R.string.s80_no_concerts),
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                item {
                    Text(stringResource(R.string.s80_musicbrainz_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                item {
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(contentColor = Color.White)) {
                        Column(Modifier.background(Brush.linearGradient(listOf(Color(0xFF783DAF), Color(0xFF42307D)))).padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.s83_discover_events), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.s83_discover_events_note), style = MaterialTheme.typography.bodyMedium)
                            Button(onClick = { uri.openUri("https://www.bandsintown.com/search?searchTerm=${Uri.encode(selected)}") }, modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.18f), contentColor = Color.White)) {
                                Text(stringResource(R.string.s80_bandsintown))
                            }
                            TextButton(onClick = { uri.openUri("https://www.eventim.de/search/?searchterm=${Uri.encode(selected)}") }, modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.textButtonColors(contentColor = Color.White)) {
                                Text(stringResource(R.string.s80_eventim))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ConcertArtistHeader(artistName: String, thumbnailUrl: String?) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        if (!thumbnailUrl.isNullOrBlank()) AsyncImage(thumbnailUrl, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.size(64.dp).clip(CircleShape))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(artistName, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.s83_events_tab), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
