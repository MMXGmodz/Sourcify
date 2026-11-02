package com.metrolist.music.utils
import android.app.Application
import androidx.media3.exoplayer.ExoPlayer
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.extensions.toMediaItem
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [33])
class ArtistQueueTest {
 @Test fun blocksTheAudibleFadingPlayerWithoutAffectingOtherArtists() {
  val fading = ExoPlayer.Builder(RuntimeEnvironment.getApplication()).build()
  try {
   fading.setMediaItem(MediaMetadata("fading", "Fading", listOf(MediaMetadata.Artist("blocked", "CAPO")), 120).toMediaItem())
   fading.playWhenReady = true
   assertFalse(stopBlockedAuxiliary(fading, mapOf("other" to "Other")))
   assertTrue(fading.playWhenReady)
   assertTrue(stopBlockedAuxiliary(fading, mapOf("blocked" to "CAPO")))
   assertFalse(fading.playWhenReady); assertEquals(0, fading.mediaItemCount)
  } finally { fading.release() }
 }
 @Test fun removesBlockedCurrentAndUpcomingTracksAndCanClearAllBlockedQueue() {
  val player = ExoPlayer.Builder(RuntimeEnvironment.getApplication()).build()
  fun item(id: String, artist: String) = MediaMetadata(id, id, listOf(MediaMetadata.Artist(artist, artist)), 120).toMediaItem()
  try {
   player.setMediaItems(listOf(item("one", "blocked"), item("two", "keep"), item("three", "blocked")))
   pruneBlockedTracks(player, mapOf("blocked" to "blocked"))
   assertEquals(1, player.mediaItemCount); assertEquals("two", player.currentMediaItem?.mediaId)
   pruneBlockedTracks(player, mapOf("keep" to "keep")); assertEquals(0, player.mediaItemCount)
  } finally { player.release() }
 }
}
