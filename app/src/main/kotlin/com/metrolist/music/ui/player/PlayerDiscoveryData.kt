package com.metrolist.music.ui.player

import android.util.LruCache
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.WatchEndpoint
import com.metrolist.innertube.pages.ArtistPage
import com.metrolist.music.models.MediaMetadata
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

internal enum class DiscoverKind { ARTIST, SONG }

internal fun filterDiscoverCandidates(
    items: List<SongItem>, excludedIds: Set<String>, hideExplicit: Boolean = false, hideVideo: Boolean = false,
): List<SongItem> = items.asSequence()
    .filter { it.id.isNotBlank() && it.id !in excludedIds && (!hideExplicit || !it.explicit) && (!hideVideo || !it.isVideoSong) }
    .distinctBy { it.id }.take(80).toList()

internal suspend fun loadDiscoverCandidates(
    endpoint: WatchEndpoint, fallback: WatchEndpoint?, excludedIds: Set<String>,
    load: suspend (WatchEndpoint) -> List<SongItem>,
): List<SongItem> {
    var error: Exception? = null
    var receivedResponse = false
    for (request in listOfNotNull(endpoint, fallback).distinct()) {
        try {
            val items = filterDiscoverCandidates(load(request), excludedIds)
            receivedResponse = true
            if (items.isNotEmpty()) return items
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = e }
    }
    if (!receivedResponse) error?.let { throw it }
    return emptyList()
}

internal object PlayerDiscoveryRepository {
    private data class Entry(val songs: List<SongItem>, val time: Long)
    private val cache = LruCache<String, Entry>(16)

    suspend fun load(song: MediaMetadata, artist: ArtistPage?, kind: DiscoverKind, force: Boolean = false): List<SongItem> = withContext(Dispatchers.IO) {
        val artistId = song.artists.firstOrNull()?.id
        val key = "${kind.name}:${song.id}:$artistId"
        cache.get(key)?.takeIf { !force && System.currentTimeMillis() - it.time < 300_000 }?.let { return@withContext it.songs }
        val page = if (kind == DiscoverKind.ARTIST && artist == null && artistId != null) {
            withTimeoutOrNull(12_000) { YouTube.artist(artistId).getOrNull() }
        } else artist
        val artistSeed = if (kind == DiscoverKind.ARTIST) page?.sections?.asSequence()
            ?.flatMap { it.items.asSequence() }?.filterIsInstance<SongItem>()?.firstOrNull()?.id else null
        val seed = artistSeed ?: song.id
        val endpoint = if (kind == DiscoverKind.ARTIST) page?.artist?.radioEndpoint else null
        val items = loadDiscoverCandidates(
            endpoint ?: WatchEndpoint(videoId = seed, playlistId = "RDAMVM$seed"),
            WatchEndpoint(videoId = seed), setOf(song.id, seed),
        ) { request ->
            val next = withTimeoutOrNull(15_000) { YouTube.next(request).getOrThrow() }
                ?: throw java.io.IOException("Recommendation request timed out")
            val related = next.relatedEndpoint?.let {
                withTimeoutOrNull(8_000) { YouTube.related(it).getOrNull()?.songs }
            }.orEmpty()
            // Radio responses often contain only their seed; related songs supply the actual alternatives.
            related + next.items
        }
        if (items.isNotEmpty()) cache.put(key, Entry(items, System.currentTimeMillis()))
        items
    }
}
