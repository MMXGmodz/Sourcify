package com.metrolist.music.utils

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class CanvasSongPreferenceTest {
    @Test fun disablingOneSongSurvivesRestartWithoutDeletingItsChosenClip() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = CanvasVideoStore(context)
        val song = "song-specific-canvas"
        val clip = CanvasSelection(videoId = "abcdefghijk", startMs = 2000, endMs = 10000)
        store.saveClip(song, clip)
        store.setEnabled(song, false)
        val reopened = CanvasVideoStore(context)
        assertFalse(reopened.isEnabled(song))
        assertTrue(reopened.isEnabled("different-song"))
        assertEquals(clip, reopened.selection(song))
        reopened.setEnabled(song, true)
        assertEquals(clip, reopened.selection(song))
    }
}
