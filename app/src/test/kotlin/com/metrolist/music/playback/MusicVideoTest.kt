package com.metrolist.music.playback

import com.metrolist.innertube.models.Artist
import com.metrolist.innertube.models.SongItem
import com.metrolist.music.models.MediaMetadata
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class MusicVideoTest {
    private val song = MediaMetadata("abcdefghijk", "My Song", listOf(MediaMetadata.Artist("artist", "Original Artist")), 240)
    private fun candidate(title: String = "My Song (Official Music Video)", artist: String = "Original Artist", type: String? = "MUSIC_VIDEO_TYPE_OMV", duration: Int = 245) =
        SongItem("video123456", title, listOf(Artist(artist, "artist")), duration = duration, musicVideoType = type, thumbnail = "")

    @Test fun officialMatchRequiresExactSongAndArtist() {
        assertEquals(listOf(candidate()), musicVideoCandidates(song, listOf(candidate(), candidate(title = "My Other Song"), candidate(artist = "Another Artist"))))
        assertTrue(musicVideoCandidates(song, listOf(candidate("My Song live"), candidate("My Song Remix"), candidate(type = "MUSIC_VIDEO_TYPE_ATV"), candidate(duration = 8))).isEmpty())
    }

    @Test fun searchCannotConvertOriginalIntoRandomLongerSong() {
        assertTrue(musicVideoCandidates(song, listOf(candidate("My Song Extended"), candidate("My Song", duration = 500), candidate(type = null))).isEmpty())
    }

    @Test fun originalVideoIdIsUsedWithoutSearching() = runBlocking {
        var searched = false
        val resolver = MusicVideoResolver(search = { searched = true; emptyList() })
        assertEquals(song.id, resolver.resolveId(song.copy(musicVideoType = "MUSIC_VIDEO_TYPE_OMV")))
        assertFalse(searched)
    }

    @Test fun failedOrUnmatchedSearchLeavesNoVideo() = runBlocking {
        val resolver = MusicVideoResolver(search = { listOf(candidate(artist = "Another Artist")) })
        assertNull(resolver.resolveId(song))
    }

    @Test fun onlineRoomHostsAndGuestsAreRestrictedAlongWithCastAndOffline() {
        assertTrue(canPlayMusicVideo(casting = false, inOnlineRoom = false, connected = true, remoteAllowed = true))
        assertFalse(canPlayMusicVideo(casting = false, inOnlineRoom = true, connected = true, remoteAllowed = true))
        assertFalse(canPlayMusicVideo(casting = true, inOnlineRoom = false, connected = true, remoteAllowed = true))
        assertFalse(canPlayMusicVideo(casting = false, inOnlineRoom = false, connected = false, remoteAllowed = true))
        assertFalse(canPlayMusicVideo(casting = false, inOnlineRoom = false, connected = true, remoteAllowed = false))
    }

    @Test fun persistedAndRestoredPositionFitsOriginalAudioLength() {
        assertEquals(239_999L, musicVideoAudioPosition(280_000, 240_000))
        assertEquals(125_000L, musicVideoAudioPosition(125_000, 240_000))
        assertEquals(125_000L, musicVideoAudioPosition(125_000, -1))
        assertEquals(0L, musicVideoAudioPosition(-1, 240_000))
    }

    @Test fun cancelledAndStaleLoadsCannotEnterVideoMode() {
        val session = MusicVideoSession()
        val first = session.begin(song.id)
        session.audio(song.id, "offline")
        assertFalse(session.ready(first, song.id))
        val second = session.begin("next")
        assertFalse(session.ready(second, song.id))
        assertTrue(session.ready(second, "next"))
        assertEquals(MusicVideoMode.VIDEO, session.state.value.mode)
        session.audio("next", "playback failed")
        assertEquals(MusicVideoMode.AUDIO, session.state.value.mode)
        assertEquals("playback failed", session.state.value.reason)
    }
}
