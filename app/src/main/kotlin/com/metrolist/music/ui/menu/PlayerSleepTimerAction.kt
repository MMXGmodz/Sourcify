package com.metrolist.music.ui.menu

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.constants.*
import com.metrolist.music.ui.component.Material3MenuGroup
import com.metrolist.music.ui.component.Material3MenuItemData
import com.metrolist.music.utils.rememberPreference
import kotlin.math.roundToInt

@Composable
internal fun PlayerSleepTimerAction() {
    val connection = LocalPlayerConnection.current ?: return
    val timer = connection.service.sleepTimer ?: return
    val (defaultMinutes, setDefaultMinutes) = rememberPreference(SleepTimerDefaultKey, 30f)
    val (stopAtEnd, setStopAtEnd) = rememberPreference(SleepTimerStopAfterCurrentSongKey, false)
    val (fade, setFade) = rememberPreference(SleepTimerFadeOutKey, false)
    var showing by remember { mutableStateOf(false) }
    var minutes by remember { mutableFloatStateOf(defaultMinutes.coerceIn(5f, 120f)) }
    Material3MenuGroup(listOf(Material3MenuItemData(
        title = { Text(stringResource(R.string.sleep_timer)) },
        description = { if (timer.isActive) Text(stringResource(R.string.sourcify_timer_active)) },
        icon = { Icon(painterResource(R.drawable.bedtime), null) }, onClick = { showing = true },
    )))
    if (showing) AlertDialog(onDismissRequest = { showing = false }, title = { Text(stringResource(R.string.sleep_timer)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("${minutes.roundToInt()} min")
            Slider(minutes, { minutes = it }, valueRange = 5f..120f, steps = 22)
            TextButton(onClick = { setDefaultMinutes(minutes) }) { Text(stringResource(R.string.set_as_default)) }
            Row { Text(stringResource(R.string.sleep_timer_stop_after_current_song_title), Modifier.weight(1f)); Switch(stopAtEnd, setStopAtEnd) }
            Row { Text(stringResource(R.string.sleep_timer_fade_out_title), Modifier.weight(1f)); Switch(fade, setFade) }
            TextButton(onClick = { timer.start(-1); showing = false }) { Text(stringResource(R.string.end_of_song)) }
            if (timer.isActive) TextButton(onClick = { timer.clear(); showing = false }) { Text(stringResource(R.string.sourcify_timer_stop)) }
        } },
        confirmButton = { TextButton(onClick = { timer.start(minutes.roundToInt(), stopAtEnd, fade); showing = false }) { Text(stringResource(android.R.string.ok)) } },
        dismissButton = { TextButton(onClick = { showing = false }) { Text(stringResource(android.R.string.cancel)) } })
}
