package com.metrolist.music.utils
import android.app.Application
import android.content.Context
import android.graphics.BitmapFactory
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
    @Test fun lyricsCardExportsBrandedPngThroughAnAppOwnedContentUri() = runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val song=MediaMetadata("abcdefghijk","Song",listOf(MediaMetadata.Artist(null,"Artist")),180)
        val bitmap=renderShareCard(context,song,listOf("First line","Second line"),1)
        assertEquals(1200,bitmap.width);assertEquals(1200,bitmap.height)
        var ink=0
        for(y in 1010 until 1100) for(x in 80 until 600) if(android.graphics.Color.red(bitmap.getPixel(x,y))<80) ink++
        assertTrue("Sourcify brand must be painted",ink>100)
        val uri=saveShareCard(context,bitmap)
        assertEquals("content",uri.scheme)
        assertEquals("${context.packageName}.FileProvider",uri.authority)
        context.contentResolver.openInputStream(uri)!!.use {
            val decoded=BitmapFactory.decodeStream(it)
            assertEquals(1200,decoded.width);assertEquals(1200,decoded.height)
        }
        java.io.File("build/ui-previews/share-lyrics.png").apply { parentFile!!.mkdirs() }.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
        Unit
    }
}
