package com.metrolist.music.ui.player

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class, qualifiers = "w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CanvasBrightnessRenderTest {
    @Test fun imageAndVideoBecomeUndimmedInFullscreenAndRestoreSavedLighting() {
        for (video in listOf(false, true)) {
            val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            val root = controller.get().window.decorView
            val fullscreen = mutableStateOf(false)
            val clock = BroadcastFrameClock()
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined + clock)
            val recomposer = Recomposer(scope.coroutineContext)
            var time = 0L
            scope.launch { recomposer.runRecomposeAndApplyChanges() }
            try {
                controller.get().setContent(parent = recomposer) {
                    Box(Modifier.fillMaxSize().background(Color.White)) {
                        CanvasLightingOverlay(.4f, fullscreen.value, video)
                    }
                }
                fun capture(): List<Int> {
                    repeat(30) {
                        root.measure(View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY))
                        root.layout(0, 0, 360, 800)
                        Snapshot.sendApplyNotifications()
                        time += 16_666_667
                        clock.sendFrame(time)
                        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
                    }
                    val bitmap = Bitmap.createBitmap(360, 800, Bitmap.Config.ARGB_8888)
                    root.draw(Canvas(bitmap))
                    return listOf(160, 400, 640).map { android.graphics.Color.red(bitmap.getPixel(180, it)) }
                        .also { bitmap.recycle() }
                }
                val normal = capture()
                assertTrue("Saved lighting must visibly dim the source", normal.all { it in 1..110 })
                fullscreen.value = true
                val bright = capture()
                assertTrue("Fullscreen removes uniform dimming and video gradient", bright.all { it >= 254 })
                fullscreen.value = false
                val restored = capture()
                assertTrue("Exiting fullscreen restores saved lighting", restored.zip(normal).all { (a, b) -> kotlin.math.abs(a - b) <= 1 })
            } finally {
                controller.pause().stop().destroy()
                recomposer.cancel()
                scope.cancel()
            }
        }
    }
}
