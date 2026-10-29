package com.metrolist.music.ui.player

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.metrolist.innertube.models.SongItem
import com.metrolist.music.*
import com.metrolist.music.R
import com.metrolist.music.db.entities.ArtistEntity
import com.metrolist.music.models.MediaMetadata
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

private val ContextCardColor = Color(0xFF282828)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerContextCards(song: MediaMetadata, onNavigate: (String) -> Unit) {
    if (com.metrolist.music.utils.LocalAudioStore.isLocalId(song.id)) return
    val context = LocalContext.current
    val database = LocalDatabase.current
    if (LocalPlayerConnection.current == null) return
    val sync = LocalSyncUtils.current
    val scope = rememberCoroutineScope()
    var attempt by remember(song.id) { mutableIntStateOf(0) }
    var loading by remember(song.id) { mutableStateOf(true) }
    val result by produceState<Pair<String, PlayerContextData>?>(null, song.id, attempt) {
        value = null; loading = true
        try { value = song.id to PlayerContextRepository.load(song, force = attempt > 0) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { value = song.id to PlayerContextData(null, null) }
        finally { loading = false }
    }
    val data = result?.takeIf { it.first == song.id }?.second
    val artist = data?.artist
    val artistId = song.artists.firstOrNull()?.id
    val saved by remember(artistId) { if (artistId == null) kotlinx.coroutines.flow.flowOf(null) else database.artist(artistId) }
        .collectAsStateWithLifecycle(initialValue = null)
    val followed = saved?.artist?.bookmarkedAt != null
    var following by remember(song.id) { mutableStateOf(false) }
    var showBiography by rememberSaveable(song.id) { mutableStateOf(false) }
    var showCredits by rememberSaveable(song.id) { mutableStateOf(false) }
    var discovery by remember(song.id) { mutableStateOf<DiscoverKind?>(null) }
    val artistName = artist?.artist?.title ?: song.artists.firstOrNull()?.name
    val credits = remember(data?.media?.description) { parseSongCredits(data?.media?.description) }
    val follow: () -> Unit = {
        if (!following && artistId != null && artist != null) scope.launch {
            following = true
            try {
                withContext(Dispatchers.IO) {
                    database.withTransaction {
                        val existing = this.artist(artistId).first()?.artist
                        val old = existing ?: ArtistEntity(artistId, artist.artist.title,
                            thumbnailUrl = artist.artist.thumbnail, channelId = artist.artist.channelId)
                        val updated = old.localToggleLike()
                        if (existing == null) insert(updated) else update(updated)
                        sync.subscribeChannel(artist.artist.channelId ?: artistId, updated.bookmarkedAt != null)
                    }
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { Toast.makeText(context, R.string.player_context_error, Toast.LENGTH_LONG).show() }
            finally { following = false }
        }
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (artistName != null) ArtistAboutCard(
            name = artistName, image = artist?.artist?.thumbnail, description = artist?.description,
            audience = artist?.monthlyListenerCount ?: artist?.subscriberCountText,
            followed = followed, canFollow = artistId != null && artist != null && !following,
            onFollow = follow, onMore = { showBiography = true },
            onArtist = { artistId?.let { onNavigate("artist/${Uri.encode(it)}") } },
        )
        if (artistName != null) {
            val sample = artist?.sections?.flatMap { it.items }?.filterIsInstance<SongItem>()?.firstOrNull()
            DiscoverArtistCard(
                artistName, artist?.artist?.thumbnail, sample?.thumbnail ?: artist?.artist?.thumbnail, song.thumbnailUrl,
                canArtist = artistId != null, canRadio = artistId != null || artist?.artist?.radioEndpoint != null,
                onArtist = { artistId?.let { onNavigate("artist/${Uri.encode(it)}") } },
                onArtistRadio = { discovery = DiscoverKind.ARTIST },
                onSongRadio = { discovery = DiscoverKind.SONG },
                similarSongTitle = song.title,
            )
        }
        SongCreditsPreview(song, credits, onAll = { showCredits = true }, followed = followed, canFollow = artistId != null && artist != null && !following, onFollow = follow)
        if (!loading && ((artistId != null && data?.artist == null) || data?.media == null)) TextButton(onClick = { attempt++ }) { Text(stringResource(R.string.player_context_retry)) }
    }
    discovery?.let { PlayerDiscoverSheet(song, artist, it, onDismiss = { discovery = null }) }
    if (showBiography && artistName != null) AlertDialog(onDismissRequest = { showBiography = false },
        title = { Text(artistName) },
        text = { Text(artist?.description ?: stringResource(R.string.player_context_missing), modifier = Modifier.verticalScroll(rememberScrollState())) },
        confirmButton = { TextButton(onClick = { showBiography = false }) { Text(stringResource(android.R.string.ok)) } })
    if (showCredits) ModalBottomSheet(onDismissRequest = { showCredits = false },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Color(0xFF202020), contentColor = Color.White) {
        var creditsAttempt by remember(song.id) { mutableIntStateOf(0) }
        val creditsKey = remember(song.title, song.artists, song.duration) {
            MusicBrainzSongKey(song.title, song.artists.map { it.name }, song.duration)
        }
        val needsMoreCredits = !song.isEpisode && CreditGroup.entries.any { group -> credits.entries.none { it.group == group } }
        val extraCredits by produceState<MusicBrainzCreditsResult?>(null, creditsKey, needsMoreCredits, creditsAttempt) {
            value = null
            if (needsMoreCredits) value = MusicBrainzCreditsRepository.shared.load(creditsKey, force = creditsAttempt > 0)
        }
        val completeCredits = remember(credits, extraCredits) { mergeMusicBrainzCredits(credits, extraCredits) }
        fun openSource(url: String) {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                .onFailure { Toast.makeText(context, R.string.player_context_error, Toast.LENGTH_LONG).show() }
        }
        SongCreditsContent(song, completeCredits, followed, artistId != null && artist != null && !following, follow,
            onSource = { openSource("https://music.youtube.com/watch?v=${Uri.encode(song.id)}") },
            musicBrainz = extraCredits, loadingMusicBrainz = needsMoreCredits && extraCredits == null,
            onRetryMusicBrainz = { creditsAttempt++ },
            onMusicBrainzSource = { (extraCredits as? MusicBrainzCreditsResult.Found)?.sourceUrl?.let(::openSource) })
    }
}

@Composable
internal fun ArtistAboutCard(name: String, image: String?, description: String?, audience: String?, followed: Boolean,
    canFollow: Boolean, onFollow: () -> Unit, onMore: () -> Unit, onArtist: () -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = ContextCardColor, contentColor = Color.White) {
        Column {
            if (image != null) Box(Modifier.fillMaxWidth().aspectRatio(1.5f).clickable(onClick = onArtist)) {
                AsyncImage(image, contentDescription = name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.4f), Color.Transparent))))
                Text(stringResource(R.string.player_about_artist), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(16.dp))
            } else Text(stringResource(R.string.player_about_artist), fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        audience?.let { Text(it, color = Color.White.copy(alpha = 0.65f)) }
                    }
                    if (canFollow || followed) OutlinedButton(border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.45f)), onClick = onFollow, enabled = canFollow) {
                        Text(stringResource(if (followed) R.string.player_following else R.string.player_follow), color = Color.White)
                    }
                }
                description?.takeIf { it.isNotBlank() }?.let {
                    Text(it, maxLines = 3, overflow = TextOverflow.Ellipsis, color = Color.White.copy(alpha = 0.75f))
                    TextButton(onClick = onMore, contentPadding = PaddingValues(0.dp)) { Text(stringResource(R.string.player_more), color = Color.White, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable
internal fun DiscoverArtistCard(name: String, artistImage: String?, radioImage: String?, songImage: String?, canArtist: Boolean,
    canRadio: Boolean, onArtist: () -> Unit, onArtistRadio: () -> Unit, onSongRadio: () -> Unit, similarSongTitle: String = "") {
    Surface(shape = RoundedCornerShape(20.dp), color = ContextCardColor, contentColor = Color.White) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.player_discover_artist, name), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (canArtist) DiscoverTile(stringResource(R.string.player_songs_by, name), artistImage, Modifier.weight(1f), onArtist)
                if (canRadio) DiscoverTile(stringResource(R.string.player_similar_artist, name), radioImage, Modifier.weight(1f), onArtistRadio)
                DiscoverTile(if (similarSongTitle.isBlank()) stringResource(R.string.player_similar_song) else stringResource(R.string.player_similar_artist, similarSongTitle), songImage, Modifier.weight(1f), onSongRadio)
            }
        }
    }
}

@Composable
private fun DiscoverTile(title: String, image: String?, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.aspectRatio(0.65f).clip(RoundedCornerShape(8.dp)).background(Color(0xFF414141)).clickable(onClick = onClick)) {
        AsyncImage(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f)))))
        Text(title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 4, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomStart).padding(8.dp))
    }
}

@Composable
internal fun SongCreditsPreview(song: MediaMetadata, credits: SongCredits, onAll: () -> Unit, followed: Boolean = false, canFollow: Boolean = false, onFollow: () -> Unit = {}) {
    Surface(shape = RoundedCornerShape(20.dp), color = ContextCardColor, contentColor = Color.White) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.player_song_info), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = onAll) { Text(stringResource(R.string.player_show_all), fontWeight = FontWeight.Bold) }
            }
            val rows = previewCreditRows(song.artists.map { it.name }, credits, stringResource(R.string.player_main_artist))
            rows.take(3).forEachIndexed { index, row ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { CreditRow(row.first, row.second) }
                    if (index == 0 && canFollow) OutlinedButton(border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.45f)), onClick = onFollow) {
                        Text(stringResource(if (followed) R.string.player_following else R.string.player_follow), color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun CreditRow(name: String, role: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(name, style = MaterialTheme.typography.titleLarge, fontSize = 20.sp, fontWeight = FontWeight.Normal, color = Color.White)
        Text(role, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.6f))
    }
}

@Composable
internal fun SongCreditsContent(song: MediaMetadata, credits: SongCredits, followed: Boolean, canFollow: Boolean,
    onFollow: () -> Unit, onSource: () -> Unit, musicBrainz: MusicBrainzCreditsResult? = null,
    loadingMusicBrainz: Boolean = false, onRetryMusicBrainz: () -> Unit = {}, onMusicBrainzSource: () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(song.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
            Text(song.artists.joinToString { it.name }, color = Color.White.copy(alpha = 0.65f))
        }
        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
        Text(stringResource(R.string.player_credits_artists), style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.Bold)
        song.artists.forEachIndexed { index, artist ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { CreditRow(artist.name, stringResource(R.string.player_main_artist)) }
                if (index == 0 && canFollow) OutlinedButton(border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.45f)), onClick = onFollow) { Text(stringResource(if (followed) R.string.player_following else R.string.player_follow), color = Color.White) }
            }
        }
        listOf(CreditGroup.COMPOSITION to R.string.player_credits_composition, CreditGroup.PRODUCTION to R.string.player_credits_production).forEach { (group, label) ->
            Text(stringResource(label), style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.Bold)
            val entries = credits.entries.filter { it.group == group }
            if (entries.isEmpty()) Text(stringResource(R.string.player_credits_missing), color = Color.White.copy(alpha = 0.6f))
            entries.forEach { CreditRow(it.name, it.role) }
        }
        Text(stringResource(R.string.player_credits_sources), style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.Bold)
        Text("YouTube Music", color = Color.White)
        credits.sources.forEach { Text(it, color = Color.White.copy(alpha = 0.7f)) }
        when {
            loadingMusicBrainz -> {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(stringResource(R.string.musicbrainz_credits_loading), color = Color.White.copy(alpha = 0.7f))
            }
            musicBrainz is MusicBrainzCreditsResult.Found -> {
                TextButton(onClick = onMusicBrainzSource) { Text(stringResource(R.string.musicbrainz_credits_source)) }
                if (musicBrainz.credits.entries.isEmpty()) Text(stringResource(R.string.musicbrainz_credits_empty), color = Color.White.copy(alpha = 0.7f))
            }
            musicBrainz != null -> {
                Text(stringResource(if (musicBrainz == MusicBrainzCreditsResult.Error) R.string.musicbrainz_credits_error else R.string.musicbrainz_credits_unavailable), color = Color.White.copy(alpha = 0.7f))
                TextButton(onClick = onRetryMusicBrainz) { Text(stringResource(R.string.musicbrainz_credits_retry)) }
            }
        }
        OutlinedButton(border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.45f)), onClick = onSource, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text(stringResource(R.string.player_open_source), color = Color.White) }
    }
}
