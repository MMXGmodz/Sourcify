package com.metrolist.music

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.metrolist.music.ui.component.UpdateScreenContent
import com.metrolist.music.ui.component.UpdateStage
import com.metrolist.music.ui.theme.MetrolistTheme
import com.metrolist.music.utils.UpdateDownload
import com.metrolist.music.utils.UpdateInstaller
import com.metrolist.music.utils.UpdateNotifications
import com.metrolist.music.utils.Updater
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** User-visible owner of update installation; background receivers only post a notification. */
class UpdateActivity : ComponentActivity() {
    companion object {
        fun createIntent(context: Context): Intent = Intent(context, UpdateActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }

    private var stage by mutableStateOf(UpdateStage.CHECKING)
    private var version by mutableStateOf("")
    private var progress by mutableStateOf<Float?>(null)
    private var paused by mutableStateOf(false)
    private var errorDetail by mutableStateOf("")
    private var showPermissionDialog by mutableStateOf(false)
    private var permissionDenied by mutableStateOf(false)
    private var installerWasOpened by mutableStateOf(false)
    private var autoInstall = true
    private var installed = false
    private var updateJob: Job? = null

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        permissionDenied = !packageManager.canRequestPackageInstalls()
        autoInstall = !permissionDenied
    }
    private val installerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        autoInstall = false
        if (result.resultCode == RESULT_OK) {
            installed = true
            stage = UpdateStage.INSTALLED
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        autoInstall = savedInstanceState?.getBoolean("auto_install", true) ?: true
        permissionDenied = savedInstanceState?.getBoolean("permission_denied", false) ?: false
        installerWasOpened = savedInstanceState?.getBoolean("installer_opened", false) ?: false
        installed = savedInstanceState?.getBoolean("installed", false) ?: false
        setContent { MetrolistTheme { UpdateContent() } }
    }

    override fun onResume() {
        super.onResume()
        resumeUpdate()
    }

    override fun onPause() {
        updateJob?.cancel()
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("auto_install", autoInstall)
        outState.putBoolean("permission_denied", permissionDenied)
        outState.putBoolean("installer_opened", installerWasOpened)
        outState.putBoolean("installed", installed)
        super.onSaveInstanceState(outState)
    }

    private fun resumeUpdate(retry: Boolean = false) {
        updateJob?.cancel()
        if (installed) {
            stage = UpdateStage.INSTALLED
            return
        }
        stage = UpdateStage.CHECKING
        updateJob = lifecycleScope.launch {
            try {
                var owned = withContext(Dispatchers.IO) {
                    if (retry) UpdateDownload.forget(applicationContext)
                    UpdateDownload.ownedDownload(applicationContext)
                }
                if (owned != null && !Updater.isUpdateAvailable(BuildConfig.BASE_VERSION_NAME, owned.version)) {
                    withContext(Dispatchers.IO) { UpdateDownload.forget(applicationContext) }
                    owned = null
                }
                val id = if (owned != null) {
                    version = owned.version
                    owned.id
                } else {
                    val (release, available) = Updater.checkForUpdate(forceRefresh = true).getOrThrow()
                    if (!available || release == null || Updater.getDownloadUrlForCurrentVariant(release) == null) {
                        stage = UpdateStage.UP_TO_DATE
                        return@launch
                    }
                    version = release.versionName
                    withContext(Dispatchers.IO) { UpdateDownload.enqueue(applicationContext, release) }
                }
                while (isActive) {
                    val snapshot = withContext(Dispatchers.IO) { UpdateDownload.query(applicationContext, id) }
                    when (snapshot.status) {
                        DownloadManager.STATUS_PENDING, DownloadManager.STATUS_RUNNING, DownloadManager.STATUS_PAUSED -> {
                            stage = UpdateStage.DOWNLOADING
                            paused = snapshot.status == DownloadManager.STATUS_PAUSED
                            progress = if (snapshot.totalBytes > 0) {
                                (snapshot.downloadedBytes.toFloat() / snapshot.totalBytes).coerceIn(0f, 1f)
                            } else null
                            delay(1000)
                        }
                        DownloadManager.STATUS_SUCCESSFUL -> {
                            stage = UpdateStage.VERIFYING
                            val uri = withContext(Dispatchers.IO) { UpdateDownload.verifiedInstallUri(applicationContext, id) }
                            stage = UpdateStage.READY
                            UpdateNotifications.dismissDownload(this@UpdateActivity)
                            if (autoInstall && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                                if (!packageManager.canRequestPackageInstalls()) {
                                    showPermissionDialog = true
                                } else {
                                    autoInstall = false
                                    installerWasOpened = true
                                    try {
                                        installerLauncher.launch(UpdateInstaller.installIntent(this@UpdateActivity, uri))
                                    } catch (_: Exception) {
                                        errorDetail = getString(R.string.s1380_update_no_installer)
                                        stage = UpdateStage.ERROR
                                    }
                                }
                            }
                            return@launch
                        }
                        DownloadManager.STATUS_FAILED -> error(getString(R.string.s1380_update_download_error, snapshot.reason))
                        else -> error(getString(R.string.s1380_update_unavailable))
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                errorDetail = error.message ?: getString(R.string.s1380_update_unavailable)
                stage = UpdateStage.ERROR
            }
        }
    }

    @Composable
    private fun UpdateContent() {
        Surface(Modifier.fillMaxSize()) {
            UpdateScreenContent(
                stage = stage,
                version = version,
                progress = progress,
                paused = paused,
                errorDetail = errorDetail,
                permissionDenied = permissionDenied,
                installerWasOpened = installerWasOpened,
                onInstall = {
                    autoInstall = true
                    resumeUpdate()
                },
                onRetry = {
                    autoInstall = true
                    permissionDenied = false
                    installerWasOpened = false
                    resumeUpdate(retry = true)
                },
                onOpenDownloads = {
                    try {
                        startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS))
                    } catch (_: Exception) {
                        Toast.makeText(this@UpdateActivity, R.string.s1380_update_unavailable, Toast.LENGTH_LONG).show()
                    }
                },
                onClose = { finish() },
            )
            if (showPermissionDialog) {
                AlertDialog(
                    onDismissRequest = { showPermissionDialog = false; autoInstall = false },
                    title = { Text(stringResource(R.string.s1380_update_permission_title)) },
                    text = { Text(stringResource(R.string.s1380_update_permission_message)) },
                    confirmButton = {
                        TextButton(onClick = {
                            showPermissionDialog = false
                            try {
                                permissionLauncher.launch(UpdateInstaller.permissionIntent(this@UpdateActivity))
                            } catch (_: Exception) {
                                autoInstall = false
                                errorDetail = getString(R.string.s1380_update_no_settings)
                                stage = UpdateStage.ERROR
                            }
                        }) { Text(stringResource(R.string.s1380_update_permission_action)) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showPermissionDialog = false; autoInstall = false }) {
                            Text(stringResource(R.string.cancel))
                        }
                    },
                )
            }
        }
    }
}
