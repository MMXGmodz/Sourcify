package com.metrolist.music.jam

import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.extensions.withUpdatedMetadata
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.playback.MusicService
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class AndroidJamTest {
    @Test
    fun media3QueueKeepsDuplicateOccurrencesStableAndHidesLocalPaths() {
        val service = Robolectric.buildService(MusicService::class.java).get()
        val player = ExoPlayer.Builder(ApplicationProvider.getApplicationContext()).build()
        MusicService::class.java.getDeclaredField("player").apply { isAccessible = true }.set(service, player)
        try {
            fun item(id: String, title: String) = MediaMetadata(id, title, listOf(MediaMetadata.Artist(null, "Artist")), 180).toMediaItem()
            val duplicate = item("abcdefghijk", "Repeat")
            player.setMediaItems(listOf(duplicate, duplicate, item("/private/storage/song.mp3", "Local song")))
            val jam = AndroidJam(service)
            val original = jam.snapshot().getJSONArray("queue")
            val first = original.getJSONObject(0).getString("entryId")
            val second = original.getJSONObject(1).getString("entryId")
            assertNotEquals(first, second)
            player.replaceMediaItem(1, player.getMediaItemAt(1).withUpdatedMetadata(MediaMetadata("abcdefghijk", "Repeat", emptyList(), 180, liked = true)))
            assertEquals(second, jam.snapshot().getJSONArray("queue").getJSONObject(1).getString("entryId"))
            assertEquals("", original.getJSONObject(2).getString("id"))
            assertFalse(jam.snapshot().toString().contains("/private/storage"))
            jam.move(second, -1)
            assertEquals(second, jam.snapshot().getJSONArray("queue").getJSONObject(0).getString("entryId"))
            jam.remove(first)
            assertEquals(2, player.mediaItemCount)
            assertEquals("Repeat", player.getMediaItemAt(0).mediaMetadata.title.toString())
            assertEquals(second, jam.snapshot().getJSONArray("queue").getJSONObject(0).getString("entryId"))
            val page = service.assets.open("jam/index.html").bufferedReader().use { it.readText() }
            assertTrue(page.contains("auf dem Gerät des Hosts"))
            assertFalse(page.contains("Metrolist"))
        } finally { player.release() }
    }
}
