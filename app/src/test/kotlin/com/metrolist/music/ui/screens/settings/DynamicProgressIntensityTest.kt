package com.metrolist.music.ui.screens.settings

import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsOwner
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class, qualifiers = "w360dp-h800dp-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
class DynamicProgressIntensityTest {
    @Test
    fun intensityClampsStoredValuesAndAccessibilityChangesSaveWithoutChangingMusic() {
        for ((stored, expected) in listOf(-50 to 0f, 150 to 100f)) {
            val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
            try {
                val activity = controller.get()
                var saved: Int? = null
                var changedMusic = false
                activity.setContent {
                    MaterialTheme {
                        DynamicProgressOptions(true, true, { changedMusic = true }, {}, stored, { saved = it })
                    }
                }
                val decor = activity.window.decorView
                fun layout() {
                    shadowOf(Looper.getMainLooper()).idleFor(1, TimeUnit.SECONDS)
                    decor.measure(View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY))
                    decor.layout(0, 0, 360, 800)
                    shadowOf(Looper.getMainLooper()).idleFor(1, TimeUnit.SECONDS)
                }
                layout()
                fun nodes(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::nodes)
                val slider = nodes(requireNotNull(owner(decor)).unmergedRootSemanticsNode).single {
                    it.config.getOrNull(SemanticsProperties.ProgressBarRangeInfo)?.range == 0f..100f
                }
                assertEquals(expected, slider.config[SemanticsProperties.ProgressBarRangeInfo].current)
                val change = requireNotNull(slider.config[SemanticsActions.SetProgress].action)
                assertTrue(change(35f))
                layout()
                assertEquals(35, saved)
                assertFalse("Intensity changes must preserve the music preference", changedMusic)
            } finally {
                controller.pause().stop().destroy()
            }
        }
    }

    private fun owner(view: View): SemanticsOwner? {
        if (view.javaClass.simpleName == "AndroidComposeView") {
            return view.javaClass.getMethod("getSemanticsOwner").invoke(view) as SemanticsOwner
        }
        if (view is ViewGroup) for (index in 0 until view.childCount) owner(view.getChildAt(index))?.let { return it }
        return null
    }
}
