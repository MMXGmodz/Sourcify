package com.metrolist.music.ui.tv

import android.view.KeyEvent

internal enum class TvMediaAction { TOGGLE, PLAY, PAUSE, NEXT, PREVIOUS, STOP }
internal fun tvMediaAction(keyCode: Int): TvMediaAction? = when (keyCode) {
    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> TvMediaAction.TOGGLE
    KeyEvent.KEYCODE_MEDIA_PLAY -> TvMediaAction.PLAY
    KeyEvent.KEYCODE_MEDIA_PAUSE -> TvMediaAction.PAUSE
    KeyEvent.KEYCODE_MEDIA_NEXT -> TvMediaAction.NEXT
    KeyEvent.KEYCODE_MEDIA_PREVIOUS -> TvMediaAction.PREVIOUS
    KeyEvent.KEYCODE_MEDIA_STOP -> TvMediaAction.STOP
    else -> null
}
internal fun tvSeekPosition(position: Long, duration: Long, delta: Long): Long {
    if (duration <= 0) return position.coerceAtLeast(0)
    return (position.coerceIn(0, duration) + delta).coerceIn(0, duration)
}
