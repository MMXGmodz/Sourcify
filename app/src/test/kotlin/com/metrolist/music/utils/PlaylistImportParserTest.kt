package com.metrolist.music.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class PlaylistImportParserTest {
    @Test
    fun `CSV accepts exported Spotify headers quoting BOM and duration milliseconds`() {
        val tracks = parsePlaylistImportFile("\uFEFFTrack Name,Artist Name(s),Duration (ms)\r\n\"Hello, \"\"world\"\"\",Björk,123000\r\n")
        assertEquals(listOf(PlaylistImportTrack("Hello, \"world\"", "Björk", 123)), tracks)
    }

    @Test
    fun `CSV supports multiline quoted fields semicolons and preserves duplicates`() {
        val tracks = parsePlaylistImportFile("title;artist\n\"A\nB\";Singer\nSame;Artist\nSame;Artist")
        assertEquals("A\nB", tracks.first().title)
        assertEquals(tracks[1], tracks[2])
    }

    @Test
    fun `extended M3U reads metadata without following media locations`() {
        assertEquals(listOf(PlaylistImportTrack("Song - Remix", "Artist", 123)),
            parsePlaylistImportFile("#EXTM3U\n#EXTINF:123,Artist - Song - Remix\nhttps://untrusted.invalid/file.mp3\n"))
    }

    @Test
    fun `malformed and metadata-free exports cannot silently create partial playlists`() {
        listOf("title,artist\nValid,Singer\nBroken,", "title,artist\n\"unfinished,Singer", "title,artist\n\"ok\"junk,Singer",
            "#EXTM3U\n#EXTINF:123,Song without artist\nfile.mp3", "url\nhttps://example.com/song").forEach {
            assertFailure(PlaylistImportError.INVALID_FILE) { parsePlaylistImportFile(it) }
        }
        assertFailure(PlaylistImportError.EMPTY) { parsePlaylistImportFile("title,artist\n") }
    }

    @Test
    fun `oversized playlists and files fail instead of truncating`() {
        assertFailure(PlaylistImportError.LIMIT) {
            parsePlaylistImportFile("title,artist\n" + (1..201).joinToString("\n") { "Song $it,Artist" })
        }
        assertFailure(PlaylistImportError.LIMIT) { parsePlaylistImportFile("x".repeat(PLAYLIST_IMPORT_MAX_BYTES + 1)) }
    }

    @Test
    fun `provider URLs require HTTPS trusted hosts and valid playlist IDs`() {
        assertEquals(PlaylistImportLink("deezer", "123"), parsePlaylistImportLink("https://www.deezer.com/de/playlist/123?utm_source=x"))
        assertEquals(PlaylistImportLink("youtube", "PL_123-a"), parsePlaylistImportLink("https://music.youtube.com/playlist?list=PL_123-a&si=x"))
        listOf("http://deezer.com/playlist/123", "https://deezer.com.evil.test/playlist/123", "https://deezer.com@evil.test/playlist/123",
            "https://deezer.com:123/playlist/1", "https://deezer.com/track/123", "file:///playlist/1", "https://youtube.com/playlist?list=../../etc").forEach {
            assertFailure(PlaylistImportError.UNSUPPORTED) { parsePlaylistImportLink(it) }
        }
        assertFailure(PlaylistImportError.EXPORT_REQUIRED) { parsePlaylistImportLink("https://open.spotify.com/playlist/id") }
        assertFailure(PlaylistImportError.EXPORT_REQUIRED) { parsePlaylistImportLink("https://music.apple.com/de/playlist/name/id") }
    }

    @Test
    fun `matching normalizes accents but rejects unrelated artists versions and runtimes`() {
        val source = PlaylistImportTrack("Jóga", "Björk", 300)
        assertEquals(1.0, playlistImportMatchScore(source, PlaylistImportTrack("Joga", "Bjork", 301)), 0.0001)
        assertEquals(0.0, playlistImportMatchScore(source, PlaylistImportTrack("Joga", "Other singer", 300)), 0.0)
        assertEquals(0.0, playlistImportMatchScore(source, PlaylistImportTrack("Joga (Live)", "Bjork", 300)), 0.0)
        assertEquals(0.0, playlistImportMatchScore(source, PlaylistImportTrack("Joga", "Bjork", 350)), 0.0)
        assertTrue(playlistImportMatchScore(source, PlaylistImportTrack("Joga remastered", "Bjork", 300)) < 0.95)
    }

    private fun assertFailure(reason: PlaylistImportError, block: () -> Unit) {
        try { block(); fail("Expected $reason") } catch (error: PlaylistImportException) { assertEquals(reason, error.reason) }
    }
}
