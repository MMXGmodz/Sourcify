package com.metrolist.music.lyrics

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class SongTitleTranslationTest {
    private fun key(text: String, language: String = "en") =
        TranslationCacheKey(text, language, "Literal", "OpenRouter", "url", "model")

    @Test
    fun `title translation deduplicates requests and separates target languages`() = runBlocking {
        val title = key("title-dedup")
        var calls = 0
        val request: suspend () -> Result<List<String>> = { calls++; delay(10); Result.success(listOf("translated")) }
        val first = async { SongTitleTranslations.translate(title, request) }
        val second = async { SongTitleTranslations.translate(title, request) }
        assertEquals("translated", first.await())
        assertEquals("translated", second.await())
        assertEquals(1, calls)
        assertEquals("traduit", SongTitleTranslations.translate(title.copy(language = "fr")) { Result.success(listOf("traduit")) })
    }

    @Test
    fun `title keeps original on failure or invalid response and avoids repeated failures`() = runBlocking {
        val title = key("title-failure")
        var calls = 0
        repeat(2) {
            assertEquals(title.text, SongTitleTranslations.translate(title) { calls++; Result.failure(Exception("offline")) })
        }
        assertEquals(1, calls)
        val invalid = key("title-invalid")
        assertEquals(invalid.text, SongTitleTranslations.translate(invalid) { Result.success(listOf("one", "two")) })
    }

    @Test
    fun `cancellation escapes without caching fallback`() = runBlocking {
        val title = key("title-cancelled")
        try {
            SongTitleTranslations.translate(title) { throw CancellationException("cancelled") }
            fail("Expected cancellation")
        } catch (_: CancellationException) {
            // A later visible row must still be able to translate the same title.
        }
        assertEquals("translated", SongTitleTranslations.translate(title) { Result.success(listOf("translated")) })
    }
}
