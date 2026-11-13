package com.metrolist.music.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.metrolist.innertube.YouTube
import com.metrolist.music.LocalDatabase
import com.metrolist.music.db.entities.deserializeArtistPage
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.utils.CanvasVideoSource
import com.metrolist.music.utils.SongEditStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

internal data class LandscapeBackgroundSource(val image: Any? = null, val video: CanvasVideoSource? = null)

@Composable
internal fun rememberLandscapeBackground(song: MediaMetadata?, enabled: Boolean): LandscapeBackgroundSource {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val store = remember(context) { SongEditStore.get(context) }
    val revision by store.revision.collectAsState()
    val custom = remember(song?.id, revision) { song?.id?.let(store::landscapeBackground) }
    val artistId = remember(song?.id, revision) { song?.let(store::original)?.artists?.firstOrNull { it.id != null }?.id }
    val artistImage by produceState<String?>(null, artistId, enabled, custom) {
        value = null
        if (!enabled || custom != null || artistId == null) return@produceState
        try {
            val cached = withContext(Dispatchers.IO) { database.artist(artistId).first()?.artist }
            value = cached?.cachedPageJson?.let { runCatching { deserializeArtistPage(it).artist?.thumbnail }.getOrNull() }
                ?: cached?.thumbnailUrl
            if (value.isNullOrBlank()) value = YouTube.artist(artistId).getOrThrow().artist.thumbnail
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // A missing artist image leaves the song-colored gradient visible.
        }
    }
    return when {
        custom?.isImage == true -> LandscapeBackgroundSource(image = custom.uri)
        custom != null -> LandscapeBackgroundSource(video = CanvasVideoSource(custom.uri))
        else -> LandscapeBackgroundSource(image = artistImage)
    }
}

@Composable
internal fun LandscapeBackground(source: LandscapeBackgroundSource, color: Color, playing: Boolean) {
    var failed by remember(source) { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        LandscapeArtworkBackdrop(source.image, color)
        if (source.video != null && !failed) {
            CanvasVideoBackground(source.video, playing, onError = { failed = true }, fullscreen = true, modifier = Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = .7f), Color.Black.copy(alpha = .25f)))))
        }
    }
}
