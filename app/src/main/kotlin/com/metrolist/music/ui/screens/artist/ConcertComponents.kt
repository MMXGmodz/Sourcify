package com.metrolist.music.ui.screens.artist

import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.metrolist.music.LocalDatabase
import com.metrolist.music.LocalArtistNameAliases
import com.metrolist.music.R
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.utils.ArtistConcert
import com.metrolist.music.utils.ArtistConcerts
import com.metrolist.music.utils.ConcertPreferences
import com.metrolist.music.utils.normalizeArtistName
import com.metrolist.music.utils.rememberPreference
import com.metrolist.music.utils.SongEditStore
import com.metrolist.music.utils.concertProviderArtistName
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

internal data class ConcertLoadState(
    val events: List<ArtistConcert> = emptyList(),
    val loading: Boolean = false,
    val failed: Boolean = false,
)

@Composable
internal fun rememberConcerts(artist: String, enabled: Boolean = true, retry: Int = 0): ConcertLoadState {
    var state by remember(artist, enabled, retry) { mutableStateOf(ConcertLoadState(loading = enabled && artist.isNotBlank())) }
    LaunchedEffect(artist, enabled, retry) {
        if (!enabled || artist.isBlank()) return@LaunchedEffect
        delay(350)
        state = try {
            ConcertLoadState(events = ArtistConcerts.find(artist))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            ConcertLoadState(failed = true)
        }
    }
    return state
}

internal fun ArtistConcert.matchesCity(city: String): Boolean =
    city.isNotBlank() && this.city.isNotBlank() && normalizeArtistName(this.city) == normalizeArtistName(city)

internal fun artistHeaderCollapseProgress(scrollOffset: Int, headerHeight: Int, fadeDistance: Int): Float {
    if (headerHeight <= 0) return 0f
    return ((scrollOffset - headerHeight + fadeDistance).toFloat() / fadeDistance.coerceAtLeast(1)).coerceIn(0f, 1f)
}

@Composable
internal fun ArtistContentTabs(eventsSelected: Boolean, onSelectEvents: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        listOf(false, true).forEach { events ->
            val selected = eventsSelected == events
            Column(Modifier.selectable(selected = selected, role = androidx.compose.ui.semantics.Role.Tab,
                onClick = { onSelectEvents(events) }).padding(top = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(if (events) R.string.s83_events_tab else R.string.s83_music_tab),
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                    color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Box(Modifier.width(48.dp).height(3.dp).background(if (selected) Color(0xFF1ED760) else Color.Transparent))
            }
        }
    }
}

@Composable
internal fun ArtistUpcomingConcertCard(artistName: String, onAllEvents: () -> Unit) {
    val state = rememberConcerts(artistName)
    val concert = state.events.firstOrNull() ?: return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val saved by rememberPreference(ConcertPreferences.savedKey, emptySet<String>())
    ArtistUpcomingConcertCardContent(concert, concert.id in saved, onAllEvents) {
        scope.launch { ConcertPreferences.setSaved(context, concert.id, concert.id !in saved) }
    }
}

@Composable
internal fun ArtistUpcomingConcertCardContent(concert: ArtistConcert, saved: Boolean, onAllEvents: () -> Unit, onToggleSaved: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(16.dp), shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        ConcertCardHeading(false, onAllEvents, overImage = false)
        Column(Modifier.padding(horizontal = 18.dp)) {
            Text(concert.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            ConcertRow(concert, saved, onToggleSaved)
        }
    }
}

@Composable
internal fun PlayerConcertCard(
    song: MediaMetadata,
    onAllEvents: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val context = LocalContext.current
    val edits = remember(context) { SongEditStore.get(context) }
    val editRevision by edits.revision.collectAsStateWithLifecycle()
    val sourceSong = remember(song, editRevision) { edits.original(song) }
    val primaryArtist = sourceSong.artists.firstOrNull()
    val database = LocalDatabase.current
    val cachedArtist by remember(primaryArtist?.id) { database.artist(primaryArtist?.id.orEmpty()) }
        .collectAsStateWithLifecycle(initialValue = null)
    val providerArtist = cachedArtist?.takeIf { it.id == primaryArtist?.id }
    val aliases = LocalArtistNameAliases.current
    val artist = remember(primaryArtist, providerArtist?.artist?.cachedPageJson, aliases) {
        concertProviderArtistName(primaryArtist?.id, primaryArtist?.name.orEmpty(), providerArtist?.artist, aliases)
    }
    val state = rememberConcerts(artist, enabled)
    val city by rememberPreference(ConcertPreferences.cityKey, "")
    val concert = state.events.firstOrNull { it.matchesCity(city) } ?: state.events.firstOrNull()
    if (enabled && !state.failed && concert != null) {
        PlayerConcertCardContent(artist, concert, concert.posterUrl,
            concert.matchesCity(city), { onAllEvents(artist) }, modifier)
    }
}

@Composable
internal fun PlayerConcertCardContent(
    artistName: String,
    concert: ArtistConcert,
    posterUrl: String?,
    nearby: Boolean,
    onAllEvents: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uri = LocalUriHandler.current
    Card(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Box(Modifier.fillMaxWidth().height(240.dp)) {
            ConcertArtwork(concert, Modifier.fillMaxSize().clickable { uri.openUri(concert.url) })
            if (!posterUrl.isNullOrBlank()) AsyncImage(model = posterUrl, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clickable { uri.openUri(concert.url) })
            Box(Modifier.fillMaxWidth().height(110.dp).background(Brush.verticalGradient(
                listOf(Color.Black.copy(alpha = 0.65f), Color.Transparent))))
            ConcertCardHeading(nearby, onAllEvents, overImage = true)
        }
        Column(Modifier.fillMaxWidth().clickable { uri.openUri(concert.url) }.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(artistName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(concert.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(concertDateDetails(concert), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            val venue = listOf(concert.place, concert.city).filter(String::isNotBlank).distinct().joinToString(" · ")
            if (venue.isNotBlank()) Text(venue, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ConcertCardHeading(nearby: Boolean, onAllEvents: () -> Unit, overImage: Boolean) {
    Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(if (nearby) R.string.s83_live_event_nearby else R.string.s83_live_event),
            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f),
            color = if (overImage) Color.White else LocalContentColor.current)
        TextButton(onClick = onAllEvents, colors = ButtonDefaults.textButtonColors(
            contentColor = if (overImage) Color.White else MaterialTheme.colorScheme.primary)) {
            Text(stringResource(R.string.s83_all_events), maxLines = 1)
        }
    }
}

internal fun concertDateDetails(concert: ArtistConcert): String {
    val date = concert.day?.format(DateTimeFormatter.ofPattern("EEE, d. MMM yyyy", Locale.getDefault())) ?: concert.date
    return listOf(date, concert.time).filter(String::isNotBlank).joinToString(" · ")
}

@Composable
internal fun ConcertRow(concert: ArtistConcert, saved: Boolean, onToggleSaved: () -> Unit, modifier: Modifier = Modifier) {
    val uri = LocalUriHandler.current
    Row(modifier.fillMaxWidth().clickable { uri.openUri(concert.url) }.padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        val date = concert.day
        Column(Modifier.width(54.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            val month = date?.format(DateTimeFormatter.ofPattern("MMM", Locale.getDefault()))
                ?: runCatching { YearMonth.parse(concert.date).format(DateTimeFormatter.ofPattern("MMM", Locale.getDefault())) }.getOrNull()
            Text(month?.uppercase(Locale.getDefault()) ?: concert.date.take(4),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(date?.dayOfMonth?.toString() ?: "—", style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold)

        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(concert.city.ifBlank { concert.title }, style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val dayAndTime = listOfNotNull(date?.format(DateTimeFormatter.ofPattern("EEEE", Locale.getDefault())),
                concert.time.takeIf(String::isNotBlank), concert.place.takeIf(String::isNotBlank)).joinToString(" · ")
            if (dayAndTime.isNotBlank()) Text(dayAndTime, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)

        }
        IconToggleButton(checked = saved, onCheckedChange = { onToggleSaved() },
            modifier = Modifier.size(48.dp).clip(CircleShape)) {
            Icon(painterResource(if (saved) R.drawable.check else R.drawable.add_circle),
                stringResource(if (saved) R.string.s83_unsave_event else R.string.s83_save_event),
                tint = if (saved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun ArtistEventsSection(artistName: String, onAllEvents: () -> Unit) {
    var retry by remember { mutableIntStateOf(0) }
    val state = rememberConcerts(artistName, retry = retry)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val saved by rememberPreference(ConcertPreferences.savedKey, emptySet<String>())
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (state.failed) {
            Text(stringResource(R.string.s80_concert_error))
            TextButton(onClick = { retry++ }) { Text(stringResource(R.string.retry)) }
        }
        if (!state.loading && !state.failed && state.events.isEmpty()) Text(stringResource(R.string.s80_no_concerts))
        state.events.take(4).forEach { concert ->
            ConcertRow(concert, concert.id in saved, {
                scope.launch { ConcertPreferences.setSaved(context, concert.id, concert.id !in saved) }
            })
        }
        Text(stringResource(R.string.s80_musicbrainz_note), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 12.dp))
        OutlinedButton(onClick = onAllEvents, enabled = artistName.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.s83_view_all_events))
            Spacer(Modifier.width(8.dp))
            Icon(painterResource(R.drawable.arrow_forward), contentDescription = null, modifier = Modifier.size(18.dp))
        }
    }
}
