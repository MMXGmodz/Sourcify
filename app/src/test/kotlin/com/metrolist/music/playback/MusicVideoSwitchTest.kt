package com.metrolist.music.playback

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.MediaItem
import androidx.media3.datasource.ByteArrayDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.test.core.app.ApplicationProvider
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
class MusicVideoSwitchTest {
    @Test fun replacementRebuildsCurrentSourceAndKeepsQueuePositionPauseAndIdentity() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val delegate = DefaultMediaSourceFactory(DataSource.Factory { ByteArrayDataSource(byteArrayOf(0)) })
        var created = 0
        val factory = object : MediaSource.Factory by delegate {
            override fun createMediaSource(mediaItem: MediaItem): MediaSource {
                created++
                return delegate.createMediaSource(mediaItem)
            }
        }
        val player = ExoPlayer.Builder(context).setMediaSourceFactory(factory).build()
        try {
            val first = MediaMetadata("abcdefghijk", "First", emptyList(), 240).toMediaItem()
            val current = MediaMetadata("bcdefghijkl", "Second", emptyList(), 240).toMediaItem()
            val last = MediaMetadata("cdefghijklm", "Last", emptyList(), 240).toMediaItem()
            player.setMediaItems(listOf(first, current, last), 1, 125_000)
            player.setShuffleOrder(DefaultShuffleOrder(intArrayOf(2, 1, 0), 1L))
            player.shuffleModeEnabled = true
            assertEquals(listOf(2, 1, 0), order(player))
            val beforeNext = player.nextMediaItemIndex
            val beforePrevious = player.previousMediaItemIndex
            player.playWhenReady = false
            val previousCreated = created
            val video = current.buildUpon().setUri("sourcify-musicvideo://session/test").setCustomCacheKey(null).build()
            replaceCurrentMusicVideoSource(player, video, 240_000)
            assertTrue("Changed local URI must rebuild the MediaSource", created > previousCreated)
            assertEquals(listOf(2, 1, 0), order(player))
            assertEquals(beforeNext, player.nextMediaItemIndex)
            assertEquals(beforePrevious, player.previousMediaItemIndex)
            assertEquals(3, player.mediaItemCount)
            assertEquals(first, player.getMediaItemAt(0))
            assertEquals(last, player.getMediaItemAt(2))
            assertEquals(current.metadata, player.currentMediaItem!!.metadata)
            assertEquals(current.mediaId, player.currentMediaItem!!.mediaId)
            assertEquals(1, player.currentMediaItemIndex)
            assertEquals(125_000L, player.currentPosition)
            assertFalse(player.playWhenReady)
            player.playWhenReady = true
            replaceCurrentMusicVideoSource(player, current, 120_000)
            assertEquals(listOf(2, 1, 0), order(player))
            assertEquals(beforeNext, player.nextMediaItemIndex)
            assertEquals(beforePrevious, player.previousMediaItemIndex)
            assertEquals(119_999L, player.currentPosition)
            assertTrue(player.playWhenReady)
            assertEquals(current.localConfiguration!!.uri, player.currentMediaItem!!.localConfiguration!!.uri)
        } finally { player.release() }
    }
    private fun order(player: Player): List<Int> = buildList {
        var next = player.currentTimeline.getFirstWindowIndex(true)
        while (next != C.INDEX_UNSET) {
            add(next)
            next = player.currentTimeline.getNextWindowIndex(next, Player.REPEAT_MODE_OFF, true)
        }
    }
}
