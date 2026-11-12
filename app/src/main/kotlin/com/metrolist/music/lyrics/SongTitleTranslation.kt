package com.metrolist.music.lyrics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.metrolist.music.api.DeepLService
import com.metrolist.music.api.OpenRouterService
import com.metrolist.music.constants.*
import com.metrolist.music.utils.rememberPreference
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Locale

private data class CachedTitle(val text: String, val expiresAt: Long)

internal object SongTitleTranslations {
    private val cache = TranslationCache<TranslationCacheKey, CachedTitle>(256)
    private val requests = ArrayDeque<Long>()
    private val mutex = Mutex()

    // Serialize/deduplicate visible-row requests and cap new requests to 30 per minute.
    suspend fun translate(key: TranslationCacheKey, request: suspend () -> Result<List<String>>): String = mutex.withLock {
        val now = System.nanoTime() / 1_000_000
        cache[key]?.takeIf { it.expiresAt > now }?.let { return@withLock it.text }
        while (requests.firstOrNull()?.let { now - it >= 60_000 } == true) requests.removeFirst()
        if (requests.size >= 30) return@withLock key.text
        requests.addLast(now)
        val translated = request().getOrNull()?.takeIf { validTranslation(it, 1) }?.single()
        val value = translated ?: key.text
        cache[key] = CachedTitle(value, if (translated != null) Long.MAX_VALUE else now + 60_000)
        value
    }
}

@Composable
fun rememberTranslatedSongTitle(original: String): String {
    val enabled by rememberPreference(TranslateSongTitlesKey, false)
    if (!enabled || original.isBlank() || original.contains('\n') || original.contains('\r')) return original
    val target by rememberPreference(TranslateLanguageKey, "en")
    val provider by rememberPreference(AiProviderKey, "OpenRouter")
    val apiKey by rememberPreference(OpenRouterApiKey, "")
    val deeplKey by rememberPreference(DeeplApiKey, "")
    val baseUrl by rememberPreference(OpenRouterBaseUrlKey, OpenRouterDefaultBaseUrl)
    val model by rememberPreference(OpenRouterModelKey, OpenRouterDefaultModel)
    val formality by rememberPreference(DeeplFormalityKey, "default")
    val effectiveKey = if (provider == "DeepL") deeplKey else apiKey
    if (effectiveKey.isBlank() || target.isBlank()) return original
    val key = TranslationCacheKey(original, target, "Literal", provider, baseUrl, model, formality = formality, credentialFingerprint = translationCredentialFingerprint(effectiveKey))
    var title by remember(key, effectiveKey) { mutableStateOf(original) }
    LaunchedEffect(key, effectiveKey) {
        delay(350)
        title = SongTitleTranslations.translate(key) {
            if (provider == "DeepL") {
                DeepLService.translate(original, target, effectiveKey, formality, maxRetries = 1)
            } else {
                val language = Locale.forLanguageTag(target).getDisplayLanguage(Locale.ENGLISH).ifBlank { target }
                OpenRouterService.translate(
                    original, language, effectiveKey, baseUrl, model, "Literal", maxRetries = 1,
                    customSystemPrompt = "Translate the song title to $language. Preserve names when appropriate. Return only a JSON object with exactly one string in a lines array. Do not add commentary.",
                )
            }
        }
    }
    return title
}
