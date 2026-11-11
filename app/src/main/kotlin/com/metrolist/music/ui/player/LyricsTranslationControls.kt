package com.metrolist.music.ui.player

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.metrolist.music.LocalDatabase
import com.metrolist.music.R
import com.metrolist.music.constants.*
import com.metrolist.music.db.entities.LyricsEntity
import com.metrolist.music.lyrics.LyricsEntry
import com.metrolist.music.lyrics.LyricsTranslationHelper
import com.metrolist.music.utils.rememberPreference

@Composable
internal fun LyricsTranslationControls(songId: String, entries: List<LyricsEntry>, entity: LyricsEntity?) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val scope = rememberCoroutineScope()
    val provider by rememberPreference(AiProviderKey, "OpenRouter")
    val key by rememberPreference(OpenRouterApiKey, "")
    val deepLKey by rememberPreference(DeeplApiKey, "")
    val url by rememberPreference(OpenRouterBaseUrlKey, OpenRouterDefaultBaseUrl)
    val model by rememberPreference(OpenRouterModelKey, OpenRouterDefaultModel)
    val language by rememberPreference(TranslateLanguageKey, "en")
    val mode by rememberPreference(TranslateModeKey, "Literal")
    val formality by rememberPreference(DeeplFormalityKey, "default")
    val prompt by rememberPreference(AiSystemPromptKey, "")
    val status by LyricsTranslationHelper.status.collectAsStateWithLifecycle()
    var missingKey by remember { mutableStateOf(false) }
    val identity = remember(mode, provider, url, model, prompt, formality) {
        LyricsTranslationHelper.translationIdentity(mode, provider, url, model, prompt, formality)
    }
    DisposableEffect(entries, language, identity, key, deepLKey) {
        LyricsTranslationHelper.setCompositionActive(true)
        onDispose { LyricsTranslationHelper.cancelTranslation(); LyricsTranslationHelper.setCompositionActive(false) }
    }
    LaunchedEffect(entries, entity, language, identity) {
        LyricsTranslationHelper.loadTranslationsFromDatabase(entries, entity, language, identity)
    }
    val busy = status is LyricsTranslationHelper.TranslationStatus.Translating
    Row(Modifier.fillMaxWidth()) {
        TextButton(enabled = entries.isNotEmpty() && !busy, onClick = {
            missingKey = (if (provider == "DeepL") deepLKey else key).isBlank()
            if (!missingKey) LyricsTranslationHelper.translateLyrics(entries, language, key, url, model, mode,
                scope, context, provider, deepLKey, formality, true, songId, database, prompt)
        }) { Text(stringResource(R.string.s813_translate_lyrics), color = Color.White) }
        if (busy) TextButton(onClick = { LyricsTranslationHelper.cancelTranslation() }) {
            Text(stringResource(android.R.string.cancel), color = Color.White)
        }
    }
    when {
        missingKey -> Text(stringResource(R.string.ai_error_api_key_required), color = Color.White.copy(alpha = .8f))
        busy -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 4.dp))
        status is LyricsTranslationHelper.TranslationStatus.Error -> Text(
            (status as LyricsTranslationHelper.TranslationStatus.Error).message,
            color = Color.White.copy(alpha = .8f), style = MaterialTheme.typography.bodySmall)
    }
}
