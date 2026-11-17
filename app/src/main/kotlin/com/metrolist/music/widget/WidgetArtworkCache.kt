package com.metrolist.music.widget

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.toArgb
import androidx.palette.graphics.Palette
import coil3.imageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.crossfade
import coil3.toBitmap
import com.metrolist.music.ui.theme.PlayerColorExtractor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WidgetArtworkCache @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val mutex = Mutex()
    private var cachedUri: String? = null
    private var cachedArtwork: WidgetArtwork? = null

    suspend fun get(uri: String?): WidgetArtwork = mutex.withLock {
        if (uri == null) {
            cachedUri = null
            cachedArtwork = null
            return@withLock WidgetArtwork(null, WidgetColors.Fallback)
        }
        cachedArtwork?.takeIf { cachedUri == uri }?.let { return@withLock it }
        withContext(Dispatchers.IO) {
            val bitmap = try {
                val request = ImageRequest.Builder(context)
                    .data(uri)
                    .size(300, 300)
                    .allowHardware(false)
                    .crossfade(false)
                    // Widgets reuse player/downloaded artwork without starting their own downloads.
                    .networkCachePolicy(CachePolicy.DISABLED)
                    .build()
                context.imageLoader.execute(request).image?.toBitmap()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            val colors = bitmap?.let {
                val palette = Palette.from(it).maximumColorCount(8).resizeBitmapArea(100 * 100).generate()
                WidgetColors.fromSeed(
                    PlayerColorExtractor.extractGradientColors(palette, WidgetColors.FallbackSeed).first().toArgb(),
                )
            } ?: WidgetColors.Fallback
            WidgetArtwork(bitmap, colors).also {
                cachedUri = uri
                // A cache miss may become available once the player's image request completes.
                cachedArtwork = it.takeIf { bitmap != null }
            }
        }
    }
}

class WidgetArtwork internal constructor(val bitmap: Bitmap?, val colors: WidgetColors)
