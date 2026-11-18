package com.metrolist.music.utils
import org.junit.Assert.*
import org.junit.Test
class ShareCardTest {
    @Test fun embeddedLineBreaksCannotBypassTheFiveLineLimit() {
        assertEquals(listOf("one", "two", "three", "four", "five"), selectedShareLines(listOf("one\ntwo\nthree", "four\nfive\nsix"), setOf(0,1)))
    }
    @Test fun sharingNeverExposesLocalIdsAsYoutubeLinks() {
        assertNull(shareSongUrl("local:123"))
        assertNull(shareSongUrl("https://unsafe.example"))
        assertEquals("https://music.youtube.com/watch?v=abcdefghijk", shareSongUrl("abcdefghijk"))
    }
    @Test fun lyricsSelectionKeepsAtMostFiveOriginalLinesInOrder() {
        assertEquals(listOf("one", "two", "three", "four", "five"), selectedShareLines(listOf("one","two","three","four","five","six"), setOf(5,3,1,0,4,2)))
        assertTrue(selectedShareLines(listOf("one"), setOf(-1,10)).isEmpty())
    }
}
