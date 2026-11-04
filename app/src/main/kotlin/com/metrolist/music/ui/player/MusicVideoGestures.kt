package com.metrolist.music.ui.player

import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.media3.common.Player

internal fun musicVideoSeekTarget(positionMs: Long, durationMs: Long, seconds: Int, forward: Boolean): Long {
    val limit = durationMs.takeIf { it >= 0 } ?: Long.MAX_VALUE
    val position = positionMs.coerceIn(0, limit)
    val step = seconds.coerceIn(1, 20) * 1_000L
    return if (forward) position + minOf(step, limit - position) else (position - step).coerceAtLeast(0)
}

internal fun seekMusicVideo(player: Player, seconds: Int, forward: Boolean): Boolean {
    if (!player.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)) return false
    player.seekTo(musicVideoSeekTarget(player.currentPosition, player.duration, seconds, forward))
    return true
}

@Composable
internal fun Modifier.musicVideoGestures(
    controlsLabel: String,
    backwardLabel: String,
    forwardLabel: String,
    onToggle: () -> Unit,
    onSeek: (Boolean) -> Boolean,
): Modifier {
    val toggle by rememberUpdatedState(onToggle)
    val seek by rememberUpdatedState(onSeek)
    return pointerInput(Unit) {
        detectTapGestures(onTap = { toggle() }, onDoubleTap = { seek(it.x >= size.width / 2f) })
    }.onKeyEvent { event ->
        if (event.key == Key.Enter || event.key == Key.DirectionCenter || event.key == Key.Spacebar) {
            if (event.type == KeyEventType.KeyUp) onToggle()
            true
        } else false
    }.focusable().semantics {
        role = Role.Button
        contentDescription = controlsLabel
        onClick(controlsLabel) { onToggle(); true }
        customActions = listOf(
            CustomAccessibilityAction(backwardLabel) { onSeek(false) },
            CustomAccessibilityAction(forwardLabel) { onSeek(true) },
        )
    }
}
