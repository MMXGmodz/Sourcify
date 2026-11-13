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
import androidx.compose.ui.semantics.getOrNull
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

    @Test
    @Config(qualifiers = "w412dp-h892dp-xhdpi")
    fun lyricsTransportIsCenteredAndGuestControlsCannotChangePlayback() {
        var guest by mutableStateOf(false)
        val clicks = intArrayOf(0, 0, 0)
        render(width = 824, height = 1784, snapshot = "lyrics-fullscreen-controls.png", check = { activity, settle ->
            val root = activity.findViewById<View>(android.R.id.content)
            fun owner(view: View): androidx.compose.ui.semantics.SemanticsOwner? {
                view.javaClass.methods.firstOrNull { it.name == "getSemanticsOwner" && it.parameterCount == 0 }?.let {
                    return it.invoke(view) as? androidx.compose.ui.semantics.SemanticsOwner
                }
                if (view is android.view.ViewGroup) for (index in 0 until view.childCount) owner(view.getChildAt(index))?.let { return it }
                return null
            }
            fun nodes(node: androidx.compose.ui.semantics.SemanticsNode): List<androidx.compose.ui.semantics.SemanticsNode> = listOf(node) + node.children.flatMap(::nodes)
            fun bounds(resource: Int): Rect {
                val description = activity.getString(resource)
                return nodes(requireNotNull(owner(root)).unmergedRootSemanticsNode).first {
                    it.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.ContentDescription)?.contains(description) == true
                }.boundsInWindow
            }
            val previous = bounds(com.metrolist.music.R.string.previous)
            val play = bounds(com.metrolist.music.R.string.pause)
            val next = bounds(com.metrolist.music.R.string.next)
            assertEquals(412f, play.center.x, 1f)
            assertTrue(previous.right < play.left && play.right < next.left)
            assertTrue(bounds(com.metrolist.music.R.string.s16_lyrics_options).right > 700f)
            fun tap(resource: Int) {
                val point = bounds(resource).center
                val time = android.os.SystemClock.uptimeMillis()
                listOf(android.view.MotionEvent.ACTION_DOWN, android.view.MotionEvent.ACTION_UP).forEach { action ->
                    android.view.MotionEvent.obtain(time, time + if (action == android.view.MotionEvent.ACTION_UP) 50 else 0,
                        action, point.x, point.y, 0).also { root.dispatchTouchEvent(it); it.recycle() }
                }
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
                settle()
            }
            tap(com.metrolist.music.R.string.previous); tap(com.metrolist.music.R.string.pause); tap(com.metrolist.music.R.string.next)
            assertArrayEquals(intArrayOf(1, 1, 1), clicks)
            guest = true; settle()
            tap(com.metrolist.music.R.string.previous); tap(com.metrolist.music.R.string.pause); tap(com.metrolist.music.R.string.next)
            assertArrayEquals("Room guests must not skip or pause the shared queue", intArrayOf(1, 1, 1), clicks)
        }) {
            Column(Modifier.fillMaxSize().padding(24.dp)) {
                LyricsFullscreenHeader(com.metrolist.music.R.drawable.small_icon, "I vs ME", "Celo, Abdi", "0:13 / 3:25", {}, {}, {})
                Spacer(Modifier.height(40.dp))
                Text("Lyrics", style = androidx.compose.material3.MaterialTheme.typography.headlineLarge)
                Spacer(Modifier.weight(1f))
                LyricsPlaybackControls(true, guest, true, true, { clicks[0]++ }, { clicks[1]++ }, { clicks[2]++ })
            }
        }
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
