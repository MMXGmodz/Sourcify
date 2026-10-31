package com.metrolist.music.ui.player

import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class, qualifiers = "w412dp-h892dp-xhdpi")
class LyricsLineInteractionTest {
    @Test fun tappingTimedLineSeeksButUntimedLineDoesNothing() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        val activity = controller.get()
        var seek = -1L
        var timedY = 0f
        var untimedY = 0f
        var headerY = 0f
        var collapsed = 0
        try {
            activity.setContent {
                MaterialTheme {
                    Column(Modifier.fillMaxSize().padding(top = 100.dp)) {
                        Box(Modifier.fillMaxWidth().onGloballyPositioned { timedY = it.positionInWindow().y + it.size.height / 2f }) {
                            FullscreenLyricsLine("Timed verse", false, true) { seek = lyricsSeekPosition(10_000, 500, true, true, false)!! }
                        }
                        Box(Modifier.fillMaxWidth().onGloballyPositioned { untimedY = it.positionInWindow().y + it.size.height / 2f }) {
                            FullscreenLyricsLine("Untimed verse", false, false) { seek = 0 }
                        }
                        Box(Modifier.fillMaxWidth().height(100.dp).onGloballyPositioned { headerY = it.positionInWindow().y }) {
                            PlayerScrollHeader(com.metrolist.music.models.MediaMetadata("track", "Song", emptyList(), 180),
                                .99f, androidx.compose.ui.graphics.Color.Black, false, false, .2f, { collapsed++ }, {}, {}, {})
                        }
                    }
                }
            }
            val root = activity.window.decorView
            repeat(2) {
                root.measure(View.MeasureSpec.makeMeasureSpec(824, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1784, View.MeasureSpec.EXACTLY))
                root.layout(0, 0, 824, 1784)
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
            }
            fun tap(y: Float, x: Float = 200f) {
                val time = SystemClock.uptimeMillis()
                listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP).forEach { action ->
                    MotionEvent.obtain(time, time + if (action == MotionEvent.ACTION_UP) 50 else 0, action, x, y, 0).also { root.dispatchTouchEvent(it); it.recycle() }
                }
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
            }
            tap(headerY + 92f, 64f)
            assertEquals("Invisible outgoing arrow must not collapse the player", 0, collapsed)
            tap(timedY)
            assertEquals(9_500L, seek)
            tap(untimedY)
            assertEquals(9_500L, seek)
        } finally { controller.pause().stop().destroy() }
    }
}
