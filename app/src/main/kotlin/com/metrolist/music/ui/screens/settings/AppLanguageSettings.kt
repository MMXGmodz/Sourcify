package com.metrolist.music.ui.screens.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import com.metrolist.music.R
import com.metrolist.music.constants.AppLanguageKey
import com.metrolist.music.constants.LanguageCodeToName
import com.metrolist.music.constants.SYSTEM_DEFAULT
import com.metrolist.music.ui.component.EnumDialog
import com.metrolist.music.ui.component.Material3SettingsItem
import com.metrolist.music.utils.rememberPreference
import com.metrolist.music.utils.safeDataStoreEdit
import kotlinx.coroutines.launch

@Composable
internal fun appLanguageSettingsItem(): Material3SettingsItem {
    val context = LocalContext.current
    val (appLanguage) = rememberPreference(AppLanguageKey, SYSTEM_DEFAULT)
    val coroutineScope = rememberCoroutineScope()
    var showDialog by rememberSaveable { mutableStateOf(false) }
    val nativeLanguageSettings = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    val localeTag = if (nativeLanguageSettings) {
        context.getSystemService(android.app.LocaleManager::class.java).applicationLocales.toLanguageTags()
            .ifEmpty { SYSTEM_DEFAULT }
    } else appLanguage
    // Read the configuration so the row refreshes after returning from system language settings.
    val configuration = LocalConfiguration.current
    val languageName = LanguageCodeToName[localeTag]
        ?: LanguageCodeToName[localeTag.substringBefore('-')]
        ?: if (localeTag == SYSTEM_DEFAULT) stringResource(R.string.system_default)
        else configuration.locales[0].displayName

    if (showDialog) {
        EnumDialog(
            onDismiss = { showDialog = false },
            onSelect = { language ->
                showDialog = false
                coroutineScope.launch { applyLegacyAppLanguage(context, language) }
            },
            title = stringResource(R.string.s1382_app_language),
            current = appLanguage,
            values = listOf(SYSTEM_DEFAULT) + LanguageCodeToName.keys.toList(),
            valueText = { LanguageCodeToName.getOrElse(it) { stringResource(R.string.system_default) } },
        )
    }
    return Material3SettingsItem(
        icon = painterResource(R.drawable.language),
        title = { Text(stringResource(R.string.s1382_app_language)) },
        description = { Text(languageName) },
        onClick = {
            if (nativeLanguageSettings) {
                context.startActivity(Intent(Settings.ACTION_APP_LOCALE_SETTINGS, "package:${context.packageName}".toUri()))
            } else {
                showDialog = true
            }
        },
    )
}

internal suspend fun applyLegacyAppLanguage(context: Context, language: String) {
    if (!context.safeDataStoreEdit { it[AppLanguageKey] = language }) return
    var current = context
    while (current is ContextWrapper) {
        if (current is Activity) {
            current.recreate()
            return
        }
        current = current.baseContext
    }
}
