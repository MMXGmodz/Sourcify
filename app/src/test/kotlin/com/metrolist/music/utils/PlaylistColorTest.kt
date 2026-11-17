package com.metrolist.music.utils

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class PlaylistColorTest {
    @Test fun selectedColorsPersistPerPlaylistAndAutomaticResetKeepsOtherSettings() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val original = context.dataStore.data.first()
        val first = "LPcolor-one"
        val second = "LPcolor-two"
        val firstKey = playlistColorKey(first)
        val secondKey = playlistColorKey(second)
        val style = playlistCoverStyleKey(first)
        try {
            assertTrue(setClassicPlaylistCover(context, first, true))
            assertTrue(setPlaylistColor(context, first, 0xFF2574D9.toInt()))
            assertTrue(setPlaylistColor(context, second, 0xFFB12769.toInt()))
            var prefs = context.dataStore.data.first()
            assertEquals(0xFF2574D9.toInt(), readPlaylistColor(prefs[firstKey]))
            assertEquals(0xFFB12769.toInt(), readPlaylistColor(prefs[secondKey]))
            assertTrue(setPlaylistColor(context, first, null))
            prefs = context.dataStore.data.first()
            assertNull(readPlaylistColor(prefs[firstKey]))
            assertEquals(0xFFB12769.toInt(), readPlaylistColor(prefs[secondKey]))
            assertEquals(true, prefs[style])
        } finally {
            context.dataStore.edit { prefs ->
                listOf(firstKey, secondKey).forEach { key -> original[key]?.let { prefs[key] = it } ?: prefs.remove(key) }
                original[style]?.let { prefs[style] = it } ?: prefs.remove(style)
            }
        }
        Unit
    }

    @Test fun corruptValuesFallBackToAutomaticAndRemoteAliasesShareOneColor() {
        assertNull(readPlaylistColor(null))
        assertNull(readPlaylistColor("invalid"))
        assertNull(readPlaylistColor("4294967296"))
        assertEquals(0xFF123456.toInt(), readPlaylistColor(0x00123456.toString()))
        assertEquals(playlistColorKey("PLexample"), playlistColorKey("VLPLexample"))
        mapOf("LP_LIKED" to "auto:liked", "LP_DOWNLOADED" to "auto:downloaded", "LP_TOP" to "auto:top",
            "LP_CACHED" to "auto:cache", "LP_UPLOADED" to "auto:uploaded").forEach { (library, page) ->
            assertEquals(playlistColorKey(library), playlistColorKey(page))
        }
        assertNotEquals(playlistColorKey("auto:liked"), playlistColorKey("auto:downloaded"))
    }
}
