package com.metrolist.music.ui.screens.equalizer

import android.content.Intent
import android.media.audiofx.AudioEffect
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R

@Composable
internal fun SystemEqualizerAction() {
    val context = LocalContext.current
    val connection = LocalPlayerConnection.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
    IconButton(onClick = {
        val session = connection?.player?.audioSessionId ?: 0
        val intent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
            putExtra(AudioEffect.EXTRA_AUDIO_SESSION, session)
            putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
            putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
        }
        when {
            session <= 0 -> Toast.makeText(context, R.string.sourcify_eq_start_song, Toast.LENGTH_SHORT).show()
            intent.resolveActivity(context.packageManager) == null -> Toast.makeText(context, R.string.sourcify_eq_unavailable, Toast.LENGTH_SHORT).show()
            else -> launcher.launch(intent)
        }
    }) { Icon(painterResource(R.drawable.graphic_eq), stringResource(R.string.system_equalizer)) }
}
