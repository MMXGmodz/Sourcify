package com.metrolist.music.utils

import android.net.Uri
import com.metrolist.innertube.models.Artist
import com.metrolist.innertube.models.SongItem
import com.metrolist.music.models.MediaMetadata
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class AutomaticCanvasResolverTest {
    private val song = MediaMetadata("audio", "Hello", listOf(MediaMetadata.Artist(null, "Adele")), 295)
    private fun video(id: String, title: String, artist: String = "Adele") = SongItem(
        id = id, title = title, artists = listOf(Artist(artist, null)), duration = 300,
        thumbnail = "", musicVideoType = "MUSIC_VIDEO_TYPE_OMV",
    )

    @Test
    fun selectedVideoResolvesDirectlyWithoutSearch() = runBlocking {
        val expected = CanvasVideoSource(Uri.parse("https://example.com/video.mp4"))
        val resolver = AutomaticCanvasResolver(search = { error("must not search") }, resolveVideo = { expected })
        assertEquals(expected, resolver.resolve(song.copy(musicVideoType = "MUSIC_VIDEO_TYPE_OMV")))
    }

    @Test
    fun concurrentRequestsShareOneLookup() = runBlocking {
        var searches = 0
        val resolver = AutomaticCanvasResolver(search = { searches++; kotlinx.coroutines.delay(20); listOf(video("match", "Hello")) },
            resolveVideo = { CanvasVideoSource(Uri.parse("https://example.com/video.mp4")) })
        kotlinx.coroutines.coroutineScope {
            val first = async { resolver.resolve(song) }
            val second = async { resolver.resolve(song) }
            assertEquals(first.await(), second.await())
        }
        assertEquals(1, searches)
    }

    @Test
    fun matchesTitleAndArtistAndAvoidsUnrequestedVersions() {
        val candidates = listOf(
            video("wrong-artist", "Hello", "Lionel Richie"),
            video("wrong-song", "Someone Like You"),
            video("cover", "Hello cover"),
            video("remix", "Hello remix"),
            video("match", "Hello (Official Music Video)"),
        )
        assertEquals(listOf("match"), canvasCandidates(song, candidates).map { it.id })
        val music = song.copy(title = "Music")
        assertEquals(listOf("music"), canvasCandidates(music, listOf(video("music", "Music"))).map { it.id })
    }

    @Test
    fun loopsEightSecondsAroundMiddleAndHandlesShortVideos() {
        assertEquals(146_000L until 154_000L, canvasMiddleClip(300_000))
        assertEquals(0L until 3_000L, canvasMiddleClip(3_000))
        assertEquals(0L until 8_000L, canvasMiddleClip(8_000))
    }

    @Test
    fun fallsBackToAnotherMatchingVideoAndCachesWithoutMixingSongs() = runBlocking {
        val queries = mutableListOf<String>()
        val ids = mutableListOf<String>()
        var time = 1_000L
        val expected = CanvasVideoSource(Uri.parse("https://example.com/video.mp4"), 146_000, 154_000)
        val resolver = AutomaticCanvasResolver(
            search = { queries += it; listOf(video("blocked", "Hello (Official Video)"), video("playable", "Hello")) },
            resolveVideo = { id -> ids += id; if (id == "blocked") error("unavailable") else expected },
            now = { time },
        )
        assertEquals(expected, resolver.resolve(song))
        assertEquals(listOf("blocked", "playable"), ids)
        assertEquals(listOf("Adele Hello"), queries)
        assertEquals(expected, resolver.resolve(song))
        assertEquals(1, queries.size)
        assertNull(resolver.resolve(song.copy(id = "other", title = "Different Song")))
        time += 301_000
        assertEquals(expected, resolver.resolve(song))
        assertEquals(3, queries.size)
    }

    @Test
    fun unavailableSearchAndEpisodesFallBackToCover() = runBlocking {
        var calls = 0
        val resolver = AutomaticCanvasResolver(search = { calls++; error("offline") }, resolveVideo = { error("must not extract") })
        assertNull(resolver.resolve(song.copy(isEpisode = true)))
        assertEquals(0, calls)
        assertNull(resolver.resolve(song))
        assertNull(resolver.resolve(song))
        assertEquals(1, calls)
    }

    @Test
    fun cancellationOfOldSongDoesNotContinueToAnotherVideo() = runBlocking {
        var calls = 0
        val resolver = AutomaticCanvasResolver(
            search = { listOf(video("first", "Hello"), video("second", "Hello")) },
            resolveVideo = { calls++; throw CancellationException("song changed") },
        )
        var cancelled = false
        try { resolver.resolve(song) } catch (_: CancellationException) { cancelled = true }
        assertTrue(cancelled)
        assertEquals(1, calls)
    }
}
