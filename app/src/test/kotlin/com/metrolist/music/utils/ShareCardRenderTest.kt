package com.metrolist.music.utils
import android.app.Application
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.metrolist.music.models.MediaMetadata
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[33],application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShareCardRenderTest {
    @Test fun coverPaletteAndCustomColorsReachBothExportTypes() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val song = MediaMetadata("abcdefghijk", "Song", listOf(MediaMetadata.Artist(null, "Artist")), 180)
        val cover = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(240, 140, 20)) }
        val automatic = shareCoverColor(cover)
        assertTrue(Color.red(automatic) > Color.green(automatic))
        assertTrue(Color.green(automatic) > Color.blue(automatic))
        val songCard = renderShareCard(context, song, emptyList(), automatic, cover)
        val top = songCard.getPixel(4, 0)
        for (channel in listOf(Color::red, Color::green, Color::blue))
            assertTrue("The top gradient must retain the cover color", kotlin.math.abs(channel(automatic) - channel(top)) <= 1)
        val custom = Color.rgb(28, 90, 125)
        val lines = listOf("First chosen line", "Second chosen line", "Third chosen line", "Fourth chosen line", "Fifth chosen line")
        val lyricsCard = renderShareCard(context, song, lines, custom, cover)
        assertEquals(custom, lyricsCard.getPixel(4, 4))
        assertEquals(1500, lyricsCard.height)
        java.io.File("build/ui-previews/share-five-lyrics.png").apply { parentFile!!.mkdirs() }.outputStream().use {
            lyricsCard.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        cover.recycle(); songCard.recycle(); lyricsCard.recycle()
    }

    @Test fun lyricsCardExportsBrandedPngThroughAnAppOwnedContentUri() = runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val song=MediaMetadata("abcdefghijk","Song",listOf(MediaMetadata.Artist(null,"Artist")),180)
        val bitmap=renderShareCard(context,song,listOf("First line","Second line"),Color.LTGRAY)
        assertEquals(1200,bitmap.width);assertEquals(1500,bitmap.height)
        var ink=0
        for(y in 1310 until 1400) for(x in 80 until 600) if(android.graphics.Color.red(bitmap.getPixel(x,y))<80) ink++
        assertTrue("Sourcify brand must be painted",ink>100)
        val uri=saveShareCard(context,bitmap)
        assertEquals("content",uri.scheme)
        assertEquals("${context.packageName}.FileProvider",uri.authority)
        context.contentResolver.openInputStream(uri)!!.use {
            val decoded=BitmapFactory.decodeStream(it)
            assertEquals(1200,decoded.width);assertEquals(1500,decoded.height)
        }
        java.io.File("build/ui-previews/share-lyrics.png").apply { parentFile!!.mkdirs() }.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
        Unit
    }
}
