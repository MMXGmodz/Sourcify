package com.metrolist.music.utils

import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.prepareGet
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal data class PlaylistImportCollection(
    val title: String,
    val tracks: List<PlaylistImportTrack>,
    val youtubeSongs: List<SongItem>? = null,
    val skippedCount: Int = 0,
)

internal data class PlaylistImportMatch(
    val source: PlaylistImportTrack,
    val candidates: List<SongItem>,
    val selectedId: String?,
)

internal object ExternalPlaylistImporter {
    private val client = HttpClient {
        expectSuccess = true
        install(HttpTimeout) { requestTimeoutMillis = 15_000 }
    }

    suspend fun loadLink(link: String, spotify: com.metrolist.music.spotify.SpotifyPlaylistClient? = null): PlaylistImportCollection = withTimeout(60_000) {
        val source = parsePlaylistImportLink(link)
        if (source.provider == "spotify") {
            return@withTimeout (spotify ?: throw com.metrolist.music.spotify.SpotifyImportException(
                com.metrolist.music.spotify.SpotifyImportFailure.AUTH_REQUIRED)).load(source.id)
        }
        if (source.provider == "youtube") {
            val page = YouTube.playlist(source.id).getOrThrow()
            val songs = page.songs.toMutableList()
            var continuation = page.songsContinuation
            val seen = mutableSetOf<String>()
            while (continuation != null) {
                if (songs.size >= PLAYLIST_IMPORT_MAX_TRACKS || !seen.add(continuation)) throw PlaylistImportException(PlaylistImportError.LIMIT)
                val next = YouTube.playlistContinuation(continuation).getOrThrow()
                songs.addAll(next.songs)
                continuation = next.continuation
            }
            validateSize(songs.size)
            return@withTimeout PlaylistImportCollection(page.playlist.title, songs.map { it.importTrack() }, songs)
        }
        val head = deezerJson("https://api.deezer.com/playlist/${source.id}")
        val title = head["title"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val tracks = mutableListOf<PlaylistImportTrack>()
        var index = 0
        do {
            val page = deezerJson("https://api.deezer.com/playlist/${source.id}/tracks", index)
            val total = page["total"]?.jsonPrimitive?.intOrNull
            if (total != null && total > PLAYLIST_IMPORT_MAX_TRACKS) throw PlaylistImportException(PlaylistImportError.LIMIT)
            val rows = page["data"]?.jsonArray ?: throw PlaylistImportException(PlaylistImportError.REQUEST)
            rows.forEach { value ->
                val row = value.jsonObject
                val trackTitle = row["title"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val artist = row["artist"]?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull.orEmpty()
                if (trackTitle.isBlank() || artist.isBlank()) throw PlaylistImportException(PlaylistImportError.REQUEST)
                tracks.add(PlaylistImportTrack(trackTitle, artist, row["duration"]?.jsonPrimitive?.intOrNull))
            }
            if (tracks.size > PLAYLIST_IMPORT_MAX_TRACKS) throw PlaylistImportException(PlaylistImportError.LIMIT)
            index += rows.size
            val more = page["next"]?.jsonPrimitive?.contentOrNull?.isNotBlank() == true
            if (more && (rows.isEmpty() || index >= PLAYLIST_IMPORT_MAX_TRACKS)) throw PlaylistImportException(PlaylistImportError.LIMIT)
        } while (more)
        validateSize(tracks.size)
        PlaylistImportCollection(title, tracks)
    }

    suspend fun match(collection: PlaylistImportCollection, onProgress: (Int, Int) -> Unit): List<PlaylistImportMatch> {
        validateSize(collection.tracks.size)
        val lookups = mutableMapOf<String, List<SongItem>>()
        return collection.tracks.mapIndexed { index, source ->
            currentCoroutineContext().ensureActive()
            onProgress(index + 1, collection.tracks.size)
            val direct = collection.youtubeSongs?.getOrNull(index)
            if (direct != null) return@mapIndexed PlaylistImportMatch(source, listOf(direct), direct.id)
            val query = playlistImportSearchQuery(source)
            val found = lookups[query] ?: run {
                if (index > 0) delay(250)
                val result = try {
                    kotlinx.coroutines.withTimeoutOrNull(15_000) {
                        val songs = YouTube.search(query, YouTube.SearchFilter.FILTER_SONG).getOrThrow()
                            .items.filterIsInstance<SongItem>()
                        if (songs.isNotEmpty()) songs else YouTube.search(query, YouTube.SearchFilter.FILTER_VIDEO)
                            .getOrThrow().items.filterIsInstance<SongItem>()
                    }.orEmpty()
                } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch (_: Exception) { emptyList() }
                lookups[query] = result
                result
            }
            val candidates = found.distinctBy { it.id }.filterNot { it.isEpisode }
                .map { it to playlistImportMatchScore(source, it.importTrack()) }
                .filter { it.second >= 0.55 }.sortedByDescending { it.second }.take(3)
            val best = candidates.firstOrNull()
            val confident = best != null && best.second >= 0.95 &&
                (candidates.getOrNull(1)?.second?.let { best.second - it >= 0.1 } ?: true)
            PlaylistImportMatch(source, candidates.map { it.first }, best?.first?.id?.takeIf { confident })
        }
    }

    private suspend fun deezerJson(url: String, index: Int? = null): JsonObject =
        client.prepareGet(url) {
            if (index != null) { parameter("limit", 100); parameter("index", index) }
        }.execute { response ->
            val channel = response.bodyAsChannel()
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            try {
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val read = channel.readAvailable(buffer)
                    if (read == -1) break
                    if (output.size() + read > PLAYLIST_IMPORT_MAX_BYTES) throw PlaylistImportException(PlaylistImportError.LIMIT)
                    output.write(buffer, 0, read)
                }
            } finally {
                channel.cancel(null)
            }
            Json.parseToJsonElement(output.toString(Charsets.UTF_8.name())).jsonObject.also {
                if (it["error"] != null) throw PlaylistImportException(PlaylistImportError.REQUEST)
            }
        }

    private fun validateSize(size: Int) {
        if (size > PLAYLIST_IMPORT_MAX_TRACKS) throw PlaylistImportException(PlaylistImportError.LIMIT)
        if (size == 0) throw PlaylistImportException(PlaylistImportError.EMPTY)
    }

    private fun SongItem.importTrack() = PlaylistImportTrack(title, artists.joinToString(" ") { it.name }, duration, album?.name)
}
