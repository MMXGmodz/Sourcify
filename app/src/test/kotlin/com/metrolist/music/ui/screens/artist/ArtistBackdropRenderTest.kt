package com.metrolist.music.ui.screens.artist

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsOwner
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.ui.component.ListItem
import com.metrolist.music.ui.component.NavigationTitle
import com.metrolist.music.ui.component.SwipeToSongBox
import com.metrolist.music.ui.theme.LocalSongColor
import com.metrolist.music.ui.theme.MetrolistTheme
import com.metrolist.music.ui.theme.screenHeaderBrush
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import java.io.File
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class, qualifiers = "de-w412dp-h892dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class ArtistBackdropRenderTest {
    @Test fun artistRowsAndFavoritesKeepTheSameBackdropBeforeAndAfterScroll() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        val activity = controller.get()
        val clock = BroadcastFrameClock()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined + clock)
        val recomposer = Recomposer(scope.coroutineContext)
        scope.launch { recomposer.runRecomposeAndApplyChanges() }
        lateinit var list: LazyListState
        var favoriteY = 0
        var releaseY = 0
        var rowY = 0
        var selectedY = 0
        var headerY = 0
        var activeColor = 0
        var backdropTop = 0
        var measuredHeaderHeight by mutableIntStateOf(0)
        try {
            activity.setContent(parent = recomposer) {
                MetrolistTheme(darkTheme = true, pureBlack = true) {
                    CompositionLocalProvider(
                        LocalSongColor provides Color(0xFF597792),
                        LocalPlayerConnection provides null,
                    ) {
                        Surface(Modifier.fillMaxSize()) {
                            Box(Modifier.fillMaxSize().background(screenHeaderBrush())) {
                                list = rememberLazyListState()
                                activeColor = MaterialTheme.colorScheme.secondaryContainer.toArgb()
                                val fadeDistance = with(LocalDensity.current) { 112.dp.roundToPx() }
                                val collapse = if (list.firstVisibleItemIndex > 0) 1f else
                                    artistHeaderCollapseProgress(list.firstVisibleItemScrollOffset, measuredHeaderHeight, fadeDistance)
                                ArtistBackdrop("KIDD KAWAKI", collapse > 0f, {}, {}, collapseProgress = collapse,
                                    modifier = Modifier.padding(end = 2.dp).onGloballyPositioned { backdropTop = it.positionInWindow().y.toInt() }) {
                                    LazyColumn(state = list, contentPadding = PaddingValues(top = 72.dp)) {
                                        item {
                                            Column(Modifier.heightIn(min = 180.dp).onSizeChanged { measuredHeaderHeight = it.height }.onGloballyPositioned { releaseY = it.positionInWindow().y.toInt() + it.size.height / 2 }) {
                                                ArtistHighlightRows("KIDD KAWAKI", 0, "Neue Single", null, true, true, {}, {})
                                            }
                                        }
                                        item {
                                            Column(Modifier.onGloballyPositioned { favoriteY = it.positionInWindow().y.toInt() + it.size.height / 2 }) {
                                                ArtistHighlightRows("KIDD KAWAKI", 5, null, null, false, true, {}, {})
                                            }
                                        }
                                        item {
                                            NavigationTitle("Top-Songs", modifier = Modifier.onGloballyPositioned { headerY = it.positionInWindow().y.toInt() + it.size.height / 2 })
                                        }
                                        items(30) { index ->
                                            SwipeToSongBox(mediaItem = MediaItem.Builder().setMediaId("song-$index").build(),
                                                modifier = Modifier.onGloballyPositioned {
                                                    if (index == list.firstVisibleItemIndex.coerceAtLeast(3) - 3 || index == 0) rowY = it.positionInWindow().y.toInt() + it.size.height / 2
                                                    if (index == 1) selectedY = it.positionInWindow().y.toInt() + it.size.height / 2
                                                }) {
                                                ListItem(title = "Song $index", subtitle = "KIDD KAWAKI", isActive = index == 1,
                                                    thumbnailContent = { Box(Modifier.size(48.dp)) })
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            val root = activity.window.decorView
            var frame = 0L
            fun capture(name: String): Bitmap {
                repeat(8) {
                    root.measure(View.MeasureSpec.makeMeasureSpec(824, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1784, View.MeasureSpec.EXACTLY))
                    root.layout(0, 0, 824, 1784)
                    Snapshot.sendApplyNotifications()
                    frame += 16_666_667L
                    clock.sendFrame(frame)
                    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
                }
                return Bitmap.createBitmap(824, 1784, Bitmap.Config.ARGB_8888).also { bitmap ->
                    root.draw(Canvas(bitmap))
                    File("build/ui-previews/$name.png").apply { parentFile?.mkdirs() }.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                }
            }
            fun toolbarTitleVisible(): Boolean {
                fun owner(view: View): SemanticsOwner? {
                    view.javaClass.methods.firstOrNull { it.name == "getSemanticsOwner" && it.parameterCount == 0 }?.let {
                        return it.invoke(view) as? SemanticsOwner
                    }
                    if (view is ViewGroup) for (i in 0 until view.childCount) owner(view.getChildAt(i))?.let { return it }
                    return null
                }
                fun containsTitle(node: SemanticsNode): Boolean {
                    val bounds = node.boundsInWindow
                    // Offscreen lazy rows can remain in the unmerged semantics tree
                    // with empty clipped bounds at the origin.
                    return (!bounds.isEmpty && bounds.bottom > backdropTop && bounds.top < backdropTop + 128 &&
                        node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == "KIDD KAWAKI" } == true) ||
                        node.children.any(::containsTitle)
                }
                return containsTitle(requireNotNull(owner(root)).unmergedRootSemanticsNode)
            }
            fun assertSameBackdrop(label: String, expected: Int, actual: Int) {
                assertEquals("$label alpha", expected ushr 24, actual ushr 24)
                // Skia dithers independently drawn gradients by one RGB step.
                val difference = listOf(16, 8, 0).maxOf { shift ->
                    kotlin.math.abs(((expected ushr shift) and 255) - ((actual ushr shift) and 255))
                }
                assertTrue("$label (maximum RGB difference: $difference)", difference <= 1)
            }
            fun assertToolbarBackdrop(bitmap: Bitmap) {
                val y = backdropTop + 100
                val reference = bitmap.getPixel(823, y)
                assertNotEquals("Toolbar reference must remain tinted", android.graphics.Color.BLACK, reference)
                assertSameBackdrop("Artist toolbar must match the continuous page backdrop", reference, bitmap.getPixel(780, y))
            }
            fun assertBackdrop(bitmap: Bitmap, label: String, y: Int, insideX: Int) {
                assertTrue("$label must be visible", y in 1 until bitmap.height)
                val beside = bitmap.getPixel(2, y)
                assertNotEquals("Fixture must expose a tinted backdrop", android.graphics.Color.BLACK, beside)
                assertSameBackdrop("$label must not paint an opaque rectangle over the artist backdrop", beside, bitmap.getPixel(insideX, y))
            }
            val expanded = capture("artist-backdrop-expanded")
            assertToolbarBackdrop(expanded)
            assertFalse("Expanded artist keeps the title in its artwork header", toolbarTitleVisible())
            assertBackdrop(expanded, "Favorites", favoriteY, 50)
            assertBackdrop(expanded, "Latest release", releaseY, 50)
            assertBackdrop(expanded, "Unselected song with swipe enabled", rowY, 50)
            assertBackdrop(expanded, "Top Songs heading", headerY, 780)
            assertEquals("Current song keeps its playing highlight", activeColor, expanded.getPixel(50, selectedY))
            expanded.recycle()
            runBlocking { list.scrollToItem(12) }
            val scrolled = capture("artist-backdrop-scrolled")
            assertTrue("Scroll must actually advance the lazy list", list.firstVisibleItemIndex >= 12)
            assertToolbarBackdrop(scrolled)
            assertTrue("Artist title appears in the toolbar after scrolling", toolbarTitleVisible())
            assertBackdrop(scrolled, "Scrolled unselected song", rowY, 50)
            scrolled.recycle()
        } finally {
            controller.pause().stop().destroy()
            recomposer.cancel()
            scope.cancel()
        }
    }

    @Test fun paddedTabsStayInTheListUntilTheyReachTheToolbarThenRemainPinned() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        val activity = controller.get()
        val clock = BroadcastFrameClock()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined + clock)
        val recomposer = Recomposer(scope.coroutineContext)
        scope.launch { recomposer.runRecomposeAndApplyChanges() }
        lateinit var list: LazyListState
        var toolbarHeight = 0
        var measuredHeaderHeight = 0
        var originalTabsY = 0f
        var pinnedTabsY = 0f
        var fixtureTop = 0f
        var pinned = false
        try {
            activity.setContent(parent = recomposer) {
                MetrolistTheme(darkTheme = true, pureBlack = true) {
                    val topPadding = WindowInsets.systemBars.asPaddingValues().calculateTopPadding() +
                        com.metrolist.music.constants.AppBarHeight
                    toolbarHeight = with(LocalDensity.current) { topPadding.roundToPx() }
                    list = rememberLazyListState()
                    val tabsPinned by remember {
                        derivedStateOf {
                            val tabs = list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == "artist_content_tabs" }
                            tabs?.let { it.offset <= 0 } ?: (list.firstVisibleItemIndex > 1)
                        }
                    }
                    pinned = tabsPinned
                    ArtistBackdrop("KIDD KAWAKI", true, {}, {},
                        modifier = Modifier.onGloballyPositioned { fixtureTop = it.positionInWindow().y }) {
                        LazyColumn(state = list, contentPadding = PaddingValues(top = topPadding)) {
                            item(key = "header") {
                                Box(Modifier.fillMaxWidth().height(300.dp).onSizeChanged { measuredHeaderHeight = it.height })
                            }
                            item(key = "artist_content_tabs") {
                                ArtistContentTabs(false, {}, Modifier.onGloballyPositioned { originalTabsY = it.positionInWindow().y })
                            }
                            items(30) { Text("Song $it", Modifier.fillMaxWidth().height(64.dp)) }
                        }
                        if (tabsPinned) {
                            ArtistContentTabs(false, {}, Modifier.padding(top = topPadding)
                                .onGloballyPositioned { pinnedTabsY = it.positionInWindow().y })
                        }
                    }
                }
            }
            val root = activity.window.decorView
            var frame = 0L
            fun settle() {
                val bitmap = Bitmap.createBitmap(824, 1784, Bitmap.Config.ARGB_8888)
                try {
                    repeat(8) {
                        root.requestLayout()
                        root.measure(View.MeasureSpec.makeMeasureSpec(824, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1784, View.MeasureSpec.EXACTLY))
                        root.layout(0, 0, 824, 1784)
                        Snapshot.sendApplyNotifications()
                        frame += 16_666_667L
                        clock.sendFrame(frame)
                        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
                        // AndroidComposeView flushes deferred Compose placement during
                        // dispatchDraw; measuring the unchanged Android root is insufficient.
                        root.draw(Canvas(bitmap))
                    }
                } finally {
                    bitmap.recycle()
                }
            }
            settle()
            assertTrue("Fixture must include actual toolbar and status-bar padding", toolbarHeight >= 128)
            // Keep the tabs half a toolbar below their final position. Comparing the
            // lazy-item offset with toolbarHeight would incorrectly pin them here.
            runBlocking { list.scrollToItem(0, measuredHeaderHeight - toolbarHeight / 2) }
            settle()
            assertEquals("The intermediate scroll must reach the requested offset", measuredHeaderHeight - toolbarHeight / 2, list.firstVisibleItemScrollOffset)
            assertEquals("Lazy item offsets exclude the top padding", toolbarHeight / 2,
                list.layoutInfo.visibleItemsInfo.first { it.key == "artist_content_tabs" }.offset)
            assertFalse("Tabs must not duplicate above their still-visible list row", pinned)
            assertEquals("List coordinates must include the top content padding", toolbarHeight * 1.5f, originalTabsY - fixtureTop, 1f)
            runBlocking { list.scrollToItem(1) }
            settle()
            assertTrue("Tabs pin exactly as their list row reaches the toolbar", pinned)
            assertEquals(toolbarHeight.toFloat(), originalTabsY - fixtureTop, 1f)
            assertEquals(toolbarHeight.toFloat(), pinnedTabsY - fixtureTop, 1f)
            runBlocking { list.scrollToItem(2, 32) }
            settle()
            assertTrue("Tabs remain pinned after the original row leaves the viewport", pinned)
            assertEquals(toolbarHeight.toFloat(), pinnedTabsY - fixtureTop, 1f)
        } finally {
            controller.pause().stop().destroy()
            recomposer.cancel()
            scope.cancel()
        }
    }
}
