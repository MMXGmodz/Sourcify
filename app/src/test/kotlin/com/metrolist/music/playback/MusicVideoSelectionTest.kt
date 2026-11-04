package com.metrolist.music.playback

import android.app.Application
import android.net.Uri
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class MusicVideoSelectionTest {
    @Test fun acceptsOnlyDirectIdsAndTrustedYouTubeVideoLinks() {
        val id = "abcdefghijk"
        for (input in listOf(id, "https://youtu.be/$id?t=5", "https://www.youtube.com/watch?v=$id&list=ignored",
            "https://music.youtube.com/watch?v=$id", "https://youtube.com/shorts/$id", "https://youtube.com/embed/$id")) {
            assertEquals(input, id, musicVideoId(input))
        }
        for (input in listOf("https://youtube.com.evil.test/watch?v=$id", "https://evil.test/watch?v=$id",
            "file:///watch?v=$id", "javascript:$id", "https://youtube.com/playlist?list=$id", "bad", "", "https://youtube.com/watch?v=bad")) {
            assertNull(input, musicVideoId(input))
        }
    }

    @Test fun savedChoicesArePerSongSurviveRecreationAndResetToAutomatic() {
        val context = RuntimeEnvironment.getApplication()
        val store = MusicVideoSelectionStore(context)
        val youtube = MusicVideoSelection.YouTube("abcdefghijk")
        val local = MusicVideoSelection.Local(Uri.parse("content://media/external/video/123"), 90_000)
        store.set("song-a", youtube)
        store.set("song-b", local)
        val reopened = MusicVideoSelectionStore(context)
        assertEquals(youtube, reopened.get("song-a"))
        assertEquals(local, reopened.get("song-b"))
        store.set("song-a", null)
        assertNull(reopened.get("song-a"))
        assertEquals(local, reopened.get("song-b"))
    }

    @Test fun rejectsUnsafeSourcesBeforeReplacingAPreviousChoice() {
        val store = MusicVideoSelectionStore(RuntimeEnvironment.getApplication())
        val previous = MusicVideoSelection.YouTube("abcdefghijk")
        store.set("song-safe", previous)
        val invalid = listOf(MusicVideoSelection.YouTube("https://evil.test"),
            MusicVideoSelection.Local(Uri.parse("https://evil.test/video"), 1_000),
            MusicVideoSelection.Local(Uri.parse("file:///private/file"), 1_000),
            MusicVideoSelection.Local(Uri.parse("content://media/video/1"), 0))
        for (selection in invalid) {
            assertThrows(IllegalArgumentException::class.java) { store.set("song-safe", selection) }
            assertEquals(previous, store.get("song-safe"))
        }
    }
}
