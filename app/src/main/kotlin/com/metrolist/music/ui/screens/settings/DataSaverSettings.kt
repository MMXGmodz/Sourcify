package com.metrolist.music.ui.screens.settings

import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.R
import com.metrolist.music.constants.*
import com.metrolist.music.ui.component.Material3SettingsGroup
import com.metrolist.music.ui.component.Material3SettingsItem
import com.metrolist.music.ui.utils.backToMain
import com.metrolist.music.utils.rememberPreference

@Composable
fun DataSaverSettings(navController: NavController) {
    SettingsPage(stringResource(R.string.data_saver), { navController.navigateUp() },
        bottom = LocalPlayerAwareWindowInsets.current.asPaddingValues().calculateBottomPadding(),
        onBackToMain = navController::backToMain) {
        DataSaverPreferences()
    }
}

@Composable
internal fun DataSaverPreferences() {
    val (enabled, setEnabled) = rememberPreference(DataSaverKey, true)
    val (mobile, setMobile) = rememberPreference(DownloadMobileAllowedKey, false)
    val (background, setBackground) = rememberPreference(DownloadBackgroundAllowedKey, false)
    Material3SettingsGroup(items = listOf(Material3SettingsItem(
        icon = painterResource(R.drawable.wifi_proxy),
        title = { Text(stringResource(R.string.data_saver)) },
        description = { Text(stringResource(R.string.data_saver_description)) },
        trailingContent = { Switch(enabled, setEnabled) },
        onClick = { setEnabled(!enabled) },
    )))
    Text(stringResource(R.string.data_saver_download_note))
    Material3SettingsGroup(title = stringResource(R.string.s9_downloads), items = listOf(
        Material3SettingsItem(
            icon = painterResource(R.drawable.download),
            title = { Text(stringResource(R.string.s9_mobile_downloads)) },
            description = { Text(stringResource(R.string.s9_mobile_desc)) },
            trailingContent = { Switch(mobile && !enabled, setMobile, enabled = !enabled) },
            onClick = { if (!enabled) setMobile(!mobile) },
        ),
        Material3SettingsItem(
            icon = painterResource(R.drawable.download),
            title = { Text(stringResource(R.string.s9_background_downloads)) },
            description = { Text(stringResource(R.string.s9_background_desc)) },
            trailingContent = { Switch(background, setBackground) },
            onClick = { setBackground(!background) },
        ),
        Material3SettingsItem(
            icon = painterResource(R.drawable.wifi_proxy),
            title = { Text(stringResource(R.string.canvas_wifi_only)) },
            description = { Text(stringResource(R.string.canvas_wifi_only_description)) },
        ),
    ))
}
