package com.metrolist.music.ui.player

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.ScaleFactor
import kotlin.math.ceil
import kotlin.math.max

internal const val DefaultCanvasZoom = 1f

internal fun canvasZoom(zoom: Float): Float =
    (if (zoom.isFinite()) zoom else DefaultCanvasZoom).coerceIn(1f, 2f)

private fun canvasScale(viewWidth: Float, viewHeight: Float, sourceWidth: Float, sourceHeight: Float, zoom: Float): Double =
    max(viewWidth.toDouble() / sourceWidth, viewHeight.toDouble() / sourceHeight) * canvasZoom(zoom)

internal fun canvasFrameSize(viewWidth: Int, viewHeight: Int, videoWidth: Float, videoHeight: Float, zoom: Float = DefaultCanvasZoom): Pair<Int, Int> {
    if (viewWidth <= 0 || viewHeight <= 0 || videoWidth <= 0 || videoHeight <= 0 || !videoWidth.isFinite() || !videoHeight.isFinite()) return viewWidth to viewHeight
    val scale = canvasScale(viewWidth.toFloat(), viewHeight.toFloat(), videoWidth, videoHeight, zoom)
    val width = ceil(videoWidth * scale)
    val height = ceil(videoHeight * scale)
    if (width > Int.MAX_VALUE || height > Int.MAX_VALUE) return viewWidth to viewHeight
    return width.toInt() to height.toInt()
}

internal class CanvasContentScale(private val zoom: Float = DefaultCanvasZoom) : ContentScale {
    override fun computeScaleFactor(srcSize: Size, dstSize: Size): ScaleFactor {
        if (srcSize.width <= 0f || srcSize.height <= 0f || !srcSize.width.isFinite() || !srcSize.height.isFinite()) return ScaleFactor(1f, 1f)
        val scale = canvasScale(dstSize.width, dstSize.height, srcSize.width, srcSize.height, zoom).toFloat()
        return ScaleFactor(scale, scale)
    }
}
