package com.metrolist.music.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.metrolist.music.R

@Composable
fun SourcifyUpdateHero(
    title: String,
    subtitle: String,
    version: String,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), color = Color.Transparent) {
        Column(
            Modifier.background(Brush.linearGradient(listOf(
                lerp(colors.surfaceContainerHigh, colors.primary, .23f),
                colors.surfaceContainerLow,
            ))).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(shape = RoundedCornerShape(18.dp), color = colors.primary.copy(alpha = .14f)) {
                    Icon(painterResource(R.drawable.sourcify_mark), contentDescription = null,
                        tint = colors.primary, modifier = Modifier.size(56.dp))
                }
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
            }
            Text(title, style = MaterialTheme.typography.headlineMedium, color = colors.onSurface,
                modifier = Modifier.semantics { heading() })
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            if (version.isNotBlank()) {
                Surface(shape = RoundedCornerShape(50), color = colors.primary.copy(alpha = .14f)) {
                    Text(version, style = MaterialTheme.typography.labelLarge, color = colors.primary,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp))
                }
            }
        }
    }
}

internal enum class UpdateStage { CHECKING, DOWNLOADING, VERIFYING, READY, UP_TO_DATE, ERROR, INSTALLED }

@Composable
internal fun UpdateScreenContent(
    stage: UpdateStage,
    version: String,
    progress: Float?,
    paused: Boolean,
    errorDetail: String,
    permissionDenied: Boolean,
    installerWasOpened: Boolean,
    onInstall: () -> Unit,
    onRetry: () -> Unit,
    onOpenDownloads: () -> Unit,
    onClose: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(Modifier.fillMaxSize(), color = colors.surfaceContainerLowest) {
        Box(Modifier.fillMaxSize().systemBarsPadding(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 600.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                SourcifyUpdateHero(
                    title = stringResource(R.string.s1380_update_title),
                    subtitle = stringResource(R.string.s1387_update_subtitle),
                    version = if (version.isBlank()) "" else stringResource(R.string.latest_version_format, version),
                )
                val title = stringResource(when (stage) {
                    UpdateStage.CHECKING -> R.string.checking_for_updates
                    UpdateStage.DOWNLOADING -> if (paused) R.string.s1387_update_waiting else R.string.s1387_update_downloading_title
                    UpdateStage.VERIFYING -> R.string.s1387_update_verifying_title
                    UpdateStage.READY -> R.string.s1387_update_ready_title
                    UpdateStage.UP_TO_DATE -> R.string.s10_up_to_date
                    UpdateStage.INSTALLED -> R.string.s1380_update_install_success
                    UpdateStage.ERROR -> R.string.s1380_update_failed_title
                })
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = if (stage == UpdateStage.ERROR) colors.errorContainer else colors.surfaceContainerLow,
                ) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (stage == UpdateStage.CHECKING || stage == UpdateStage.VERIFYING) {
                                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(painterResource(when (stage) {
                                    UpdateStage.ERROR -> R.drawable.error
                                    UpdateStage.DOWNLOADING -> R.drawable.download
                                    UpdateStage.READY -> R.drawable.update
                                    else -> R.drawable.check
                                }), contentDescription = null, modifier = Modifier.size(24.dp),
                                    tint = if (stage == UpdateStage.ERROR) colors.onErrorContainer else colors.primary)
                            }
                            Text(title, style = MaterialTheme.typography.titleLarge,
                                color = if (stage == UpdateStage.ERROR) colors.onErrorContainer else colors.onSurface,
                                modifier = Modifier.semantics { heading(); liveRegion = LiveRegionMode.Polite })
                        }
                        when (stage) {
                            UpdateStage.DOWNLOADING -> {
                                val value = progress
                                if (value == null) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                else {
                                    LinearProgressIndicator(progress = { value }, modifier = Modifier.fillMaxWidth())
                                    Text(stringResource(R.string.s1380_update_progress, (value * 100).toInt()),
                                        style = MaterialTheme.typography.titleMedium, color = colors.primary)
                                }
                                Text(stringResource(if (paused) R.string.s1380_update_paused else R.string.s1380_update_downloading),
                                    color = colors.onSurfaceVariant)
                            }
                            UpdateStage.VERIFYING -> Text(stringResource(R.string.s1380_update_verifying), color = colors.onSurfaceVariant)
                            UpdateStage.READY -> {
                                Text(stringResource(when {
                                    permissionDenied -> R.string.s1380_update_permission_denied
                                    installerWasOpened -> R.string.s1380_update_install_cancelled
                                    else -> R.string.s1380_update_ready
                                }), color = colors.onSurface)
                                Text(stringResource(R.string.s1380_update_install_note), style = MaterialTheme.typography.bodyMedium,
                                    color = colors.onSurfaceVariant)
                                Button(onInstall, Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                                    Text(stringResource(R.string.s1380_update_install))
                                }
                            }
                            UpdateStage.ERROR -> {
                                Text(stringResource(R.string.s1380_update_error, errorDetail), color = colors.onErrorContainer)
                                Button(onRetry, Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                                    Text(stringResource(R.string.s1380_update_retry))
                                }
                                OutlinedButton(onOpenDownloads, Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                                    Text(stringResource(R.string.s1380_update_open_downloads))
                                }
                            }
                            else -> Unit
                        }
                    }
                }
                TextButton(onClose, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.close)) }
            }
        }
    }
}

@Composable
fun SourcifyUpdateAvailableDialog(
    version: String,
    description: String,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            Modifier.widthIn(max = 560.dp).fillMaxWidth()
                .heightIn(max = (LocalConfiguration.current.screenHeightDp * .85f).dp),
            shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            SourcifyUpdateAvailableContent(version, description, onUpdate, onDismiss)
        }
    }
}

@Composable
internal fun SourcifyUpdateAvailableContent(
    version: String,
    description: String,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SourcifyUpdateHero(
            title = stringResource(R.string.update_available_title),
            subtitle = stringResource(R.string.s1387_update_subtitle),
            version = stringResource(R.string.latest_version_format, version),
        )
        if (description.isNotBlank()) {
            Text(stringResource(R.string.s1387_update_whats_new), style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 8.dp).semantics { heading() })
            Text(description, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp))
        }
        Button(onUpdate, Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Text(stringResource(R.string.s1380_update_install))
        }
        TextButton(onDismiss, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.s1387_update_later)) }
    }
}
