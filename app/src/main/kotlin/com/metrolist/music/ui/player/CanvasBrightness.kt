package com.metrolist.music.ui.player

internal fun canvasDimAlpha(brightness: Float, fullscreen: Boolean = false): Float =
    if (fullscreen) 0f else 1f - (if (brightness.isFinite()) brightness else 0.65f).coerceIn(0.15f, 1f)
