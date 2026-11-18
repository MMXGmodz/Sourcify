package com.metrolist.music.ui.player

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import com.metrolist.music.R
import com.metrolist.music.ui.theme.MetrolistTheme
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShareLyricsPickerTest {
    @Test fun linesAreSelectableWithALimitAndContinueKeepsTheirOrder() {
        for (dark in listOf(true, false)) {
            val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            val clock = BroadcastFrameClock()
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined + clock)
            val recomposer = Recomposer(scope.coroutineContext)
            scope.launch { recomposer.runRecomposeAndApplyChanges() }
            try {
                val activity = controller.get()
                activity.setTheme(R.style.Theme_Metrolist)
                val selected = mutableStateOf(emptySet<Int>())
                var shared = emptySet<Int>()
                var backed = false
                val lines = listOf("First line", "Second line", "Third line", "Fourth line", "Fifth line", "Sixth line")
                activity.setContent(parent = recomposer) {
                    MetrolistTheme(darkTheme = dark) {
                        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
                                ShareLyricsPicker(lines, selected.value, false, { selected.value = it },
                                    { backed = true }, { shared = selected.value })
                            }
                        }
                    }
                }
                val decor = activity.window.decorView
                var time = 0L
                fun settle() {
                    repeat(12) {
                        decor.measure(View.MeasureSpec.makeMeasureSpec(440, View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY))
                        decor.layout(0, 0, 440, 1000)
                        Snapshot.sendApplyNotifications()
                        clock.sendFrame(time.also { time += 16_666_667 })
                        shadowOf(Looper.getMainLooper()).idleFor(16, TimeUnit.MILLISECONDS)
                        val frame = Bitmap.createBitmap(440, 1000, Bitmap.Config.ARGB_8888)
                        decor.draw(Canvas(frame)); frame.recycle()
                    }
                }
                fun nodes() = allNodes(requireNotNull(owner(decor)).unmergedRootSemanticsNode)
                fun line(label: String) = nodes().single {
                    it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text == label } == true
                }
                settle()
                for (label in lines.take(5)) {
                    requireNotNull(line(label).config[SemanticsActions.OnClick].action).invoke()
                    settle()
                }
                assertEquals(setOf(0, 1, 2, 3, 4), selected.value)
                assertTrue(line("Sixth line").config.contains(SemanticsProperties.Disabled))
                assertEquals(androidx.compose.ui.state.ToggleableState.On,
                    line("First line").config[SemanticsProperties.ToggleableState])
                requireNotNull(line("First line").config[SemanticsActions.OnClick].action).invoke()
                settle()
                assertFalse(line("Sixth line").config.contains(SemanticsProperties.Disabled))
                requireNotNull(line("Sixth line").config[SemanticsActions.OnClick].action).invoke()
                settle()
                val next = nodes().single { node -> node.config.getOrNull(SemanticsActions.OnClick) != null &&
                    allNodes(node).any { it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text == activity.getString(R.string.s6_lyrics_continue) } == true } }
                requireNotNull(next.config[SemanticsActions.OnClick].action).invoke()
                assertEquals(setOf(1, 2, 3, 4, 5), shared)
                assertFalse(backed)
                val bitmap = Bitmap.createBitmap(440, 1000, Bitmap.Config.ARGB_8888)
                decor.draw(Canvas(bitmap))
                File("build/ui-previews/lyrics-picker-${if (dark) "dark" else "light"}.png")
                    .apply { parentFile!!.mkdirs() }.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            } finally { controller.pause().stop().destroy(); recomposer.cancel(); scope.cancel() }
        }
    }

    private fun allNodes(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::allNodes)
    private fun owner(view: View): SemanticsOwner? {
        if (view.javaClass.simpleName == "AndroidComposeView") return view.javaClass.getMethod("getSemanticsOwner").invoke(view) as SemanticsOwner
        if (view is ViewGroup) for (index in 0 until view.childCount) owner(view.getChildAt(index))?.let { return it }
        return null
    }
}
