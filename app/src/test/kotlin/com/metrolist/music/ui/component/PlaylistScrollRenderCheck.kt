package com.metrolist.music.ui.component

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.metrolist.music.ui.theme.LocalSongColor
import com.metrolist.music.ui.theme.MetrolistTheme
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import java.io.File
import java.time.Duration

internal class PlaylistScrollRenderCheck {
    fun titleCollapsesAndPlayStaysClickableAfterHeaderLeavesComposition() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        val activity = controller.get()
        // Supply frames explicitly: static bitmap draws do not advance Compose's animation/recomposition clock.
        val clock = BroadcastFrameClock()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined + clock)
        val recomposer = Recomposer(scope.coroutineContext)
        scope.launch { recomposer.runRecomposeAndApplyChanges() }
        var frameTime = 0L
        lateinit var list: LazyListState
        var playCalls = 0
        var searchCalls = 0
        var contentY = 0f
        var barBottom = 128f
        var scale = 2f
        var expectedBar = 0
        val cover = Color(0xFF304BB8)
        try {
            activity.setContent(parent = recomposer) {
                MetrolistTheme(darkTheme = true, themeColor = cover) {
                    CompositionLocalProvider(LocalSongColor provides cover) {
                        Surface(Modifier.fillMaxSize()) {
                            Box(Modifier.fillMaxSize()) {
                                list = rememberLazyListState()
                                val density = LocalDensity.current
                                scale = density.density
                                barBottom = WindowInsets.statusBars.getTop(density) + with(density) { 64.dp.toPx() }
                                expectedBar = lerp(MaterialTheme.colorScheme.background, cover, 0.52f).toArgb()
                                PlaylistCollapsingContainer(list, "Lieblingssongs", true, {}, { searchCalls++ },
                                    onPlay = { playCalls++ }, modifier = Modifier.onGloballyPositioned { contentY = it.positionInWindow().y }) {
                                    LazyColumn(state = list, contentPadding = PaddingValues(top = 64.dp)) {
                                        item(key = "playlist_header") {
                                            Column(Modifier.fillMaxWidth().playlistHeaderAnchor()) {
                                                PlaylistArtworkHeader("Lieblingssongs") {
                                                    Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(cover, Color(0xFF86A2EF)))))
                                                }
                                                Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.End) {
                                                    Surface(onClick = { playCalls++ }, color = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(58.dp).playlistPlayAnchor()) {
                                                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("▶") }
                                                    }
                                                }
                                            }
                                        }
                                        items(40) { index -> Text("Song $index", Modifier.fillMaxWidth().height(72.dp).padding(20.dp)) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            val root = activity.window.decorView
            fun layout() {
                repeat(5) {
                    root.measure(View.MeasureSpec.makeMeasureSpec(824, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1784, View.MeasureSpec.EXACTLY))
                    root.layout(0, 0, 824, 1784)
                    Snapshot.sendApplyNotifications()
                    frameTime += 16_666_667L
                    clock.sendFrame(frameTime)
                    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32))
                }
            }
            fun capture(name: String): Bitmap {
                layout()
                val bitmap = Bitmap.createBitmap(824, 1784, Bitmap.Config.ARGB_8888)
                root.draw(Canvas(bitmap))
                File("build/ui-previews/$name.png").apply { parentFile?.mkdirs() }.outputStream().use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                return bitmap
            }
            fun tap(x: Float, y: Float) {
                val time = SystemClock.uptimeMillis()
                listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP).forEach { action ->
                    val event = MotionEvent.obtain(time, time + if (action == MotionEvent.ACTION_UP) 50 else 0, action, x, y, 0)
                    root.dispatchTouchEvent(event)
                    event.recycle()
                }
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200))
            }
            layout()
            capture("playlist-scroll-1-expanded").recycle()
            runBlocking { list.scrollToItem(0, (350 * scale).toInt()) }
            capture("playlist-scroll-2-title-transition").recycle()
            runBlocking { list.scrollToItem(0, (450 * scale).toInt()) }
            capture("playlist-scroll-3-play-transition").recycle()
            runBlocking { list.scrollToItem(15) }
            val collapsed = capture("playlist-scroll-4-collapsed")
            assertTrue("Header must actually leave the lazy viewport", list.firstVisibleItemIndex > 0)
            assertEquals("Opaque bar must use the playlist color", expectedBar, collapsed.getPixel(400, (contentY + barBottom - 6 * scale).toInt()))
            collapsed.recycle()
            tap(824f - 49 * scale, contentY + barBottom)
            assertEquals("Docked play must still work when header is disposed", 1, playCalls)
            runBlocking { list.scrollToItem(0) }
            capture("playlist-scroll-5-returned").recycle()
            tap(824f - 49 * scale, contentY + barBottom)
            assertEquals("Floating play must disappear after scrolling back", 1, playCalls)
            tap(824f - 36 * scale, contentY + barBottom - 32 * scale)
            assertEquals("Toolbar search remains clickable", 1, searchCalls)
        } finally {
            controller.pause().stop().destroy()
            recomposer.cancel()
            scope.cancel()
        }
    }
}
