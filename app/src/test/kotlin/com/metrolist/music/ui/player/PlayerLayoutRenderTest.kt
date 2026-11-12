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
import androidx.compose.ui.unit.sp
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

    @Test fun landscapeArtworkFillsTheScreenAndControlsSitAcrossTheBottom() {
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
        assertEquals(0f, artwork.top, 1f)
        assertEquals(720f, artwork.width, 1f)
        assertEquals(360f, artwork.height, 1f)
        assertTrue(title.top > 180f)
        assertEquals(0f, title.left, 1f)
        assertEquals(720f, title.width, 1f)
        assertEquals(5, buttons.size)
        val ordered = buttons.toSortedMap().values.toList()
        ordered.forEach { assertTrue(it.left >= title.left && it.right <= title.right && it.top >= title.bottom) }
        ordered.zipWithNext().forEach { (left, right) -> assertTrue(left.right <= right.left) }
    }

    @Test
    @Config(qualifiers = "w640dp-h360dp-xhdpi")
    @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
    fun landscapeReferenceRendersItsRealMetadataAndTransportComponents() {
        var titleBounds = Rect.Zero
        var progressBounds = Rect.Zero
        var transportBounds = Rect.Zero
        render(width = 1280, height = 720, snapshot = "landscape-reference.png") {
            val song = com.metrolist.music.models.MediaMetadata("fixture", "Ok Alright (feat. ScHoolboy Q)",
                listOf(com.metrolist.music.models.MediaMetadata.Artist("artist", "Travis Scott")), 417)
            androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
                PlayerLandscapeLayout(artwork = { LandscapeArtworkBackdrop(null, Color(0xFF163C2A)) }, controls = {
                    androidx.compose.foundation.layout.Box(Modifier.onGloballyPositioned { titleBounds = it.boundsInWindow() }) {
                        LandscapeTrackRow(com.metrolist.music.R.drawable.small_icon, song.title, false, {}, {}, {}) {
                            Text("Travis Scott", fontSize = 10.sp, lineHeight = 12.sp, color = Color.White.copy(alpha = .75f))
                        }
                    }
                    val slider = remember { androidx.compose.material3.SliderState(.24f, trackRange = 0f..1f) }
                    androidx.compose.material3.Slider(state = slider, onValueChange = {}, onValueChangeFinished = {},
                        thumb = {}, track = { state -> com.metrolist.music.ui.component.PlayerSliderTrack(state, trackHeight = 2.dp,
                            colors = androidx.compose.material3.SliderDefaults.colors(activeTrackColor = Color.White, inactiveTrackColor = Color.White.copy(alpha = .3f))) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp).height(24.dp).offset(y = 6.dp)
                            .onGloballyPositioned { progressBounds = it.boundsInWindow() })
                    Row(Modifier.fillMaxWidth().padding(horizontal = 36.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("01:35", color = Color.White, fontSize = 10.sp, lineHeight = 12.sp)
                        Text("06:57", color = Color.White, fontSize = 10.sp, lineHeight = 12.sp)
                    }
                    androidx.compose.foundation.layout.Box(Modifier.onGloballyPositioned { transportBounds = it.boundsInWindow() }) {
                        LandscapeTransport(com.metrolist.music.R.drawable.pause, "Pause", com.metrolist.music.constants.PlayButtonStyle.STANDARD,
                            Color.Green, false, 0, true, true, false, {}, {}, {}, {}, {})
                    }
                })
                PlayerScrollHeader(song, 0f, Color.Transparent, false, true, .24f, {}, {}, {}, more = {
                    IconButton(onClick = {}) { Text("•••", color = Color.White) }
                }, landscape = true)
            }
        }
        assertTrue(titleBounds.top > 350f)
        assertTrue(titleBounds.bottom <= progressBounds.bottom)
        assertEquals(1280f, titleBounds.width, 1f)
        assertEquals(640f, transportBounds.center.x, 1f)
        assertTrue(transportBounds.width < 600f)
        assertTrue(transportBounds.bottom <= 720f)
    }

    private fun render(check: (ComponentActivity, () -> Unit) -> Unit = { _, _ -> }, width: Int = 720, height: Int = 360, snapshot: String? = null, content: @Composable () -> Unit) {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        val clock = BroadcastFrameClock()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined + clock)
        val recomposer = Recomposer(scope.coroutineContext)
        scope.launch { recomposer.runRecomposeAndApplyChanges() }
        try {
            controller.get().setContent(parent = recomposer) { androidx.compose.material3.MaterialTheme { content() } }
            val root = controller.get().findViewById<View>(android.R.id.content)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            var frame = 0L
            fun settle() { repeat(10) {
                root.requestLayout()
                root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                root.layout(0, 0, width, height)
                Snapshot.sendApplyNotifications()
                clock.sendFrame(++frame * 16_666_667L)
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
                root.draw(Canvas(bitmap))
            }
            }
            settle()
            check(controller.get(), ::settle)
            if (snapshot != null) java.io.File("build/ui-previews/$snapshot").apply { parentFile?.mkdirs() }
                .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        } finally {
            controller.pause().stop().destroy()
            recomposer.cancel()
            scope.cancel()
        }
    }
}
