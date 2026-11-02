package com.metrolist.music.utils

import android.app.Application
import org.junit.Assert.*
import kotlinx.coroutines.flow.first
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ArtistConcertsTest {
    private val today = LocalDate.of(2026, 11, 2)
    private fun event(date: String, relations: String = """[{"type":"main performer","artist":{"name":"CAPO"}}]""") =
        """{"id":"11111111-1111-1111-1111-111111111111","name":"CAPO Live","life-span":{"begin":"$date"},"relations":$relations}"""
    private fun parse(vararg events: String) = ArtistConcerts.parse("""{"events":[${events.joinToString(",") }]}""", "CAPO", today)

    @Test fun upcomingPartialDatesStayPartialAndTodaysConcertIsIncluded() {
        assertEquals(listOf("2026-11-02"), parse(event("2026-11-02")).map { it.date })
        assertEquals(listOf("2026-12"), parse(event("2026-12")).map { it.date })
        assertEquals(listOf("2027"), parse(event("2027")).map { it.date })
        assertTrue(parse(event("2026-10"), event("2025"), event("2026-13"), event("2027-02-30")).isEmpty())
    }

    @Test fun onlyPerformersAreAcceptedAsTheRequestedArtist() {
        assertTrue(parse(event("2027-01-02", """[{"type":"photographer","artist":{"name":"CAPO"}}]""")).isEmpty())
        assertTrue(parse(event("2027-01-02", """[{"type":"main performer","artist":{"name":"Another Artist"}}]""")).isEmpty())
    }

    @Test fun providerEventLinksRequireValidIdsAndDuplicatesCollapse() {
        assertEquals(1, parse(event("2027-01-02"), event("2027-01-02")).size)
        assertTrue(parse(event("2027-01-02").replace("11111111-1111-1111-1111-111111111111", "../bad")).isEmpty())
        assertEquals("https://musicbrainz.org/event/11111111-1111-1111-1111-111111111111", parse(event("2027-01-02")).single().url)
    }
    @Test fun detailsUseOnlyPublishedCityVenueAndTime() {
        val relations = """[{"type":"main performer","artist":{"name":"CAPO"}},
            {"type":"held at","place":{"name":"E-Werk","area":{"name":"Köln","type":"City"}}}]"""
        val detailed = event("2027-01-02", relations).replace("\"name\":\"CAPO Live\"", "\"name\":\"CAPO Live\",\"time\":\"20:30:00\"")
        val concert = parse(detailed).single()
        assertEquals("Köln", concert.city)
        assertEquals("E-Werk", concert.place)
        assertEquals("20:30", concert.time)
        val missing = parse(event("2027-01-02")).single()
        assertEquals("", missing.city)
        assertEquals("", missing.time)
        assertEquals("", missing.place)
        assertNull(parse(event("2027-02")).single().day)
    }

    @Test fun ambiguousCurrentMonthAndYearAreNotClaimedAsUpcoming() {
        assertTrue(parse(event("2026"), event("2026-11")).isEmpty())
    }

    @Test fun savingAnEventPersistsAndRemovingItPreservesOtherSavedEvents() = kotlinx.coroutines.runBlocking {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val first = "11111111-1111-1111-1111-111111111111"
        val second = "22222222-2222-2222-2222-222222222222"
        ConcertPreferences.setSaved(context, first, true)
        ConcertPreferences.setSaved(context, second, true)
        assertTrue(context.dataStore.data.first()[ConcertPreferences.savedKey].orEmpty().containsAll(listOf(first, second)))
        ConcertPreferences.setSaved(context, first, false)
        ConcertPreferences.setSaved(context, "invalid", true)
        assertEquals(setOf(second), context.dataStore.data.first()[ConcertPreferences.savedKey])
        ConcertPreferences.setSaved(context, second, false)
    }

    @Test fun cachedProviderTitleWinsOverGlobalDisplayAlias() {
        val cached = com.metrolist.music.db.entities.serializeArtistPage(
            emptyList(), null, null, null, false,
            com.metrolist.innertube.models.ArtistItem("UC-provider", "CAPO", null, shuffleEndpoint = null, radioEndpoint = null),
        )
        val artist = com.metrolist.music.db.entities.ArtistEntity("UC-provider", "Mein Künstler", cachedPageJson = cached)
        assertEquals("CAPO", concertProviderArtistName(artist.id, artist.name, artist, mapOf(artist.id to artist.name)))
        assertEquals("CAPO", concertProviderArtistName(artist.id, "CAPO", artist.copy(cachedPageJson = null), mapOf(artist.id to artist.name)))
        assertEquals("", concertProviderArtistName(artist.id, artist.name, artist.copy(cachedPageJson = null), mapOf(artist.id to artist.name)))
        assertEquals("", concertProviderArtistName(artist.id, artist.name, artist.copy(cachedPageJson = "broken"), mapOf(artist.id to artist.name)))
    }

    @Test fun providerIdentityRejectsAnotherArtistsCacheButAcceptsLinkedChannel() {
        val cached = com.metrolist.music.db.entities.serializeArtistPage(
            emptyList(), null, null, null, false,
            com.metrolist.innertube.models.ArtistItem("UC-channel", "CAPO", null, shuffleEndpoint = null, radioEndpoint = null),
        )
        val local = com.metrolist.music.db.entities.ArtistEntity("local-artist", "Mein Künstler", channelId = "UC-channel", cachedPageJson = cached)
        assertEquals("CAPO", concertProviderArtistName(local.id, local.name, local, mapOf(local.id to local.name)))
        assertEquals("Other", concertProviderArtistName("other-id", "Other", local, emptyMap()))
        assertEquals("Unchanged", concertProviderArtistName(null, "Unchanged", null, emptyMap()))
    }

}
