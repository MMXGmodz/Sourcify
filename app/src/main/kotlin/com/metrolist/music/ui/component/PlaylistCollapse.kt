package com.metrolist.music.ui.component

internal data class PlaylistCollapseFrame(val titleAlpha: Float, val docked: Boolean, val playTop: Float)

internal fun playlistCollapseFrame(
    titleTop: Float?, playTop: Float?, barBottom: Float, transitionDistance: Float, playSize: Float,
): PlaylistCollapseFrame {
    val progress = titleTop?.let {
        ((barBottom + transitionDistance / 3f - it) / transitionDistance).coerceIn(0f, 1f)
    } ?: 0f
    val alpha = progress * progress * (3f - 2f * progress)
    val dockTop = barBottom - playSize / 2f
    return PlaylistCollapseFrame(alpha, playTop != null && playTop <= dockTop, dockTop)
}
