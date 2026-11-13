package com.metrolist.music.ui.player

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.metrolist.music.R
import com.metrolist.music.constants.*
import com.metrolist.music.ui.component.ExpandableBottomSheet
import com.metrolist.music.utils.rememberEnumPreference
import com.metrolist.music.utils.rememberPreference

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LandscapePlayerSettings(songId: String, onDismiss: () -> Unit, onQueue: () -> Unit, onSongOptions: () -> Unit) {
    var playStyle by rememberEnumPreference(PlayButtonStyleKey, PlayButtonStyle.STANDARD)
    var sliderStyle by rememberEnumPreference(SliderStyleKey, SliderStyle.DEFAULT)
    var squiggly by rememberPreference(SquigglySliderKey, false)
    var intensity by rememberPreference(DynamicProgressIntensityKey, 100)
    var music by rememberPreference(DynamicProgressMusicKey, true)
    ExpandableBottomSheet(onDismissRequest = onDismiss, containerColor = Color(0xFF171C1A), contentColor = Color.White) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.s16_player_options), style = MaterialTheme.typography.titleLarge)
            SettingsChoice(stringResource(R.string.player_play_button_style), PlayButtonStyle.entries.map {
                stringResource(if (it == PlayButtonStyle.STANDARD) R.string.play_button_standard else R.string.play_button_metal_ring) to (it == playStyle)
            }) { playStyle = PlayButtonStyle.entries[it] }
            val styles = listOf(SliderStyle.DEFAULT to false, SliderStyle.WAVY to false, SliderStyle.WAVY to true, SliderStyle.SLIM to false, SliderStyle.DYNAMIC to false)
            val labels = listOf(R.string.default_, R.string.wavy, R.string.squiggly, R.string.slim, R.string.s13_dynamic)
            SettingsChoice(stringResource(R.string.s16_slider_style), styles.mapIndexed { index, (style, wave) ->
                stringResource(labels[index]) to (style == sliderStyle && (style != SliderStyle.WAVY || wave == squiggly))
            }) { index ->
                val selected = styles[index]
                sliderStyle = selected.first
                if (selected.first == SliderStyle.WAVY) squiggly = selected.second
            }
            if (sliderStyle == SliderStyle.DYNAMIC) {
                Text(stringResource(R.string.s16_dynamic_intensity, intensity.coerceIn(0, 100)))
                Slider(value = intensity.coerceIn(0, 100).toFloat(), onValueChange = { intensity = it.toInt() }, valueRange = 0f..100f, steps = 99)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.s16_dynamic_music), Modifier.weight(1f))
                    Switch(checked = music, onCheckedChange = { music = it })
                }
            }
            LyricsVisibilityAction(songId)
            TextButton(onClick = onQueue) { Icon(painterResource(R.drawable.queue_music), null); Spacer(Modifier.width(12.dp)); Text(stringResource(R.string.queue)) }
            TextButton(onClick = onSongOptions) { Icon(painterResource(R.drawable.more_horiz), null); Spacer(Modifier.width(12.dp)); Text(stringResource(R.string.s16_song_options)) }
        }
    }
}

@Composable
private fun SettingsChoice(title: String, choices: List<Pair<String, Boolean>>, onChoice: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(title, style = MaterialTheme.typography.labelLarge)
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(choices.firstOrNull { it.second }?.first.orEmpty(), Modifier.weight(1f))
                Icon(painterResource(R.drawable.expand_more), null)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                choices.forEachIndexed { index, (label, _) ->
                    DropdownMenuItem(text = { Text(label) }, onClick = { onChoice(index); expanded = false })
                }
            }
        }
    }
}
