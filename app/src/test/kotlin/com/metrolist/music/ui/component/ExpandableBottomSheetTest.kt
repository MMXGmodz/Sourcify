package com.metrolist.music.ui.component

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import androidx.activity.ComponentActivity
import androidx.activity.ComponentDialog
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollDispatcher
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import java.time.Duration

@OptIn(ExperimentalMaterial3Api::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class, qualifiers = "w412dp-h892dp-xhdpi")
class ExpandableBottomSheetTest {
    @Test fun opensHalfHeightAndUpwardListGestureExpandsBeforeScrollingTheList() {
        val harness = Harness()
        val dispatcher = NestedScrollDispatcher()
        lateinit var sheetState: SheetState
        lateinit var scope: CoroutineScope
        var firstItem = -1
        try {
            harness.content {
                MaterialTheme {
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
                    scope = rememberCoroutineScope()
                    ExpandableBottomSheet(onDismissRequest = {}, sheetState = sheetState) {
                        val listState = rememberLazyListState()
                        firstItem = listState.firstVisibleItemIndex
                        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().nestedScroll(
                            object : NestedScrollConnection {}, dispatcher,
                        )) {
                            items(100) { Text("Song $it") }
                        }
                    }
                }
            }
            harness.frames()
            assertEquals(harness.diagnostics(sheetState), SheetValue.PartiallyExpanded, sheetState.currentValue)
            val partialOffset = sheetState.requireOffset()
            val consumed = dispatcher.dispatchPreScroll(Offset(0f, -200f), NestedScrollSource.UserInput)
            assertTrue("The sheet must consume upward list movement while partial", consumed.y < 0f)
            assertTrue(sheetState.requireOffset() < partialOffset)
            assertEquals("The queue must stay in place until the sheet expands", 0, firstItem)
            scope.launch { dispatcher.dispatchPreFling(Velocity(0f, -3000f)) }
            harness.frames()
            assertEquals(harness.diagnostics(sheetState), SheetValue.Expanded, sheetState.currentValue)
            assertEquals("Expanded sheet must leave upward movement to the list", 0f,
                dispatcher.dispatchPreScroll(Offset(0f, -200f), NestedScrollSource.UserInput).y, 0f)
        } finally {
            harness.close()
        }
    }

    @Test fun backDismissesTheDefaultHalfOpenSheet() {
        val harness = Harness()
        var dismissals = 0
        try {
            harness.content {
                MaterialTheme {
                    ExpandableBottomSheet(onDismissRequest = { dismissals++ }) {
                        LazyColumn(Modifier.fillMaxSize()) {
                            items(100) { Text("History song $it") }
                        }
                    }
                }
            }
            harness.frames()
            (ShadowDialog.getLatestDialog() as ComponentDialog).onBackPressedDispatcher.onBackPressed()
            harness.frames()
            assertEquals(1, dismissals)
        } finally {
            harness.close()
        }
    }

    private class Harness {
        private val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        val activity = controller.get()
        private val clock = BroadcastFrameClock()
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined + clock)
        private val recomposer = Recomposer(scope.coroutineContext)
        private var time = 0L

        init { scope.launch { recomposer.runRecomposeAndApplyChanges() } }

        fun content(content: @Composable () -> Unit) {
            activity.setContent(parent = recomposer, content = content)
        }

        private fun roots(): List<View> = (listOfNotNull(activity.window.decorView,
            ShadowDialog.getLatestDialog()?.takeIf { it.isShowing }?.window?.decorView) +
            WindowInspector.getGlobalWindowViews()).distinct()

        fun frames(count: Int = 90) {
            val bitmap = Bitmap.createBitmap(824, 1784, Bitmap.Config.ARGB_8888)
            try {
                repeat(count) {
                    roots().forEach { root ->
                        root.measure(View.MeasureSpec.makeMeasureSpec(824, View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(1784, View.MeasureSpec.EXACTLY))
                        root.layout(0, 0, 824, 1784)
                        root.draw(Canvas(bitmap))
                    }
                    Snapshot.sendApplyNotifications()
                    time += 16_666_667L
                    clock.sendFrame(time)
                    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
                }
            } finally {
                bitmap.recycle()
            }
        }

        fun diagnostics(state: SheetState): String {
            fun children(view: View): List<String> = listOf(view.javaClass.simpleName) +
                if (view is ViewGroup) (0 until view.childCount).flatMap { children(view.getChildAt(it)) } else emptyList()
            val windows = roots().joinToString { root ->
                "${root.javaClass.name} ${root.width}x${root.height} attached=${root.isAttachedToWindow} children=${children(root).distinct()}"
            }
            return "current=${state.currentValue}, target=${state.targetValue}, expanded=${state.hasExpandedState}, " +
                "partial=${state.hasPartiallyExpandedState}, offset=${runCatching { state.requireOffset() }.getOrNull()}, windows=$windows"
        }

        fun close() {
            controller.pause().stop().destroy()
            recomposer.cancel()
            scope.cancel()
        }
    }
}
