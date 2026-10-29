package com.metrolist.music.ui.player

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class MusicBrainzCreditsTest {
    private val song = MusicBrainzSongKey("Topfit", listOf("Lucio101"), 124)
    private val id = "ac343f5b-a8b7-4b21-9235-0f12dc55e048"
    private fun recording(title: String = "Topfit", artist: String = "Lucio101", length: Int = 124000, recordingId: String = id) =
        """{"id":"$recordingId","title":"$title","length":$length,"video":false,"artist-credit":[{"name":"$artist","artist":{"id":"artist-id","name":"$artist"}}]}"""

    @Test fun exactRecordingRequiresMatchingTitleArtistAndDuration() {
        assertEquals(id, selectMusicBrainzRecording(song, """{"recordings":[${recording()}]}"""))
        listOf(recording(title = "Topfit (Live)"), recording(artist = "Someone else"), recording(length = 130000))
            .forEach { assertNull(selectMusicBrainzRecording(song, """{"recordings":[$it]}""")) }
    }

    @Test fun ambiguousRecordingsAndUnknownDurationDoNotInventAMatch() {
        val other = recording(recordingId = "c124c80b-8471-4eb1-a515-a054122eb1cf")
        assertNull(selectMusicBrainzRecording(song, """{"recordings":[${recording()},$other]}"""))
        assertNull(selectMusicBrainzRecording(song.copy(durationSeconds = -1), """{"recordings":[${recording()}]}"""))
        assertNull(selectMusicBrainzRecording(song, """{"recordings":[{"id":"$id","title":"Topfit","artist-credit":[{"name":"Lucio101"}]}]}"""))
    }

    @Test fun versionTitleIsPreservedWhileCaseAndPunctuationAreNormalized() {
        assertEquals(id, selectMusicBrainzRecording(song.copy(title = "  TOPFIT! "), """{"recordings":[${recording()}]}"""))
        assertNull(selectMusicBrainzRecording(song.copy(title = "Topfit Remix"), """{"recordings":[${recording()}]}"""))
    }

    @Test fun relationsKeepConfirmedCompositionAndProductionButIgnoreUnknownRoles() {
        val detail = recording().dropLast(1) + """, "relations":[
            {"type":"mix","target-type":"artist","artist":{"name":"Brasco"}},
            {"type":"unknown","target-type":"artist","artist":{"name":"Unconfirmed"}},
            {"type":"performance","target-type":"work","work":{"title":"Topfit","relations":[
                {"type":"lyricist","target-type":"artist","artist":{"name":"Lucio101"}},
                {"type":"composer","target-type":"artist","artist":{"name":"Almnac"}}
            ]}}
        ]}"""
        val credits = parseMusicBrainzCredits(detail, id)
        assertEquals(listOf("Brasco", "Lucio101", "Almnac"), credits.entries.map { it.name })
        assertEquals(CreditGroup.PRODUCTION, credits.entries.first().group)
        assertEquals(2, credits.entries.count { it.group == CreditGroup.COMPOSITION })
        assertTrue(parseMusicBrainzCredits(detail, "different-id").entries.isEmpty())
    }

    @Test fun cachedMatchAndNegativeMatchAvoidFurtherNetworkWork() = runBlocking {
        val requests = mutableListOf<String>()
        val repository = MusicBrainzCreditsRepository(request = { url ->
            requests += url
            MusicBrainzResponse(200, if (url.contains("query=")) """{"recordings":[${recording()}]}""" else recording())
        }, pause = {})
        assertTrue(repository.load(song) is MusicBrainzCreditsResult.Found)
        assertTrue(repository.load(song) is MusicBrainzCreditsResult.Found)
        assertEquals(2, requests.size)
        val unavailable = MusicBrainzCreditsRepository(request = { requests += it; MusicBrainzResponse(200, """{"recordings":[]}""") }, pause = {})
        assertEquals(MusicBrainzCreditsResult.Unavailable, unavailable.load(song))
        assertEquals(MusicBrainzCreditsResult.Unavailable, unavailable.load(song))
        assertEquals(3, requests.size)
    }

    @Test fun requestStartsStayOneSecondApartAndHonorServiceBackoff() = runBlocking {
        var now = 10000L
        val starts = mutableListOf<Long>()
        val repository = MusicBrainzCreditsRepository(request = {
            starts += now
            MusicBrainzResponse(503, "", retryAfterSeconds = 5)
        }, nowMillis = { now }, pause = { now += it })
        assertEquals(MusicBrainzCreditsResult.Error, repository.load(song))
        assertEquals(MusicBrainzCreditsResult.Error, repository.load(song, force = true))
        assertEquals(listOf(10000L, 15000L), starts)
    }

    @Test fun mismatchingDetailDoesNotDisplayCreditsFromASearchHit() = runBlocking {
        val repository = MusicBrainzCreditsRepository(request = { url ->
            MusicBrainzResponse(200, if (url.contains("query=")) """{"recordings":[${recording()}]}""" else recording(artist = "Wrong artist"))
        }, pause = {})
        assertEquals(MusicBrainzCreditsResult.Unavailable, repository.load(song))
    }

    @Test fun fallbackFillsMissingCreditGroupsWithoutReplacingConfirmedSourceCredits() {
        val primary = SongCredits(listOf(SongCredit("Confirmed writer", "Texter*in", CreditGroup.COMPOSITION)), listOf("Original Label"))
        val extra = MusicBrainzCreditsResult.Found(id, SongCredits(listOf(
            SongCredit("Other writer", "Texter*in", CreditGroup.COMPOSITION),
            SongCredit("Producer", "Produzent*in", CreditGroup.PRODUCTION),
        ), emptyList()))
        val merged = mergeMusicBrainzCredits(primary, extra)
        assertEquals(listOf("Confirmed writer", "Producer"), merged.entries.map { it.name })
        assertEquals(listOf("Original Label"), merged.sources)
    }
}
