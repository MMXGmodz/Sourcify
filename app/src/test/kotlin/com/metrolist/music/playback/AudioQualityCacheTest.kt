package com.metrolist.music.playback

import android.content.Context
import android.net.Uri
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.ByteArrayDataSource
import androidx.media3.datasource.DataSourceInputStream
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.PlaceholderDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class AudioQualityCacheTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun downloadedAudioStaysPlayableWithoutNetworkAfterQualityChange() {
        withCaches { playerCache, downloadCache ->
            val downloadedAudio = ByteArray(8192) { (it % 127).toByte() }
            write(downloadCache, "downloaded", downloadedAudio)
            write(playerCache, "downloaded", byteArrayOf(1, 2, 3))

            assertFalse(prepareAudioQualityChange("downloaded", playerCache, downloadCache))

            val offlineSource = CacheDataSource.Factory()
                .setCache(downloadCache)
                .setCacheWriteDataSinkFactory(null)
                .setUpstreamDataSourceFactory(PlaceholderDataSource.FACTORY)
                .createDataSource()
            DataSourceInputStream(offlineSource, dataSpec("downloaded")).use {
                assertArrayEquals(downloadedAudio, it.readBytes())
            }
        }
    }

    @Test
    fun streamQualityChangeClearsOnlyTheSelectedTransientAudio() {
        withCaches { playerCache, downloadCache ->
            write(playerCache, "stream", byteArrayOf(1, 2, 3))
            write(playerCache, "other-stream", byteArrayOf(4, 5, 6))
            write(downloadCache, "saved", byteArrayOf(7, 8, 9))

            assertTrue(prepareAudioQualityChange("stream", playerCache, downloadCache))

            assertTrue(playerCache.getCachedSpans("stream").isEmpty())
            assertTrue(playerCache.isCached("other-stream", 0, 3))
            assertTrue(downloadCache.isCached("saved", 0, 3))
        }
    }

    private fun dataSpec(mediaId: String) = DataSpec.Builder()
        .setUri(Uri.parse("https://example.invalid/$mediaId"))
        .setKey(mediaId)
        .build()

    private fun write(cache: SimpleCache, mediaId: String, bytes: ByteArray) {
        CacheWriter(
            CacheDataSource.Factory()
                .setCache(cache)
                .setUpstreamDataSourceFactory { ByteArrayDataSource(bytes) }
                .createDataSource(),
            dataSpec(mediaId),
            null,
            null,
        ).cache()
    }

    private fun withCaches(block: (SimpleCache, SimpleCache) -> Unit) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = StandaloneDatabaseProvider(context)
        val playerCache = SimpleCache(temporaryFolder.newFolder("player"), NoOpCacheEvictor(), database)
        val downloadCache = SimpleCache(temporaryFolder.newFolder("downloads"), NoOpCacheEvictor(), database)
        try {
            block(playerCache, downloadCache)
        } finally {
            playerCache.release()
            downloadCache.release()
            database.close()
        }
    }
}
