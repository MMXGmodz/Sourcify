package com.metrolist.music.jam

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsOwner
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.navigation.NavController
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.R
import com.metrolist.music.ui.screens.JamScreen
import com.metrolist.music.playback.MusicService
import androidx.media3.exoplayer.ExoPlayer
import com.metrolist.music.ui.theme.MetrolistTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import java.io.File
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class, qualifiers = "w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class AndroidJamVisualTest {
    @Test
    fun nativeHostScreenRendersInvitationAndControls() = renderHost(false)

    @Test
    @Config(qualifiers = "de-w360dp-h800dp-mdpi")
    fun germanHostScreenRendersTranslatedControls() = renderHost(true)

    private fun renderHost(german: Boolean) {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()
        val service = Robolectric.buildService(MusicService::class.java).get()
        val player = ExoPlayer.Builder(activity).build()
        MusicService::class.java.getDeclaredField("player").apply { isAccessible = true }.set(service, player)
        val jam = AndroidJam(service)
        try {
            jam.server.start("MMXG")
            activity.setTheme(R.style.Theme_Metrolist)
            activity.setContent {
                CompositionLocalProvider(LocalPlayerAwareWindowInsets provides WindowInsets(0)) {
                    MetrolistTheme(darkTheme = true) {
                        Surface { JamScreen(NavController(activity), showTopBar = true, jam = jam) }
                    }
                }
            }
            shadowOf(Looper.getMainLooper()).idleFor(1, TimeUnit.SECONDS)
            val decor = activity.window.decorView
            decor.measure(View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY))
            decor.layout(0, 0, 360, 800)
            shadowOf(Looper.getMainLooper()).idleFor(1, TimeUnit.SECONDS)
            fun owner(view: View): SemanticsOwner? {
                if (view.javaClass.simpleName == "AndroidComposeView") {
                    return view.javaClass.getMethod("getSemanticsOwner").invoke(view) as SemanticsOwner
                }
                if (view is ViewGroup) for (i in 0 until view.childCount) owner(view.getChildAt(i))?.let { return it }
                return null
            }
            val text = mutableListOf<String>()
            fun collect(node: SemanticsNode) {
                node.config.getOrNull(SemanticsProperties.Text)?.forEach { text += it.text }
                node.children.forEach(::collect)
            }
            collect(requireNotNull(owner(decor)).unmergedRootSemanticsNode)
            assertTrue(text.toString(), text.contains(if (german) "Internet · mobile Daten" else "Internet · mobile data"))
            assertTrue(text.toString(), text.contains(if (german) "Selbes WLAN" else "Same Wi-Fi"))
            assertTrue(text.toString(), text.contains(if (german) "Dein Sound. Unser Jam." else "Your sound. Our Jam."))
            assertTrue(text.toString(), text.any { it.startsWith(if (german) "Sitzungscode:" else "Session code:") })
            assertTrue(text.toString(), text.contains(if (german) "Link kopieren" else "Copy link"))
            assertTrue(text.toString(), text.contains(if (german) "Neue Beitritte sperren" else "Lock new joins"))
            assertFalse(text.any { it.contains("Metrolist", ignoreCase = true) })
            val bitmap = Bitmap.createBitmap(360, 800, Bitmap.Config.ARGB_8888)
            decor.draw(Canvas(bitmap))
            val output = File("build/reports/sourcify-ui/android-jam${if (german) "-de" else "-en"}.png")
            output.parentFile?.mkdirs()
            output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        } finally {
            controller.pause().stop().destroy()
            jam.stop()
            player.release()
        }
    }
}
