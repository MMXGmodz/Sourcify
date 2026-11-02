package com.metrolist.music.utils

import android.net.Uri
import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class ArtistLinksTest {
    @Test fun channelAndArtistBrowseLinksKeepArtistId() {
        listOf(
            "https://music.youtube.com/channel/UC_CAPO-123",
            "https://music.youtube.com/channel/UC_CAPO-123/?si=share",
            "https://www.youtube.com/channel/UC_CAPO-123/releases?feature=shared",
            "https://music.youtube.com/browse/UC_CAPO-123",
            "https://youtube.com/c/UC_CAPO-123",
        ).forEach { assertEquals(it, "artist/UC_CAPO-123", artistRouteFromLink(Uri.parse(it))) }
    }
    @Test fun artistShareTextAndViewIntentBothReachTheArtist() {
        val link = "https://music.youtube.com/channel/UC_CAPO-123?si=shared"
        val sent = Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_TEXT, "CAPO\n$link")
        val viewed = Intent(Intent.ACTION_VIEW, Uri.parse(link))
        listOf(sent, viewed).forEach { incoming ->
            assertEquals("artist/UC_CAPO-123", artistRouteFromLink(requireNotNull(externalLinkUri(incoming))))
        }
        assertNull(externalLinkUri(Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_TEXT, "CAPO")))
        assertNull(externalLinkUri(Intent(Intent.ACTION_VIEW, Uri.parse("opaque:invalid"))))
    }
    @Test fun nonArtistAndMalformedLinksAreNotArtistRoutes() {
        listOf(
            "https://music.youtube.com/browse/MPRE_album",
            "https://music.youtube.com/playlist?list=PL123",
            "https://music.youtube.com/watch?v=video",
            "https://music.youtube.com/channel/",
            "https://music.youtube.com/channel/UC%2Fbad",
            "https://music.youtube.com.evil.example/channel/UC123",
            "file://music.youtube.com/channel/UC123",
        ).forEach { assertNull(it, artistRouteFromLink(Uri.parse(it))) }
    }
}
