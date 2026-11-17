package com.metrolist.music.utils

import android.content.Context
import androidx.media3.datasource.DataSourceInputStream
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.PlaceholderDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.offline.Download
import com.metrolist.music.db.entities.Song
import com.metrolist.music.playback.DownloadUtil
import java.io.FilterInputStream
import java.io.IOException
import kotlinx.coroutines.flow.first

fun Song.toArchiveTrack() = PlaylistArchiveTrack(id, title, artists.joinToString("; ") { it.name }, song.duration)

internal suspend fun preparePlaylistArchive(
    context: Context,
    downloads: DownloadUtil,
    name: String,
    tracks: List<PlaylistArchiveTrack>,
): PlaylistArchivePlan {
    require(tracks.size <= 10_000) { "Playlist is too large" }
    val local = LocalAudioStore.get(context)
    val storage = DownloadStorage.get(context)
    // Resolve only local sources. PlaceholderDataSource forbids a cache miss from making a network request.
    return planPlaylistArchive(name, tracks) { track ->
        val file = local.fileForId(track.id)
        if (file != null) return@planPlaylistArchive ArchiveAudio(file.length()) { file.inputStream() }
        val saved = storage.record(track.id)
        if (saved != null && storage.isReadable(track.id)) {
            return@planPlaylistArchive ArchiveAudio(saved.size) {
                storage.acquireRead(track.id)
                try {
                    val input = context.contentResolver.openInputStream(saved.uri) ?: throw IOException("Audio is unavailable")
                    object : FilterInputStream(input) {
                        override fun close() { try { super.close() } finally { storage.releaseRead(track.id) } }
                    }
                } catch (failure: Throwable) { storage.releaseRead(track.id); throw failure }
            }
        }
        val download = downloads.downloadManager.downloadIndex.getDownload(track.id)
            ?.takeIf { it.state == Download.STATE_COMPLETED }
        val length = download?.contentLength?.takeIf { it > 0 }
            ?: downloads.database.format(track.id).first()?.contentLength?.takeIf { it > 0 }
            ?: return@planPlaylistArchive null
        val cache = when {
            downloads.downloadCache.isCached(track.id, 0, length) -> downloads.downloadCache
            downloads.playerCache.isCached(track.id, 0, length) -> downloads.playerCache
            else -> return@planPlaylistArchive null
        }
        ArchiveAudio(length) {
            val source = CacheDataSource.Factory().setCache(cache)
                .setCacheWriteDataSinkFactory(null)
                .setUpstreamDataSourceFactory(PlaceholderDataSource.FACTORY).createDataSource()
            DataSourceInputStream(source, DataSpec.Builder().setUri(track.id).setKey(track.id).setLength(length).build())
        }
    }
}
