package com.metrolist.music.ui.player

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.IconButton
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.unit.dp
import android.content.pm.ActivityInfo
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class, qualifiers = "w720dp-h360dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlayerLayoutRenderTest {
    @Test fun unavailableLyricsReserveNoSpaceBeforeTheNextCard() {
        var nextY = -1f
        render {
            Column {
                LyricsPreviewCard(emptyList(), Color.DarkGray, {})
                Text("Next card", Modifier.onGloballyPositioned { nextY = it.positionInWindow().y })
            }
        }
        assertEquals(0f, nextY, 1f)
    }

    @Test fun canvasLocksPortraitOnlyWhileItIsActive() {
        var active by mutableStateOf(true)
        render(check = { activity, settle ->
            assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, activity.requestedOrientation)
            active = false
            settle()
            assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, activity.requestedOrientation)
            active = true
            settle()
            assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, activity.requestedOrientation)
        }) { KeepCanvasPlayerPortrait(active) }
    }

    @Test fun landscapeContentClearsTheHeaderAndItsButtonsStayInTheirOwnPane() {
        var artwork = Rect.Zero
        var title = Rect.Zero
        val buttons = mutableMapOf<Int, Rect>()
        render {
            PlayerLandscapeLayout(artwork = {
                androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().onGloballyPositioned { artwork = it.boundsInWindow() })
            }, controls = {
                Text("A very long song title with featured artists", maxLines = 1,
                    modifier = Modifier.fillMaxWidth().onGloballyPositioned { title = it.boundsInWindow() })
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    repeat(5) { index ->
                        IconButton(onClick = {}, modifier = Modifier.size(if (index == 2) 64.dp else 48.dp)
                            .onGloballyPositioned { buttons[index] = it.boundsInWindow() }) { Text("$index") }
                    }
                }
            })
        }
        assertTrue(artwork.top >= 66f)
        assertTrue(title.top >= 66f)
        assertTrue(artwork.right <= title.left)
        assertEquals(5, buttons.size)
        val ordered = buttons.toSortedMap().values.toList()
        ordered.forEach { assertTrue(it.left >= title.left && it.right <= title.right && it.top >= title.bottom) }
        ordered.zipWithNext().forEach { (left, right) -> assertTrue(left.right <= right.left) }
    }

    private fun render(check: (ComponentActivity, () -> Unit) -> Unit = { _, _ -> }, content: @Composable () -> Unit) {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        val clock = BroadcastFrameClock()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined + clock)
        val recomposer = Recomposer(scope.coroutineContext)
        scope.launch { recomposer.runRecomposeAndApplyChanges() }
        try {
            controller.get().setContent(parent = recomposer, content = content)
            val root = controller.get().findViewById<View>(android.R.id.content)
            val bitmap = Bitmap.createBitmap(720, 360, Bitmap.Config.ARGB_8888)
            var frame = 0L
            fun settle() { repeat(10) {
                root.requestLayout()
                root.measure(View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY))
                root.layout(0, 0, 720, 360)
                Snapshot.sendApplyNotifications()
                clock.sendFrame(++frame * 16_666_667L)
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
                root.draw(Canvas(bitmap))
            }
            }
            settle()
            check(controller.get(), ::settle)
            bitmap.recycle()
        } finally {
            controller.pause().stop().destroy()
            recomposer.cancel()
            scope.cancel()
        }
    }
}
