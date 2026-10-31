package com.metrolist.music.utils
import org.junit.Assert.*
import org.junit.Test
class ShareCardTest {
    @Test fun embeddedLineBreaksCannotBypassTheThreeLineLimit() {
        assertEquals(listOf("one", "two", "three"), selectedShareLines(listOf("one\ntwo", "three\nfour"), setOf(0,1)))
    }
    @Test fun sharingNeverExposesLocalIdsAsYoutubeLinks() {
        assertNull(shareSongUrl("local:123"))
        assertNull(shareSongUrl("https://unsafe.example"))
        assertEquals("https://music.youtube.com/watch?v=abcdefghijk", shareSongUrl("abcdefghijk"))
    }
    @Test fun lyricsSelectionKeepsAtMostThreeOriginalLinesInOrder() {
        assertEquals(listOf("one", "two", "three"), selectedShareLines(listOf("one","two","three","four"), setOf(3,1,0,2)))
        assertTrue(selectedShareLines(listOf("one"), setOf(-1,10)).isEmpty())
    }
}
