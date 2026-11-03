package com.metrolist.music.playback

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.ByteArrayDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MergingMediaSource
import com.metrolist.music.extensions.metadata
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.models.MediaMetadata
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class MusicVideoSourceFactoryTest {
    private val metadata = MediaMetadata("abcdefghijk", "Song", emptyList(), 240)
    private val audio = metadata.toMediaItem()
    private val sources = mutableMapOf<String, MusicVideoStreams>()
    private val bytes = DataSource.Factory { ByteArrayDataSource(byteArrayOf(1, 2, 3)) }
    private val opened = mutableListOf<String>()
    private val factory = MusicVideoSourceFactory(DefaultMediaSourceFactory(bytes), sources) { stream -> opened += stream.url; bytes }

    @Test fun mergesFullVideoAndItsOwnAudioWithoutChangingQueueIdentity() {
        val uri = "sourcify-musicvideo://session/1"
        sources[uri] = MusicVideoStreams(CachedStreamUrl("https://example.com/audio", emptyMap(), "test"), CachedStreamUrl("https://example.com/video", emptyMap(), "test"), 240_000)
        val item = audio.buildUpon().setUri(uri).setCustomCacheKey(null).build()
        val source = factory.createMediaSource(item)
        assertTrue(source is MergingMediaSource)
        assertEquals(listOf("https://example.com/audio", "https://example.com/video"), opened)
        assertEquals(audio.mediaId, source.mediaItem.mediaId)
        assertEquals(metadata, source.mediaItem.metadata)
        assertEquals(Uri.parse(uri), source.mediaItem.localConfiguration!!.uri)
        assertEquals(0L, source.mediaItem.clippingConfiguration.startPositionMs)
        assertEquals(C.TIME_END_OF_SOURCE, source.mediaItem.clippingConfiguration.endPositionMs)
    }

    @Test fun ordinaryAudioStillUsesOriginalFactory() {
        assertFalse(factory.createMediaSource(audio) is MergingMediaSource)
    }

    @Test fun expiredEphemeralItemReturnsToOriginalAudioUri() {
        val item = audio.buildUpon().setUri("sourcify-musicvideo://session/expired").build()
        assertEquals(audio.localConfiguration!!.uri, factory.createMediaSource(item).mediaItem.localConfiguration!!.uri)
    }
}
