package com.metrolist.music.ui.player

import org.junit.Assert.*
import org.junit.Test

class PlayerContextDataTest {
    @Test fun creditsKeepConfirmedRolesAndCopyrightSourcesWithoutGuessingUnknownFields() {
        val data = parseSongCredits("Provided to YouTube by Label\nComposer: Person A\nLyricist: Person B\nMixing Engineer: Person C\nMastering Engineer: Person D\nUnknown: Person E\n℗ 2026 Example Label")
        assertEquals(4, data.entries.size)
        assertEquals(2, data.entries.count { it.group == CreditGroup.COMPOSITION })
        assertEquals(2, data.entries.count { it.group == CreditGroup.PRODUCTION })
        assertEquals(listOf("℗ 2026 Example Label"), data.sources)
        assertTrue(data.entries.none { it.name == "Person E" })
    }
    @Test fun previewCombinesArtistAndWriterRolesWithoutDuplicateRows() {
        val data = parseSongCredits("Lyricist: Artist A\nComposer: Producer B\nLyricist: Artist A")
        val rows = previewCreditRows(listOf("Artist A"), data, "Hauptkünstler*in")
        assertEquals(2, rows.size)
        assertEquals("Hauptkünstler*in · Texter*in", rows.first().second)
    }
    @Test fun missingCreditsAreEmptyAndMalformedLinesCannotInventCredits() {
        assertTrue(parseSongCredits(null).entries.isEmpty())
        assertTrue(parseSongCredits("Producer:\nPlease follow me\nLink: https://example.com").entries.isEmpty())
    }
    @Test fun combinedComposerLyricistRoleRemainsACombinedCredit() {
        assertEquals("Komposition · Songtext", parseSongCredits("Composer, Lyricist: Artist A").entries.single().role)
    }
}
