package com.metrolist.music.ui.player

import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.SongItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class ManualMusicVideoSearchTest {
    private fun video(id: String) = SongItem(id = id, title = id, artists = emptyList(), thumbnail = "")

    @Test fun videoSearchTrimsQueryAndRejectsUnselectableOrDuplicateResults() = runBlocking {
        val result = searchManualMusicVideos("  artist title  ") { query ->
            assertEquals("artist title", query)
            listOf(video("abcdefghijk"), video("abcdefghijk"), video("https://example.com/video"),
                AlbumItem("album", "playlist", title = "Album", artists = null, year = null, thumbnail = ""),
                video("lmnopqrstuv"))
        }
        assertEquals(listOf("abcdefghijk", "lmnopqrstuv"), result.map { it.id })
    }

    @Test fun cancelledSearchDoesNotTurnIntoEmptySuccess() = runBlocking {
        try {
            searchManualMusicVideos("query") { throw CancellationException("closed") }
            fail("Cancellation must propagate")
        } catch (error: CancellationException) { assertEquals("closed", error.message) }
    }

    @Test fun failedSearchDoesNotTurnIntoEmptySuccess() = runBlocking {
        try {
            searchManualMusicVideos("query") { error("offline") }
            fail("Failure must reach the sheet")
        } catch (error: IllegalStateException) { assertEquals("offline", error.message) }
    }
}
