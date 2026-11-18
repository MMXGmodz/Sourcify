package com.metrolist.music.utils

import android.content.Context
import android.graphics.*
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.metrolist.music.R
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.ui.theme.DefaultThemeColor
import androidx.compose.ui.graphics.toArgb
import java.io.File
import java.util.UUID

internal fun shareSongUrl(id: String): String? = id.takeIf { it.matches(Regex("[A-Za-z0-9_-]{11}")) }
    ?.let { "https://music.youtube.com/watch?v=$it" }
internal const val MAX_SHARE_LYRIC_LINES = 5
internal fun selectedShareLines(lines: List<String>, selected: Set<Int>): List<String> = selected.sorted()
    .filter { it in lines.indices }.flatMap { lines[it].lines() }.filter(String::isNotBlank)
    .take(MAX_SHARE_LYRIC_LINES).map { it.take(300) }

internal data class ShareArtwork(val cover: Bitmap?, val color: Int)

internal fun shareCoverColor(cover: Bitmap?): Int {
    val fallback = DefaultThemeColor.toArgb()
    val palette = cover?.let { Palette.from(it).maximumColorCount(8).resizeBitmapArea(100 * 100).generate() }
    return palette?.getVibrantColor(palette.getDominantColor(fallback)) ?: fallback
}

internal suspend fun loadShareArtwork(context: Context, thumbnailUrl: String?): ShareArtwork {
    val cover = thumbnailUrl?.let {
        context.imageLoader.execute(ImageRequest.Builder(context).data(it).size(1040).allowHardware(false).build()).image?.toBitmap()
    }
    return ShareArtwork(cover, shareCoverColor(cover))
}

internal fun renderShareCard(context: Context, song: MediaMetadata, lines: List<String>, backgroundColor: Int, cover: Bitmap? = null): Bitmap {
    val cardHeight = if (lines.isEmpty()) 1800 else 1500
    val result = Bitmap.createBitmap(1200, cardHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(result)
    val bg = backgroundColor or 0xFF000000.toInt()
    val ink = if (ColorUtils.calculateLuminance(bg) > 0.179) Color.BLACK else Color.WHITE
    canvas.drawColor(bg)
    if (lines.isEmpty()) canvas.drawRect(0f,0f,1200f,1800f, Paint().apply { shader = LinearGradient(0f,0f,0f,1800f,bg,Color.BLACK,Shader.TileMode.CLAMP) })
    fun image(rect: Rect) {
        cover?.let {
            val edge = minOf(it.width,it.height)
            val left=(it.width-edge)/2;val top=(it.height-edge)/2
            canvas.drawBitmap(it,Rect(left,top,left+edge,top+edge),rect,Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        }
    }
    fun text(value: String, x: Float, y: Float, size: Float, width: Int, max: Int, bold: Boolean = false): Int {
        val paint=TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color=if(lines.isEmpty())Color.WHITE else ink; textSize=size;typeface=if(bold)Typeface.DEFAULT_BOLD else Typeface.DEFAULT }
        val layout=StaticLayout.Builder.obtain(value.take(1000),0,value.take(1000).length,paint,width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL).setMaxLines(max).setEllipsize(android.text.TextUtils.TruncateAt.END).build()
        canvas.save();canvas.translate(x,y);layout.draw(canvas);canvas.restore();return layout.height
    }
    if(lines.isEmpty()) {
        image(Rect(80,80,1120,1120))
        val height=text(song.title,80f,1190f,72f,1040,2,true)
        text(song.artists.joinToString { it.name },80f,1208f+height,44f,1040,2)
    } else {
        image(Rect(80,80,260,260))
        val titleHeight = text(song.title,300f,90f,52f,800,2,true)
        text(song.artists.joinToString { it.name },300f,100f+titleHeight,36f,800,1)
        val body = lines.take(MAX_SHARE_LYRIC_LINES).map { it.take(300) }.joinToString("\n\n")
        var fontSize = 72f
        var layout: StaticLayout
        do {
            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = ink; textSize = fontSize; typeface = Typeface.DEFAULT_BOLD }
            layout = StaticLayout.Builder.obtain(body, 0, body.length, paint, 1040).build()
            fontSize -= 2f
        } while (layout.height > cardHeight - 580 && fontSize >= 18f)
        canvas.save(); canvas.translate(80f,340f); layout.draw(canvas); canvas.restore()
    }
    context.getDrawable(R.drawable.sourcify_mark)?.mutate()?.let {
        it.setTint(if(lines.isEmpty())Color.WHITE else ink);it.setBounds(80,cardHeight-190,170,cardHeight-100);it.draw(canvas)
    }
    text("Sourcify",190f,cardHeight-187f,58f,900,1,true)
    return result
}

internal fun saveShareCard(context: Context, bitmap: Bitmap): android.net.Uri {
    val directory=File(context.cacheDir,"share-cards").apply { mkdirs() }
    directory.listFiles().orEmpty().filter { it.isFile && System.currentTimeMillis()-it.lastModified()>2*86400000L }.forEach { it.delete() }
    val file=File(directory,"${UUID.randomUUID()}.png")
    try { file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG,100,it)) } }
    catch(error:Throwable) { file.delete();throw error }
    return FileProvider.getUriForFile(context,"${context.packageName}.FileProvider",file)
}
