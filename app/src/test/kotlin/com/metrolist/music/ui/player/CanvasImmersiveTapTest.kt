package com.metrolist.music.ui.player

import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import com.metrolist.music.models.MediaMetadata
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
class CanvasImmersiveTapTest {
    @Test fun emptyVideoTapExitsButPlaybackAndFavoriteButtonsKeepTheirActions() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        val activity = controller.get()
        var exits = 0; var likes = 0; var plays = 0
        try {
            activity.setContent { MaterialTheme {
                CanvasFullscreenControls(MediaMetadata("song", "Song", emptyList(), 180), false, false, .3f,
                    { exits++ }, { likes++ }, { plays++ })
            } }
            val root = activity.window.decorView
            repeat(2) {
                root.measure(View.MeasureSpec.makeMeasureSpec(824, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1784, View.MeasureSpec.EXACTLY))
                root.layout(0, 0, 824, 1784)
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
            }
            fun tap(x: Float, y: Float) {
                val time = SystemClock.uptimeMillis()
                listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP).forEach { action ->
                    MotionEvent.obtain(time, time + if (action == MotionEvent.ACTION_UP) 50 else 0, action, x, y, 0).also { root.dispatchTouchEvent(it); it.recycle() }
                }
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
            }
            tap(400f, 600f)
            assertEquals("One tap on the video returns from fullscreen", 1, exits)
            tap(648f, 1650f)
            tap(744f, 1650f)
            assertEquals(1, likes); assertEquals(1, plays)
            assertEquals("Playback controls must not bubble to the exit gesture", 1, exits)
        } finally { controller.pause().stop().destroy() }
    }
}
