package com.metrolist.music.ui.player

import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class InitialMusicVideoSearchTest {
    @Test fun openingSearchesTheCapturedTitleOnceAndEditingDoesNotSearchAgain() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        val clock = BroadcastFrameClock()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + clock)
        val recomposer = Recomposer(scope.coroutineContext)
        scope.launch { recomposer.runRecomposeAndApplyChanges() }
        val songId = mutableStateOf("song-a")
        val query = mutableStateOf("  Captured song title  ")
        val searches = mutableListOf<Pair<String, String>>()
        val root = controller.get().window.decorView
        var time = 0L
        fun settle() = repeat(6) {
            root.measure(View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY))
            root.layout(0, 0, 360, 800)
            Snapshot.sendApplyNotifications()
            time += 16_666_667
            clock.sendFrame(time)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
        }
        try {
            controller.get().setContent(parent = recomposer) {
                val target = songId.value
                InitialMusicVideoSearch(target, query.value) { searches += target to it }
            }
            settle()
            assertEquals(listOf("song-a" to "Captured song title"), searches)
            query.value = "A user edit"
            settle()
            assertEquals("Editing or recomposing must not restart the initial request", 1, searches.size)
            songId.value = "song-b"
            query.value = "Next song"
            settle()
            assertEquals(listOf("song-a" to "Captured song title", "song-b" to "Next song"), searches)
            songId.value = "song-c"
            query.value = "abcdefghijk"
            settle()
            assertEquals("An eleven-character title must still initiate a search", "song-c" to "abcdefghijk", searches.last())
        } finally {
            controller.pause().stop().destroy()
            recomposer.cancel()
            scope.cancel()
        }
    }
}
