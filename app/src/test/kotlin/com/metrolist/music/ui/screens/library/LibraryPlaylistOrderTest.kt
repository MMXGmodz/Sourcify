package com.metrolist.music.ui.screens.library

import com.metrolist.music.db.entities.PlaylistEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LibraryPlaylistOrderTest {
    private data class Entry(val id: String, val playlist: Boolean = true)

    @Test
    fun `pins lead built-ins and other library content while preserving the selected sort`() {
        val entries = listOf(
            Entry(PlaylistEntity.LIKED_PLAYLIST_ID),
            Entry("album", playlist = false),
            Entry("zeta"),
            Entry("alpha"),
            Entry("middle"),
        )
        val pinned = setOf("alpha", "middle", "album")

        assertEquals(
            listOf("alpha", "middle", PlaylistEntity.LIKED_PLAYLIST_ID, "album", "zeta"),
            entries.pinnedPlaylistsFirst(pinned) { if (it.playlist) it.id else null }.map { it.id },
        )
        assertEquals(
            listOf("middle", "alpha", "zeta", "album", PlaylistEntity.LIKED_PLAYLIST_ID),
            entries.reversed().pinnedPlaylistsFirst(pinned) { if (it.playlist) it.id else null }.map { it.id },
        )
    }

    @Test
    fun `unpinning restores the selected sort and unknown pin IDs do not change it`() {
        val entries = listOf("liked", "beta", "alpha")
        assertEquals(entries, entries.pinnedPlaylistsFirst(emptySet()) { it })
        assertEquals(entries, entries.pinnedPlaylistsFirst(setOf("removed")) { it })
        assertEquals(listOf("liked", "alpha", "beta"), entries.pinnedPlaylistsFirst(setOf("liked", "alpha")) { it })
    }

    @Test
    fun `built-in pins open their original destination and respect the top size`() {
        assertEquals("auto_playlist/liked", libraryPlaylistRoute(PlaylistEntity.LIKED_PLAYLIST_ID))
        assertEquals("auto_playlist/downloaded", libraryPlaylistRoute(PlaylistEntity.DOWNLOADED_PLAYLIST_ID))
        assertEquals("cache_playlist/cached", libraryPlaylistRoute(CACHED_LIBRARY_PLAYLIST_ID))
        assertEquals("auto_playlist/uploaded", libraryPlaylistRoute(UPLOADED_LIBRARY_PLAYLIST_ID))
        assertEquals("top_playlist/100", libraryPlaylistRoute(TOP_LIBRARY_PLAYLIST_ID, topSize = 100))
        assertNull(libraryPlaylistRoute("LP_user_playlist"))
    }
}
