package com.metrolist.music.playback

import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem
import com.metrolist.music.models.MediaMetadata
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.Normalizer
import java.util.Locale

/** Video mode is deliberately scoped to one queue occurrence and is never persisted. */
enum class MusicVideoMode { AUDIO, LOADING, VIDEO }

data class MusicVideoState(
    val mode: MusicVideoMode = MusicVideoMode.AUDIO,
    val currentSongId: String? = null,
    val reason: String? = null,
)

internal class MusicVideoSession {
    private var generation = 0L
    private val mutableState = MutableStateFlow(MusicVideoState())
    val state = mutableState.asStateFlow()
    fun begin(songId: String): Long {
        mutableState.value = MusicVideoState(MusicVideoMode.LOADING, songId)
        return ++generation
    }
    fun ready(request: Long, songId: String): Boolean {
        if (request != generation || mutableState.value.currentSongId != songId || mutableState.value.mode != MusicVideoMode.LOADING) return false
        mutableState.value = MusicVideoState(MusicVideoMode.VIDEO, songId)
        return true
    }
    fun audio(songId: String?, reason: String? = null) {
        generation++
        mutableState.value = MusicVideoState(MusicVideoMode.AUDIO, songId, reason)
    }
}

private fun videoWords(text: String): String = Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "").replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim().replace(Regex("\\s+"), " ")

private fun videoTitle(text: String, artists: List<String>): String {
    var title = text.replace(Regex("(?i)[(\\[]\\s*(official\\s+)?(music\\s+)?video(?:\\s+(?:hd|4k))?\\s*[)\\]]"), "")
        .replace(Regex("(?i)[(\\[]\\s*(?:hd|4k)\\s*[)\\]]"), "").trim()
    artists.forEach { artist ->
        val prefix = Regex("^${Regex.escape(artist)}\\s*[-–—]\\s*", RegexOption.IGNORE_CASE)
        title = title.replace(prefix, "")
    }
    return videoWords(title)
}

internal fun musicVideoCandidates(song: MediaMetadata, results: List<SongItem>): List<SongItem> {
    val artistNames = song.artists.map { it.name }
    val artists = artistNames.map(::videoWords).filter(String::isNotEmpty).toSet()
    val title = videoTitle(song.title, artistNames)
    if (title.isBlank() || artists.isEmpty()) return emptyList()
    return results.filter { candidate ->
        val duration = candidate.duration ?: return@filter false
        candidate.musicVideoType == "MUSIC_VIDEO_TYPE_OMV" && !candidate.isEpisode &&
            candidate.artists.any { videoWords(it.name) in artists } &&
            videoTitle(candidate.title, artistNames) == title &&
            duration > 0 && (song.duration <= 0 || kotlin.math.abs(song.duration - duration) <= maxOf(45, song.duration / 4))
    }.distinctBy { it.id }.sortedBy { kotlin.math.abs(song.duration - (it.duration ?: 0)) }
}

internal class MusicVideoResolver(
    private val search: suspend (String) -> List<SongItem> = { query ->
        YouTube.search(query, YouTube.SearchFilter.FILTER_VIDEO).getOrThrow().items.filterIsInstance<SongItem>()
    },
) {
    suspend fun resolveId(song: MediaMetadata): String? {
        if (song.isEpisode || !song.id.matches(Regex("[A-Za-z0-9_-]{11}"))) return null
        if (song.isVideoSong) return song.id
        return musicVideoCandidates(song, search("${song.artists.joinToString(" ") { it.name }} ${song.title} official music video")).firstOrNull()?.id
    }
}

internal fun canPlayMusicVideo(casting: Boolean, inOnlineRoom: Boolean, connected: Boolean, remoteAllowed: Boolean): Boolean =
    !casting && !inOnlineRoom && connected && remoteAllowed

internal fun musicVideoAudioPosition(positionMs: Long, durationMs: Long): Long = positionMs.coerceAtLeast(0).let {
    if (durationMs > 0) it.coerceAtMost((durationMs - 1).coerceAtLeast(0)) else it
}
