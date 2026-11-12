/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.JsonPrimitive
import java.util.concurrent.TimeUnit

object DeepLService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    private val JSON = "application/json; charset=utf-8".toMediaType()

    suspend fun translate(
        text: String,
        targetLanguage: String,
        apiKey: String,
        formality: String = "default",
        maxRetries: Int = 3
    ): Result<List<String>> = withContext(Dispatchers.IO) {
        if (text.isBlank()) {
            return@withContext Result.failure(Exception("Input text is empty"))
        }
        translateDeepLBatches(text.lines()) { lines ->
            translateBatch(lines, targetLanguage, apiKey, formality, maxRetries)
        }
    }

    private suspend fun translateBatch(
        lines: List<String>,
        targetLanguage: String,
        apiKey: String,
        formality: String,
        maxRetries: Int,
    ): Result<List<String>> {
        var currentAttempt = 0
        val lineCount = lines.size

        // DeepL language codes (uppercase)
        val deeplLangCode = when (targetLanguage.lowercase()) {
            "zh", "zh-cn", "zh-hans" -> "ZH"
            "zh-tw", "zh-hant" -> "ZH"
            "en", "en-us" -> "EN-US"
            "en-gb" -> "EN-GB"
            "pt", "pt-pt" -> "PT-PT"
            "pt-br" -> "PT-BR"
            else -> targetLanguage.uppercase().take(2)
        }
        
        // Determine if using free or pro API
        val baseUrl = if (apiKey.endsWith(":fx")) {
            "https://api-free.deepl.com/v2/translate"
        } else {
            "https://api.deepl.com/v2/translate"
        }
        
        while (currentAttempt < maxRetries) {
            try {
                val jsonBody = buildJsonObject {
                    put("text", buildJsonArray { lines.forEach { add(it) } })
                    put("target_lang", deeplLangCode)
                    if (formality != "default") put("formality", formality)
                    put("preserve_formatting", true)
                }

                val request = Request.Builder()
                    .url(baseUrl)
                    .addHeader("Authorization", "DeepL-Auth-Key ${apiKey.trim()}")
                    .addHeader("Content-Type", "application/json")
                    .post(jsonBody.toString().toRequestBody(JSON))
                    .build()

                val result = withTranslationResponse(client, request) { response ->
                    val responseBody = response.body.string()
                    if (!response.isSuccessful) {
                        if (response.code >= 500) throw java.io.IOException("HTTP ${response.code}")
                        return@withTranslationResponse Result.failure<List<String>>(
                            Exception("Translation failed: HTTP ${response.code}: ${response.message}"),
                        )
                    }
                    parseDeepLTranslation(responseBody, lineCount)
                }
                return result
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                if (currentAttempt == maxRetries - 1) {
                    return Result.failure(e)
                }
            }
            currentAttempt++
            kotlinx.coroutines.delay(1000L * currentAttempt)
        }
        return Result.failure(Exception("Max retries exceeded"))
    }
}

internal suspend fun translateDeepLBatches(
    lines: List<String>,
    request: suspend (List<String>) -> Result<List<String>>,
): Result<List<String>> = try {
    val translated = ArrayList<String>(lines.size)
    // DeepL accepts at most 50 text entries per request; publish only after every batch succeeds.
    for (batch in lines.chunked(50)) {
        currentCoroutineContext().ensureActive()
        val result = request(batch).getOrThrow()
        require(result.size == batch.size && result.all {
            it.isNotBlank() && !it.contains('\n') && !it.contains('\r')
        }) { "Translation line count or content mismatch" }
        translated.addAll(result)
    }
    currentCoroutineContext().ensureActive()
    Result.success(translated)
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (error: Exception) {
    Result.failure(error)
}

internal fun parseDeepLTranslation(body: String, expectedLines: Int): Result<List<String>> = runCatching {
    val translations = Json.parseToJsonElement(body).jsonObject["translations"]?.jsonArray
        ?: error("Missing translations")
    require(translations.size == expectedLines) { "Translation line count mismatch" }
    translations.map {
        val text = it.jsonObject["text"] as? JsonPrimitive
        require(text != null && text.isString) { "Missing translated text" }
        require(text.content.isNotBlank() && !text.content.contains('\n') && !text.content.contains('\r')) {
            "Invalid translated line"
        }
        text.content
    }
}
