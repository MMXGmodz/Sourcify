package com.metrolist.music.playback

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.core.content.getSystemService
import androidx.media3.datasource.DataSourceInputStream
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.PlaceholderDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.offline.Download
import com.metrolist.innertube.YouTube
import com.metrolist.music.constants.AutomaticCanvasKey
import com.metrolist.music.constants.CanvasEnabledKey
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.LyricsEntity
import com.metrolist.music.lyrics.saveFetchedLyricsIfMissing
import com.metrolist.music.di.LyricsHelperEntryPoint
import com.metrolist.music.models.toMediaMetadata
import com.metrolist.music.ui.player.*
import com.metrolist.music.utils.*
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

internal class DownloadCompanionPreparer(
    private val context: Context,
    private val database: MusicDatabase,
    private val cache: Cache,
    private val lookup: (String) -> Download?,
) {
    private val store = DownloadCompanionStore.get(context)
    private val storage = DownloadStorage.get(context)
    private val lyrics by lazy { EntryPointAccessors.fromApplication(context.applicationContext, LyricsHelperEntryPoint::class.java).lyricsHelper() }

    suspend fun prepare(id: String, force: Boolean) = withContext(Dispatchers.IO) {
        val download = lookup(id)?.takeIf { it.state == Download.STATE_COMPLETED } ?: return@withContext
        val song = database.getSongByIdBlocking(id) ?: return@withContext
        val metadata = song.toMediaMetadata()
        runOptionalDownloadTasks({
            if (storage.record(id) != null && !storage.hasActiveReads(id) && storage.isReadable(id)) cache.removeResource(id)
            if (storage.targetFolder(id) != null && storage.record(id) == null) withTimeoutOrNull(60_000) {
                val length = download.contentLength.takeIf { it > 0 }
                    ?: database.format(id).first()?.contentLength?.takeIf { it > 0 }
                if (length != null && cache.isCached(id, 0, length)) {
                    val source = CacheDataSource.Factory().setCache(cache).setCacheWriteDataSinkFactory(null)
                        .setUpstreamDataSourceFactory(PlaceholderDataSource.FACTORY).createDataSource()
                    val spec = DataSpec.Builder().setUri(id).setKey(id).setLength(length).build()
                    val mime = database.format(id).first()?.mimeType ?: "audio/mp4"
                    val exported = DataSourceInputStream(source, spec).use { input ->
                        storage.export(id, metadata.title, mime, input, length) { lookup(id)?.state == Download.STATE_COMPLETED }
                    }
                    if (exported && storage.record(id) != null && !storage.hasActiveReads(id)) cache.removeResource(id)
                }
            }
        })
        if (!hasDownloadInternet(context) || (!force && System.currentTimeMillis() - store.lastAttempt(id) < 86_400_000)) return@withContext
        runOptionalDownloadTasks(
            {
                if (store.info(id) == null) withTimeoutOrNull(12_000) {
                    YouTube.getMediaInfo(id).getOrNull()?.let { store.saveInfo(id, it) }
                }
            },
            {
                val primary = parseSongCredits(store.info(id)?.description)
                if (store.additionalCredits(id) == null && CreditGroup.entries.any { group -> primary.entries.none { it.group == group } }) {
                    withTimeoutOrNull(20_000) {
                        val result = MusicBrainzCreditsRepository.shared.load(MusicBrainzSongKey(metadata.title,
                            metadata.artists.map { it.name }, metadata.duration))
                        if (result is MusicBrainzCreditsResult.Found) store.saveAdditionalCredits(id, savedMusicBrainzCredits(result))
                    }
                }
            },
            {
                val saved = database.lyrics(id).first()
                if (saved == null || saved.lyrics == LyricsEntity.LYRICS_NOT_FOUND) withTimeoutOrNull(26_000) {
                    val result = lyrics.getLyrics(metadata)
                    if (result.lyrics.isNotBlank() && result.lyrics != LyricsEntity.LYRICS_NOT_FOUND && lookup(id)?.state == Download.STATE_COMPLETED) {
                        database.saveFetchedLyricsIfMissing(LyricsEntity(id, result.lyrics, result.provider))
                    }
                }
            },
            {
                val canvasStore = CanvasVideoStore(context)
                if (context.dataStore.get(CanvasEnabledKey, true) && canvasStore.isEnabled(id) && CanvasNetworkPolicy.get(context).mayLoadRemote()) {
                    val selected = canvasStore.selection(id)
                    val saved = store.playbackSelection(id, selected)
                    if (saved?.uri?.scheme != "file") withTimeoutOrNull(60_000) {
                        val source = when {
                            selected?.uri != null -> CanvasVideoSource(selected.uri, selected.startMs, selected.endMs)
                            selected?.videoId != null -> resolveSelectedCanvasVideo(selected.videoId)?.copy(clipStartMs = selected.startMs, clipEndMs = selected.endMs)
                            context.dataStore.get(AutomaticCanvasKey, true) -> resolveCanvasForDownload(metadata)
                            else -> null
                        }
                        if (source != null) {
                            val clip = downloadCanvasClip(context, source)
                            try { store.saveCanvas(id, selected, clip.file, clip.startMs, clip.endMs) }
                            finally { clip.file.parentFile?.deleteRecursively() }
                        }
                    }
                }
            },
        )
        store.markAttempt(id)
    }
}

internal fun hasDownloadInternet(context: Context): Boolean {
    val manager = context.getSystemService<ConnectivityManager>() ?: return false
    return manager.getNetworkCapabilities(manager.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
}
