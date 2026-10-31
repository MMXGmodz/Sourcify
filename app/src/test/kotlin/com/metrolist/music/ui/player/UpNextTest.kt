package com.metrolist.music.ui.player

import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class UpNextTest {
    @Test fun shuffledNextTrackUsesThePlayersTimelineIndex() {
        val player = ExoPlayer.Builder(ApplicationProvider.getApplicationContext()).build()
        try {
            player.setMediaItems((0..3).map { MediaItem.Builder().setMediaId("song$it").setUri("https://example.org/$it").build() })
            player.setShuffleOrder(ShuffleOrder.DefaultShuffleOrder(intArrayOf(2, 0, 3, 1), 1L))
            player.shuffleModeEnabled = true
            player.seekToDefaultPosition(2)
            assertEquals("song0", nextQueueItem(player)?.mediaId)
            player.seekToDefaultPosition(1)
            assertNull(nextQueueItem(player))
        } finally { player.release() }
    }
}
