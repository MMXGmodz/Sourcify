package com.metrolist.music.ui.tv

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import com.metrolist.music.ui.theme.MetrolistTheme
import kotlinx.coroutines.*
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class, qualifiers = "w960dp-h540dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TvFocusTest {
    @Test fun dpadMovesFocusAndOkActivatesOnlyTheFocusedTvButton() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible().windowFocusChanged(true)
        val activity = controller.get()
        val clock = BroadcastFrameClock()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined + clock)
        val recomposer = Recomposer(scope.coroutineContext)
        scope.launch { recomposer.runRecomposeAndApplyChanges() }
        var frame = 0L
        var first = 0
        var second = 0
        try {
            activity.setContent(parent = recomposer) {
                MetrolistTheme(darkTheme = true) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        Box(Modifier.fillMaxSize()) {
                            val focus = remember { FocusRequester() }
                            val inputMode = LocalInputModeManager.current
                            Row(Modifier.padding(40.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                                TvActionButton("Search", { first++ }, Modifier.focusRequester(focus))
                                TvActionButton("Library", { second++ })
                            }
                            LaunchedEffect(Unit) { withFrameNanos { }; inputMode.requestInputMode(InputMode.Keyboard); focus.requestFocus() }
                        }
                    }
                }
            }
            val root = activity.window.decorView
            fun advance() {
                repeat(20) {
                    root.measure(View.MeasureSpec.makeMeasureSpec(960, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(540, View.MeasureSpec.EXACTLY))
                    root.layout(0, 0, 960, 540)
                    Snapshot.sendApplyNotifications()
                    frame += 16_666_667L
                    clock.sendFrame(frame)
                    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32))
                }
            }
            fun press(code: Int) {
                activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
                activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
                advance()
            }
            advance()
            press(KeyEvent.KEYCODE_DPAD_CENTER)
            assertEquals("Initial focus must activate search without touch", 1, first)
            press(KeyEvent.KEYCODE_DPAD_RIGHT)
            press(KeyEvent.KEYCODE_DPAD_CENTER)
            assertEquals("Right must leave the first button", 1, first)
            assertEquals("OK must activate the newly focused button", 1, second)
            press(KeyEvent.KEYCODE_DPAD_LEFT)
            press(KeyEvent.KEYCODE_ENTER)
            assertEquals("Left and Enter must return to the original control", 2, first)
            var selected = -1
            activity.setContent(parent = recomposer) {
                MetrolistTheme(darkTheme = true) {
                    Box(Modifier.fillMaxSize().padding(40.dp)) {
                        val focus = remember { FocusRequester() }
                        val inputMode = LocalInputModeManager.current
                        LazyColumn(Modifier.width(180.dp).height(180.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items((0..7).toList()) { index ->
                                TvActionButton("Menu $index", { selected = index },
                                    Modifier.fillMaxWidth().then(if (index == 0) Modifier.focusRequester(focus) else Modifier))
                            }
                        }
                        LaunchedEffect(Unit) { withFrameNanos { }; inputMode.requestInputMode(InputMode.Keyboard); focus.requestFocus() }
                    }
                }
            }
            advance()
            repeat(7) { press(KeyEvent.KEYCODE_DPAD_DOWN) }
            press(KeyEvent.KEYCODE_DPAD_CENTER)
            assertEquals("Dpad must reach menu items outside the original viewport", 7, selected)
            val bitmap = Bitmap.createBitmap(960, 540, Bitmap.Config.ARGB_8888)
            root.draw(Canvas(bitmap))
            File("build/ui-previews/tv-focus.png").apply { parentFile?.mkdirs() }.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        } finally {
            controller.pause().stop().destroy()
            recomposer.cancel(); scope.cancel()
        }
    }
}
