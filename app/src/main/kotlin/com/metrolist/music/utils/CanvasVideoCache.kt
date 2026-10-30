package com.metrolist.music.utils

import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.metrolist.innertube.YouTube
import com.metrolist.music.playback.CachedStreamUrl
import com.metrolist.music.playback.withResolvedStream
import okhttp3.OkHttpClient

/** Separate, bounded video cache; never evicts music downloads. Initialize on an IO worker. */
internal object CanvasVideoCache {
    private var cache: SimpleCache? = null
    private val httpClient = OkHttpClient()

    @Synchronized
    private fun cache(context: Context): SimpleCache = cache ?: SimpleCache(
        context.applicationContext.cacheDir.resolve("canvas-video-ranges"),
        LeastRecentlyUsedCacheEvictor(96L * 1024 * 1024),
        StandaloneDatabaseProvider(context.applicationContext),
    ).also { cache = it }

    fun dataSource(context: Context, source: CanvasVideoSource): DataSource.Factory {
        val client = httpClient.newBuilder().proxy(YouTube.proxy)
            .proxyAuthenticator { _, response ->
                if (response.request.header("Proxy-Authorization") != null) null
                else YouTube.proxyAuth?.let { response.request.newBuilder().header("Proxy-Authorization", it).build() }
            }.build()
        val resolved = source.stream?.let {
            CachedStreamUrl(source.uri.toString(), it.headers, it.clientName,
                it.requireBoundedRange, it.rangeChunkSizeBytes, it.useRangeChunks)
        }
        val upstream = CanvasNetworkPolicy.get(context).guard(DefaultDataSource.Factory(context.applicationContext, OkHttpDataSource.Factory(client)))
        val videoCache = if (source.uri.scheme in setOf("content", "file")) null else runCatching { cache(context) }.getOrNull()
        val cached: DataSource.Factory = if (videoCache == null) upstream else CacheDataSource.Factory()
            .setCache(videoCache).setUpstreamDataSourceFactory(upstream)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        // Resolve ranges before entering the cache, as the main audio pipeline does.
        return ResolvingDataSource.Factory(cached) { spec -> resolved?.let { spec.withResolvedStream(it) } ?: spec }

    }

    @Synchronized
    fun bytesUsed(context: Context): Long = cache(context).cacheSpace

    @Synchronized
    fun clear() { cache?.let { current -> current.keys.toList().forEach(current::removeResource) } }
}
