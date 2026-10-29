package com.metrolist.music.utils

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class CanvasVideoStoreTest {
    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("sourcify_canvas_clips", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("sourcify_canvas", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun selectionPersistsForItsSongAndRemovalPreservesOtherSongs() {
        val first = Uri.parse("content://videos/first")
        val second = Uri.parse("content://videos/second")
        CanvasVideoStore(context).apply {
            set("first-song", first)
            set("second-song", second)
        }
        val reopened = CanvasVideoStore(context)
        assertEquals(first, reopened.get("first-song"))
        assertEquals(second, reopened.get("second-song"))
        assertNull(reopened.get("song-without-canvas"))
        reopened.remove("first-song")
        assertNull(reopened.get("first-song"))
        assertEquals(second, reopened.get("second-song"))
    }

    @Test
    fun chosenVideoAndClipSurviveReopenAndLocalReplacementClearsRemoteChoice() {
        val store = CanvasVideoStore(context)
        val remote = CanvasSelection(videoId = "abcdefghijk", startMs = 32_000, endMs = 40_000)
        store.saveClip("song", remote)
        assertEquals(remote, CanvasVideoStore(context).selection("song"))
        val local = CanvasSelection(uri = Uri.parse("content://videos/clip"), startMs = 1_000, endMs = 9_000)
        store.saveClip("song", local)
        assertEquals(local, CanvasVideoStore(context).selection("song"))
        store.remove("song")
        assertNull(CanvasVideoStore(context).selection("song"))
    }

    @Test
    fun clipsStayWithinVideoAndShortVideosUseTheirWholeDuration() {
        assertEquals(0L until 3_000L, canvasClip(99_000, 3_000))
        assertEquals(12_000L until 20_000L, canvasClip(99_000, 20_000))
        assertEquals(0L until 8_000L, canvasClip(-200, 20_000))
    }

    @Test(expected = IllegalArgumentException::class)
    fun refusesClipLongerThanEightSeconds() {
        CanvasVideoStore(context).saveClip("song", CanvasSelection(videoId = "abcdefghijk", endMs = 9_000))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsRemoteUrls() {
        CanvasVideoStore(context).set("song", Uri.parse("https://example.com/video.mp4"))
    }
}
