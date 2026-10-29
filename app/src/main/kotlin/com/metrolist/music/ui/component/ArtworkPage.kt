package com.metrolist.music.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.MaterialTheme
import androidx.palette.graphics.Palette
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.metrolist.music.ui.theme.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ArtworkPage(url: String?, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val resolved by produceState<Pair<String?, Color>?>(null, url) {
        value = null
        val color = withContext(Dispatchers.IO) {
            try {
                if (url == null) Color.Gray else {
                    val image = context.imageLoader.execute(ImageRequest.Builder(context).data(url).size(100, 100).allowHardware(false).build()).image
                    image?.toBitmap()?.let {
                        PlayerColorExtractor.extractGradientColors(Palette.from(it).maximumColorCount(8).generate(), 0xFF808080.toInt()).first()
                    } ?: Color.Gray
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { Color.Gray }
        }
        value = url to color
    }
    val color = resolved?.takeIf { it.first == url }?.second ?: Color.Gray
    MetrolistTheme(darkTheme = dark, themeColor = color) {
        CompositionLocalProvider(LocalSongColor provides color) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(songBackdropColors(color, MaterialTheme.colorScheme.background)))) {
            content()
        }
        }
    }
}
