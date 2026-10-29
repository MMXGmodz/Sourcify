package com.metrolist.music.ui.player

import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.WatchEndpoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class PlayerDiscoveryTest {
    private fun song(id: String, explicit: Boolean = false, video: Boolean = false) = SongItem(
        id = id, title = id, artists = emptyList(), thumbnail = "", explicit = explicit,
        musicVideoType = if (video) "MUSIC_VIDEO_TYPE_OMV" else "MUSIC_VIDEO_TYPE_ATV",
    )
    private val radio = WatchEndpoint(videoId = "seed", playlistId = "RDAMVMseed")
    private val fallback = WatchEndpoint(videoId = "seed")

    @Test fun recommendationsExcludeCurrentTrackSeedsAndDuplicates() = runBlocking {
        val result = loadDiscoverCandidates(radio, fallback, setOf("current", "seed")) {
            listOf(song("current"), song("seed"), song("other"), song("other"))
        }
        assertEquals(listOf("other"), result.map { it.id })
    }
    @Test fun seedOnlyRadioUsesRelatedFallbackInsteadOfRestartingSeed() = runBlocking {
        val requests = mutableListOf<WatchEndpoint>()
        val result = loadDiscoverCandidates(radio, fallback, setOf("seed")) { endpoint ->
            requests += endpoint
            if (endpoint == radio) listOf(song("seed")) else listOf(song("seed"), song("similar"))
        }
        assertEquals(listOf(radio, fallback), requests)
        assertEquals(listOf("similar"), result.map { it.id })
    }
    @Test fun radioErrorCanRecoverWithFallback() = runBlocking {
        val result = loadDiscoverCandidates(radio, fallback, setOf("seed")) {
            if (it == radio) error("radio unavailable") else listOf(song("similar"))
        }
        assertEquals("similar", result.single().id)
    }
    @Test fun cancellationNeverLaunchesAnotherRequest() = runBlocking {
        var requests = 0
        try {
            loadDiscoverCandidates(radio, fallback, emptySet()) { requests++; throw CancellationException() }
            fail("Expected cancellation")
        } catch (_: CancellationException) { assertEquals(1, requests) }
    }
    @Test fun emptyRecommendationsRemainEmptyAndNetworkErrorsAreNotReportedAsSuccess() = runBlocking {
        assertTrue(loadDiscoverCandidates(radio, fallback, setOf("seed")) { listOf(song("seed")) }.isEmpty())
        try {
            loadDiscoverCandidates(radio, fallback, emptySet()) { error("offline") }
            fail("Expected network error")
        } catch (e: IllegalStateException) { assertEquals("offline", e.message) }
    }
    @Test fun explicitAndVideoPreferencesApplyWithoutReplacingRecommendations() {
        val filtered = filterDiscoverCandidates(listOf(song("clean"), song("explicit", explicit = true), song("video", video = true)), emptySet(), true, true)
        assertEquals(listOf("clean"), filtered.map { it.id })
    }
}
