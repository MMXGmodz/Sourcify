package com.metrolist.music.ui.component

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import com.metrolist.music.R
import com.metrolist.music.ui.theme.LocalSongColor
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class, qualifiers = "w360dp-h800dp-mdpi")
class SongColorOptionsTest {
    @Test fun customChannelsPreserveHueThroughGrayAndAutomaticRestoresNoOverride() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        val clock = BroadcastFrameClock()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined + clock)
        val recomposer = Recomposer(scope.coroutineContext)
        scope.launch { recomposer.runRecomposeAndApplyChanges() }
        var chosen by mutableStateOf<Int?>(null)
        val root = controller.get().window.decorView
        var time = 0L
        fun frames() {
            repeat(35) {
                root.measure(View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY))
                root.layout(0,0,360,800)
                Snapshot.sendApplyNotifications()
                clock.sendFrame(time.also { time += 16_666_667 })
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
                val image = Bitmap.createBitmap(360,800,Bitmap.Config.ARGB_8888)
                root.draw(Canvas(image)); image.recycle()
            }
        }
        fun nodes() = descendants(requireNotNull(owner(root)).unmergedRootSemanticsNode)
        fun toggle() { requireNotNull(nodes().single { it.config.getOrNull(SemanticsProperties.Role) == Role.Switch }.config[SemanticsActions.OnClick].action).invoke(); frames() }
        fun set(label: Int, progress: Float) {
            val text = controller.get().getString(label)
            val node = nodes().single { it.config.getOrNull(SemanticsActions.SetProgress) != null && it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(text) == true }
            assertTrue(requireNotNull(node.config[SemanticsActions.SetProgress].action).invoke(progress)); frames()
        }
        try {
            controller.get().setContent(parent = recomposer) {
                MaterialTheme {
                    CompositionLocalProvider(LocalSongColor provides Color.Red) {
                        Column(Modifier.fillMaxSize()) { SongColorOptions(chosen, { chosen = it }) }
                    }
                }
            }
            frames()
            assertNull(chosen)
            toggle()
            assertEquals(0xFFFF0000.toInt(), chosen)
            set(R.string.s811_color_hue,120f)
            assertEquals(0xFF00FF00.toInt(), chosen)
            set(R.string.s811_color_saturation,0f)
            assertEquals(0xFFFFFFFF.toInt(), chosen)
            set(R.string.s811_color_hue,240f)
            set(R.string.s811_color_saturation,1f)
            assertEquals("Hue must survive editing saturation through gray",0xFF0000FF.toInt(), chosen)
            toggle()
            assertNull(chosen)
            assertTrue(nodes().none { it.config.getOrNull(SemanticsActions.SetProgress) != null })
        } finally { controller.pause().stop().destroy(); recomposer.cancel(); scope.cancel() }
    }
    private fun descendants(n: SemanticsNode): List<SemanticsNode> = listOf(n) + n.children.flatMap(::descendants)
    private fun owner(v: View): SemanticsOwner? {
        if (v.javaClass.simpleName == "AndroidComposeView") return v.javaClass.getMethod("getSemanticsOwner").invoke(v) as SemanticsOwner
        if (v is ViewGroup) for(i in 0 until v.childCount) owner(v.getChildAt(i))?.let { return it }
        return null
    }
}
