package com.metrolist.music.playback

import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource

internal const val MUSIC_VIDEO_SCHEME = "sourcify-musicvideo"
internal data class MusicVideoStreams(val audio: CachedStreamUrl?, val video: CachedStreamUrl, val durationMs: Long)

internal fun MediaItem.isMusicVideoItem() = localConfiguration?.uri?.scheme == MUSIC_VIDEO_SCHEME
internal fun MediaItem.asAudioItem(): MediaItem = if (!isMusicVideoItem()) this else buildUpon()
    .setUri(mediaId).setCustomCacheKey(mediaId).build()

internal class MusicVideoSourceFactory(
    private val audioFactory: MediaSource.Factory,
    private val streams: Map<String, MusicVideoStreams>,
    private val dataSource: (CachedStreamUrl) -> DataSource.Factory,
) : MediaSource.Factory by audioFactory {
    override fun createMediaSource(mediaItem: MediaItem): MediaSource {
        val selected = streams[mediaItem.localConfiguration?.uri.toString()]
            ?: return audioFactory.createMediaSource(mediaItem.asAudioItem())
        // Both tracks use the music video's timeline and the primary player's clock.
        // Keeping the ephemeral URI on each child also preserves the queue identity/tag.
        val audioStream = selected.audio
            ?: return ProgressiveMediaSource.Factory(dataSource(selected.video)).createMediaSource(mediaItem)
        val audio = ProgressiveMediaSource.Factory(dataSource(audioStream)).createMediaSource(mediaItem)
        val video = ProgressiveMediaSource.Factory(dataSource(selected.video)).createMediaSource(mediaItem)
        return MergingMediaSource(true, true, audio, video)
    }
}

internal fun replaceCurrentMusicVideoSource(player: ExoPlayer, item: MediaItem, durationMs: Long) {
    val index = player.currentMediaItemIndex
    val position = musicVideoAudioPosition(player.currentPosition, durationMs)
    val playWhenReady = player.playWhenReady
    replaceMusicVideoQueueItem(player, index, item)
    player.seekTo(index, position)
    player.playWhenReady = playWhenReady
}

internal fun replaceMusicVideoQueueItem(player: ExoPlayer, index: Int, item: MediaItem) {
    val timeline = player.currentTimeline
    val order = ArrayList<Int>(timeline.windowCount)
    var next = timeline.getFirstWindowIndex(true)
    while (next != C.INDEX_UNSET) {
        order += next
        next = timeline.getNextWindowIndex(next, Player.REPEAT_MODE_OFF, true)
    }
    player.replaceMediaItem(index, item)
    // Media3 remove/insert replacement otherwise randomizes the existing shuffle traversal.
    if (order.size == player.mediaItemCount) player.setShuffleOrder(DefaultShuffleOrder(order.toIntArray(), System.currentTimeMillis()))
}
