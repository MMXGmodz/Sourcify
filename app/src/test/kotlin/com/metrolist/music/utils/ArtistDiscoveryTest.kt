package com.metrolist.music.utils

import android.app.Application
import com.metrolist.music.models.MediaMetadata
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ArtistDiscoveryTest {
 @Test fun blocksCollaboratorsAndMissingIdsButNotDifferentArtistWithSameName() {
  val blocked = mapOf("artist1" to "CAPO")
  assertTrue(ArtistBlocks.matches(blocked, listOf(MediaMetadata.Artist("other", "Other"), MediaMetadata.Artist("artist1", "CAPO"))))
  assertTrue(ArtistBlocks.matches(blocked, listOf(MediaMetadata.Artist(null, " capo "))))
  assertFalse(ArtistBlocks.matches(blocked, listOf(MediaMetadata.Artist("different", "CAPO"))))
  assertFalse(ArtistBlocks.matches(emptyMap(), listOf(MediaMetadata.Artist("artist1", "CAPO"))))
 }
 @Test fun missingBlankArtistNameDoesNotBlockEveryUnknownArtist() {
  assertFalse(ArtistBlocks.matches(mapOf("known" to ""), listOf(MediaMetadata.Artist(null, ""))))
 }
 @Test fun artistFavoritesIncludeLikedSongsThatAreNotInTheLibrary() {
  val song = com.metrolist.music.db.entities.Song(
    com.metrolist.music.db.entities.SongEntity("liked", "Liked", liked = true, inLibrary = null),
    listOf(com.metrolist.music.db.entities.ArtistEntity("artist1", "CAPO")))
  assertEquals(listOf(song), artistFavoriteSongs(listOf(song), "artist1"))
  assertTrue(artistFavoriteSongs(listOf(song), "other").isEmpty())
 }
 @Test fun blockPersistenceRoundTripAndInvalidData() {
  assertEquals(mapOf("a" to "Name"), ArtistBlocks.decode(ArtistBlocks.encode(mapOf("a" to "Name"))))
  assertTrue(ArtistBlocks.decode("broken").isEmpty())
 }
 @Test fun seedsIncludeListeningStatsAndRecentHistoryWithLikedFallback() {
  assertEquals(listOf("top", "recent", "liked"), historySeedIds(listOf("top"), listOf("recent", "top"), listOf("liked"), 5))
  assertEquals(listOf("liked"), historySeedIds(emptyList(), emptyList(), listOf("liked"), 5))
 }
 @Test fun concertsRejectPastCancelledAndUnrelatedArtists() {
  val data = """{"events":[
   {"id":"11111111-1111-1111-1111-111111111111","name":"CAPO Live","life-span":{"begin":"2027-01-02"},"relations":[{"artist":{"name":"CAPO"}}]},
   {"id":"22222222-2222-2222-2222-222222222222","name":"Old","life-span":{"begin":"2025-01-02"},"relations":[{"artist":{"name":"CAPO"}}]},
   {"id":"33333333-3333-3333-3333-333333333333","name":"Other","life-span":{"begin":"2027-01-02"},"relations":[{"artist":{"name":"Someone Else"}}]},
   {"id":"44444444-4444-4444-4444-444444444444","name":"Cancelled","cancelled":true,"life-span":{"begin":"2027-01-02"},"relations":[{"artist":{"name":"CAPO"}}]}
  ]}"""
  val result = ArtistConcerts.parse(data, "CAPO", LocalDate.of(2026, 11, 2))
  assertEquals(listOf("CAPO Live"), result.map { it.title })
 }
}
