package com.metrolist.music.ui.screens.settings

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsOwner
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import com.metrolist.music.R
import com.metrolist.music.constants.AudioQuality
import com.metrolist.music.ui.theme.MetrolistTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class, qualifiers = "w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class SettingsPresentationTest {
    @Test
    fun mediaQualityKeepsEveryControlAndInheritsTheAppBackgroundInBothThemes() {
        for (dark in listOf(true, false)) {
            val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
            try {
                val activity = controller.get()
                activity.setTheme(R.style.Theme_Metrolist)
                val backdrop = 0xFF4B617C.toInt()
                activity.setContent {
                    MetrolistTheme(darkTheme = dark) {
                        Box(Modifier.fillMaxSize().background(Color(backdrop))) {
                            MediaQualityContent(AudioQuality.AUTO, AudioQuality.LOW, AudioQuality.HIGH, 720, 240,
                                {}, {}, {}, {}, {}, {}, {})
                        }
                    }
                }
                val decor = activity.window.decorView
                shadowOf(Looper.getMainLooper()).idleFor(1, TimeUnit.SECONDS)
                decor.measure(View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY))
                decor.layout(0, 0, 360, 800)
                shadowOf(Looper.getMainLooper()).idleFor(1, TimeUnit.SECONDS)
                val texts = mutableListOf<String>()
                fun collect(node: SemanticsNode) {
                    node.config.getOrNull(SemanticsProperties.Text)?.forEach { texts += it.text }
                    node.children.forEach(::collect)
                }
                collect(requireNotNull(owner(decor)).unmergedRootSemanticsNode)
                for (label in listOf(R.string.s8_audio_streaming, R.string.s8_wifi_stream,
                    R.string.s8_mobile_stream, R.string.s8_download_audio, R.string.s8_lossless,
                    R.string.s8_video_quality)) {
                    assertTrue("Missing media quality control: ${activity.getString(label)}", texts.contains(activity.getString(label)))
                }
                val bitmap = Bitmap.createBitmap(360, 800, Bitmap.Config.ARGB_8888)
                try {
                    decor.draw(Canvas(bitmap))
                    assertEquals("Settings must keep the shared backdrop in theme dark=$dark", backdrop, bitmap.getPixel(2, 400))
                } finally { bitmap.recycle() }
            } finally { controller.pause().stop().destroy() }
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
