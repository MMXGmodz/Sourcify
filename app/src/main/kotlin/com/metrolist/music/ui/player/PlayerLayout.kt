package com.metrolist.music.ui.player

import android.content.pm.ActivityInfo
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
internal fun KeepCanvasPlayerPortrait(enabled: Boolean) {
    val activity = LocalContext.current.videoActivity() ?: return
    if (enabled) DisposableEffect(activity) {
        val original = activity.requestedOrientation
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        onDispose { activity.requestedOrientation = original }
    }
}

@Composable
internal fun PlayerLandscapeLayout(artwork: @Composable () -> Unit, controls: @Composable ColumnScope.() -> Unit) {
    Row(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(top = 66.dp, bottom = 8.dp)) {
        Box(Modifier.weight(.9f).fillMaxHeight().padding(8.dp), contentAlignment = Alignment.Center) { artwork() }
        Column(Modifier.weight(1.1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            content = controls)
    }
}
