package com.metrolist.music.utils

import android.net.Uri
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.media3.common.C
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertubex.extraction.ExtractedStream
import com.metrolist.music.models.MediaMetadata
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.Normalizer
import java.util.Locale

data class CanvasVideoSource(
    val uri: Uri,
    val clipStartMs: Long = 0,
    val clipEndMs: Long = C.TIME_END_OF_SOURCE,
    val stream: ExtractedStream? = null,
)

internal fun canvasMiddleClip(durationMs: Long): LongRange {
    val length = durationMs.coerceAtLeast(1).coerceAtMost(8_000)
    val start = ((durationMs - length) / 2).coerceAtLeast(0)
    return start until (start + length)
}

private fun words(text: String): Set<String> =
    Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .split(Regex("[^\\p{L}\\p{N}]+"))
        .filter { it.isNotBlank() }
        .toSet()

internal fun canvasCandidates(song: MediaMetadata, results: List<SongItem>): List<SongItem> {
    val title = words(song.title)
    val artists = words(song.artists.joinToString(" ") { it.name })
    if (title.isEmpty()) return emptyList()
    return results.filter { candidate ->
        val candidateTitle = words(candidate.title)
        val candidateArtists = words(candidate.artists.joinToString(" ") { it.name }) + candidateTitle
        val unwantedVersion = setOf("cover", "live", "remix", "karaoke", "instrumental", "sped", "slowed")
            .any { it in candidateTitle && it !in title }
        !candidate.isEpisode && !unwantedVersion &&
            title.intersect(candidateTitle).size.toDouble() / title.size >= 0.8 &&
            (artists.isEmpty() || artists.intersect(candidateArtists).size.toDouble() / artists.size >= 0.5)
    }.sortedByDescending { candidate ->
        val titleScore = title.intersect(words(candidate.title)).size * 10
        val officialScore = if (candidate.title.contains("official", ignoreCase = true)) 3 else 0
        val candidateDuration = candidate.duration
        val durationPenalty = if (song.duration > 0 && candidateDuration != null) {
            (kotlin.math.abs(song.duration - candidateDuration) / 20).coerceAtMost(10)
        } else 0
        titleScore + officialScore - durationPenalty
    }.distinctBy { it.id }.take(3)
}

internal class AutomaticCanvasResolver(
    private val search: suspend (String) -> List<SongItem> = { query ->
        YouTube.search(query, YouTube.SearchFilter.FILTER_VIDEO).getOrThrow().items.filterIsInstance<SongItem>()
    },
    private val resolveVideo: suspend (String) -> CanvasVideoSource? = { id ->
        InnerTubeXPlayer.videoStreamForCanvas(id)?.let { stream ->
            val duration = stream.mediaMetadata?.durationSeconds?.times(1_000)
            if (duration == null || duration <= 0 || stream.videoUrl == null) null else {
                val clip = canvasMiddleClip(duration)
                CanvasVideoSource(Uri.parse(stream.videoUrl), clip.first, clip.last + 1, stream)
            }
        }
    },
    private val now: () -> Long = System::currentTimeMillis,
) {
    private data class Entry(val source: CanvasVideoSource?, val expiresAt: Long)
    private val cache = LruCache<String, Entry>(30)

    private val mutex = Mutex()

    suspend fun resolve(song: MediaMetadata): CanvasVideoSource? = mutex.withLock {
        if (song.isEpisode) return@withLock null
        cache.get(song.id)?.takeIf { it.expiresAt > now() }?.let { return@withLock it.source }
        val source = withTimeoutOrNull(30_000) {
            val videos = if (song.isVideoSong) emptyList() else try {
                withTimeoutOrNull(10_000) {
                    search("${song.artists.joinToString(" ") { it.name }} ${song.title}")
                }.orEmpty()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                emptyList()
            }
            val ids = (listOfNotNull(song.id.takeIf { song.isVideoSong }) + canvasCandidates(song, videos).map { it.id } + listOfNotNull(song.id.takeIf { it.matches(Regex("[A-Za-z0-9_-]{11}")) }))
                .distinct().take(3)
            for (id in ids) {
                val result = try {
                    withTimeoutOrNull(10_000) { resolveVideo(id) }
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    null
                }
                if (result != null) return@withTimeoutOrNull result
            }
            null
        }
        val expiry = source?.stream?.expiresAt?.toEpochMilliseconds()?.minus(30_000)
            ?: (now() + if (source == null) 60_000 else 5 * 60_000)
        cache.put(song.id, Entry(source, expiry))
        source
    }

}

private val sharedCanvasResolver by lazy { AutomaticCanvasResolver() }

@Composable
internal fun rememberAutomaticCanvas(song: MediaMetadata?, enabled: Boolean): CanvasVideoSource? {
    val resolver = sharedCanvasResolver
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val active = enabled && lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    val result by produceState<Pair<String, CanvasVideoSource?>?>(null, song?.id, active) {
        value = null
        if (song != null && active) {
            val source = withContext(Dispatchers.IO) { resolver.resolve(song) }
            value = song.id to source
        }
    }
    return result?.takeIf { active && it.first == song?.id }?.second
}

@Composable
internal fun rememberSelectedCanvas(selection: CanvasSelection?, enabled: Boolean): CanvasVideoSource? {
    val state by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val active = enabled && state.isAtLeast(Lifecycle.State.RESUMED)
    val source by produceState<Pair<CanvasSelection, CanvasVideoSource?>?>(null, selection, active) {
        value = null
        if (active && selection != null) {
            val resolved = if (selection.uri != null) CanvasVideoSource(selection.uri, selection.startMs, selection.endMs)
            else withContext(Dispatchers.IO) {
                try {
                    resolveSelectedCanvasVideo(requireNotNull(selection.videoId))?.copy(clipStartMs = selection.startMs, clipEndMs = selection.endMs)
                } catch (error: CancellationException) { throw error }
                catch (_: Exception) { null }
            }
            value = selection to resolved
        }
    }
    return source?.takeIf { active && it.first == selection }?.second
}

private val manualCanvasResolver by lazy { AutomaticCanvasResolver(search = { emptyList() }) }

internal suspend fun resolveSelectedCanvasVideo(id: String): CanvasVideoSource? = manualCanvasResolver.resolve(
    MediaMetadata(id, "", emptyList(), -1, musicVideoType = "MUSIC_VIDEO_TYPE_OMV"),
)
