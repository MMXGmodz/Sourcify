package com.metrolist.music.ui.player

import com.metrolist.music.constants.HiddenSongLyricsKey
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.safeDataStoreEdit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class LyricsVisibilityTest {
    @Test fun visibilityEditsStayPerSongAndCanBeReversedWithoutChangingOtherSongs() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val original = context.dataStore.data.first()[HiddenSongLyricsKey]
        try {
            assertTrue(setSongLyricsVisible(context, "incorrect-lyrics", false))
            assertTrue(setSongLyricsVisible(context, "other-song", false))
            assertTrue(setSongLyricsVisible(context, "incorrect-lyrics", false))
            val hidden = context.dataStore.data.first()[HiddenSongLyricsKey].orEmpty()
            assertTrue(hidden.containsAll(listOf("incorrect-lyrics", "other-song")))
            assertFalse("Songs are visible by default", "untouched-song" in hidden)
            assertTrue(setSongLyricsVisible(context, "incorrect-lyrics", true))
            val restored = context.dataStore.data.first()[HiddenSongLyricsKey].orEmpty()
            assertFalse("incorrect-lyrics" in restored)
            assertTrue("other-song" in restored)
        } finally {
            context.safeDataStoreEdit { if (original == null) it.remove(HiddenSongLyricsKey) else it[HiddenSongLyricsKey] = original }
        }
    }
}
