package com.metrolist.music.widget

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.RemoteViews
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.test.core.app.ApplicationProvider
import com.metrolist.music.R
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class WidgetColorsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun coverColorsKeepTextAndControlsReadableAcrossBrightDarkAndGrayCovers() {
        val seeds = listOf(Color.BLACK, Color.WHITE, Color.GRAY, Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW, Color.CYAN)
        seeds.forEach { seed ->
            val colors = WidgetColors.fromSeed(seed)
            assertTrue(ColorUtils.calculateContrast(colors.foreground, colors.background) >= 4.5)
            assertTrue(ColorUtils.calculateContrast(colors.secondary, colors.background) >= 4.5)
            assertTrue(ColorUtils.calculateContrast(colors.accent, colors.background) >= 3.0)
            assertTrue(ColorUtils.calculateContrast(colors.onAccent, colors.accent) >= 4.5)
        }
        assertNotEquals(WidgetColors.fromSeed(Color.RED), WidgetColors.fromSeed(Color.BLUE))
    }

    @Test fun allWidgetLayoutsAcceptDynamicColorsOnOlderAndroid() {
        val colors = WidgetColors.fromSeed(Color.RED)
        listOf(R.layout.widget_music_player, R.layout.widget_compact_square, R.layout.widget_compact_wide,
            R.layout.widget_turntable, R.layout.widget_playlist).forEach { layout ->
            val remote = RemoteViews(context.packageName, layout)
            colors.applyTo(remote, layout)
            val view = remote.apply(context, FrameLayout(context))
            if (layout != R.layout.widget_turntable) assertNotNull(view.findViewById<ImageView>(R.id.widget_surface).colorFilter)
            if (layout == R.layout.widget_music_player) assertEquals(colors.foreground, view.findViewById<TextView>(R.id.widget_song_title).currentTextColor)
        }
    }

    @Test fun localCoverAndPaletteAreReusedAndMissingCoverResetsPreviousColors() = runBlocking {
        val cover = File(context.cacheDir, "widget-cover.png")
        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        cover.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val cache = WidgetArtworkCache(context)
        val first = cache.get(cover.toURI().toString())
        assertNotNull(first.bitmap)
        assertSame(first, cache.get(cover.toURI().toString()))
        val idle = cache.get(null)
        assertNull(idle.bitmap)
        assertEquals(WidgetColors.Fallback, idle.colors)
        val unavailable = cache.get(File(context.cacheDir, "missing-widget-cover.png").toURI().toString())
        assertNull(unavailable.bitmap)
        assertEquals(WidgetColors.Fallback, unavailable.colors)
        cover.delete()
        Unit
    }
}
