/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.lyrics

import android.content.Context
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.LyricsEntity
import com.metrolist.music.api.DeepLService
import com.metrolist.music.api.OpenRouterService
import com.metrolist.music.api.OpenRouterStreamingService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.CancellationException

/**
 * A helper class that provides AI-powered translation for lyrics.
 */
object LyricsTranslationHelper {
    private val _status = MutableStateFlow<TranslationStatus>(TranslationStatus.Idle)
    val status: StateFlow<TranslationStatus> = _status.asStateFlow()

    private val _hasActiveTranslations = MutableStateFlow(false)
    val hasActiveTranslations: StateFlow<Boolean> = _hasActiveTranslations.asStateFlow()

    private val _manualTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val manualTrigger: SharedFlow<Unit> = _manualTrigger.asSharedFlow()

    private val _clearTranslationsTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val clearTranslationsTrigger: SharedFlow<Unit> = _clearTranslationsTrigger.asSharedFlow()

    private val generation = java.util.concurrent.atomic.AtomicLong()
    private var translationJob: kotlinx.coroutines.Job? = null
    private var isCompositionActive = true

    // Cache translations in memory to avoid redundant API calls during a session
    private val translationCache = TranslationCache<TranslationCacheKey, List<String>>(32)

    // Map of language codes to full names for better AI understanding
    private val LanguageCodeToName = mapOf(
        "en" to "English",
        "es" to "Spanish",
        "fr" to "French",
        "de" to "German",
        "it" to "Italian",
        "pt" to "Portuguese",
        "ru" to "Russian",
        "ja" to "Japanese",
        "ko" to "Korean",
        "zh" to "Chinese",
        "ar" to "Arabic",
        "hi" to "Hindi",
        "bn" to "Bengali",
        "pa" to "Punjabi",
        "tr" to "Turkish",
        "vi" to "Vietnamese",
        "th" to "Thai",
        "id" to "Indonesian",
        "pl" to "Polish",
        "nl" to "Dutch",
        "sv" to "Swedish",
        "uk" to "Ukrainian"
    )

    fun setCompositionActive(active: Boolean) {
        isCompositionActive = active
        if (!active) cancelTranslation()
    }

    fun triggerManualTranslation() {
        _manualTrigger.tryEmit(Unit)
    }

    fun triggerClearTranslations() {
        cancelTranslation()
        translationCache.clear()
        _clearTranslationsTrigger.tryEmit(Unit)
        _hasActiveTranslations.value = false
    }

    fun clearTranslations(lyrics: LyricsEntity): LyricsEntity {
        return lyrics.copy(
            translatedLyrics = "",
            translationLanguage = "",
            translationMode = ""
        )
    }

    fun cancelTranslation() {
        generation.incrementAndGet()
        translationJob?.cancel()
        _status.value = TranslationStatus.Idle
    }

    fun translationIdentity(
        mode: String,
        provider: String,
        baseUrl: String,
        model: String,
        systemPrompt: String,
        formality: String,
    ): String = mode + ":" + java.security.MessageDigest.getInstance("SHA-256")
        .digest(listOf(provider, baseUrl, model, systemPrompt, formality).joinToString("\u0000").toByteArray())
        .joinToString("") { "%02x".format(it) }

    fun loadTranslationsFromDatabase(
        lyrics: List<LyricsEntry>,
        lyricsEntity: LyricsEntity?,
        targetLanguage: String,
        mode: String
    ) {
        if (lyricsEntity == null || lyricsEntity.translatedLyrics.isNullOrBlank()) {
            _hasActiveTranslations.value = false
            lyrics.forEach { it.translatedTextFlow.value = null }
            return
        }
        
        // Only load if language and mode match
        if (lyricsEntity.translationLanguage != targetLanguage || lyricsEntity.translationMode != mode) {
            _hasActiveTranslations.value = false
            lyrics.forEach { it.translatedTextFlow.value = null }
            return
        }
        
        val translatedLines = lyricsEntity.translatedLyrics.split("\n")
        val nonEmptyEntries = lyrics.filter { it.text.isNotBlank() }
        
        lyrics.forEach { it.translatedTextFlow.value = null }
        _hasActiveTranslations.value = false
        if (validTranslation(translatedLines, nonEmptyEntries.size)) {
            var transIndex = 0
            lyrics.forEach { entry ->
                if (entry.text.isNotBlank() && transIndex < translatedLines.size) {
                    entry.translatedTextFlow.value = translatedLines[transIndex]
                    transIndex++
                }
            }
            
            _hasActiveTranslations.value = true
        }
    }

    fun translateLyrics(
        lyrics: List<LyricsEntry>,
        targetLanguage: String,
        apiKey: String,
        baseUrl: String,
        model: String,
        mode: String,
        scope: CoroutineScope,
        context: Context,
        provider: String = "OpenRouter",
        deeplApiKey: String = "",
        deeplFormality: String = "default",
        useStreaming: Boolean = true,
        songId: String = "",
        database: MusicDatabase? = null,
        systemPrompt: String = "",
    ) {
        cancelTranslation()
        _status.value = TranslationStatus.Translating
        _hasActiveTranslations.value = false
        lyrics.forEach { it.translatedTextFlow.value = null }
        val requestGeneration = generation.get()
        translationJob = scope.launch(Dispatchers.Main.immediate) {
            try {
                val effectiveApiKey = if (provider == "DeepL") deeplApiKey else apiKey
                require(effectiveApiKey.isNotBlank()) { context.getString(com.metrolist.music.R.string.ai_error_api_key_required) }
                require(targetLanguage.isNotBlank()) { context.getString(com.metrolist.music.R.string.ai_error_language_required) }
                val entries = lyrics.filter { it.text.isNotBlank() }
                require(entries.isNotEmpty()) { context.getString(com.metrolist.music.R.string.ai_error_no_lyrics) }
                val text = entries.joinToString("\n") { it.text }
                val cacheKey = TranslationCacheKey(text, targetLanguage, mode, provider, baseUrl, model, systemPrompt, deeplFormality, translationCredentialFingerprint(effectiveApiKey))
                val originalEntity = if (songId.isNotBlank()) database?.lyrics(songId)?.first() else null
                val translatedLines = translationCache[cacheKey] ?: run {
                    val language = LanguageCodeToName[targetLanguage]
                        ?: Locale.forLanguageTag(targetLanguage).getDisplayLanguage(Locale.ENGLISH).ifBlank { targetLanguage }
                    val result = if (provider == "DeepL") {
                        DeepLService.translate(text, targetLanguage, deeplApiKey, deeplFormality)
                    } else if (useStreaming && provider != "Custom") {
                        var completed: List<String>? = null
                        var failure: String? = null
                        OpenRouterStreamingService.streamTranslation(
                            text, language, apiKey, baseUrl, model, mode, systemPrompt,
                        ).collect { chunk ->
                            when (chunk) {
                                is OpenRouterStreamingService.StreamChunk.Content -> Unit
                                is OpenRouterStreamingService.StreamChunk.Complete -> completed = chunk.translatedLines
                                is OpenRouterStreamingService.StreamChunk.Error -> failure = chunk.message
                            }
                        }
                        failure?.let { error(it) }
                        Result.success(completed ?: error("No translation received"))
                    } else {
                        OpenRouterService.translate(text, language, apiKey, baseUrl, model, mode, customSystemPrompt = systemPrompt)
                    }
                    result.getOrThrow().also {
                        require(validTranslation(it, entries.size)) { "Translation lines do not match the lyrics" }
                        currentCoroutineContext().ensureActive()
                        translationCache[cacheKey] = it
                    }
                }
                currentCoroutineContext().ensureActive()
                if (!isCompositionActive) return@launch
                if (database != null && originalEntity != null) {
                    val identity = translationIdentity(mode, provider, baseUrl, model, systemPrompt, deeplFormality)
                    database.transaction {
                        if (generation.get() == requestGeneration) {
                            // Conditional update preserves a concurrently replaced source and all other metadata.
                            openHelper.writableDatabase.execSQL(
                                "UPDATE lyrics SET translatedLyrics = ?, translationLanguage = ?, translationMode = ? WHERE id = ? AND lyrics = ?",
                                arrayOf(translatedLines.joinToString("\n"), targetLanguage, identity, songId, originalEntity.lyrics),
                            )
                        }
                    }
                }

                currentCoroutineContext().ensureActive()
                entries.forEachIndexed { index, entry -> entry.translatedTextFlow.value = translatedLines[index] }
                _hasActiveTranslations.value = true
                _status.value = TranslationStatus.Success
                delay(3000)
                _status.value = TranslationStatus.Idle
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                currentCoroutineContext().ensureActive()
                if (isCompositionActive) {
                    _status.value = TranslationStatus.Error(error.message ?: context.getString(com.metrolist.music.R.string.ai_error_translation_failed))
                }
            }
        }
    }

    sealed class TranslationStatus {
        data object Idle : TranslationStatus()
        data object Translating : TranslationStatus()
        data object Success : TranslationStatus()
        data class Error(val message: String) : TranslationStatus()
    }
}
