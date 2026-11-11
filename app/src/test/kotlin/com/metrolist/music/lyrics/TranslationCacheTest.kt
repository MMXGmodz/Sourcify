package com.metrolist.music.lyrics

import org.junit.Assert.*
import org.junit.Test

class TranslationCacheTest {
    private fun key(text: String = "Aa", language: String = "en", provider: String = "OpenRouter") =
        TranslationCacheKey(text, language, "Literal", provider, "url", "model")

    @Test
    fun `cache distinguishes hash collisions languages and provider configuration`() {
        assertEquals("Aa".hashCode(), "BB".hashCode())
        val cache = TranslationCache<TranslationCacheKey, String>(10)
        cache[key()] = "translated"
        assertNull(cache[key(text = "BB")])
        assertNull(cache[key(language = "fr")])
        assertNull(cache[key(provider = "DeepL")])
        assertNull(cache[key().copy(model = "other")])
        assertNull(cache[key().copy(prompt = "other")])
    }

    @Test
    fun `cache evicts least recently used entry and clears`() {
        val cache = TranslationCache<String, String>(2)
        cache["a"] = "A"
        cache["b"] = "B"
        assertEquals("A", cache["a"])
        cache["c"] = "C"
        assertNull(cache["b"])
        assertEquals("C", cache["c"])
        cache.clear()
        assertNull(cache["a"])
    }

    @Test
    fun `alignment rejects incomplete blank and embedded lines`() {
        assertTrue(validTranslation(listOf("uno", "dos"), 2))
        assertFalse(validTranslation(listOf("uno"), 2))
        assertFalse(validTranslation(listOf("uno", "dos", "tres"), 2))
        assertFalse(validTranslation(listOf("uno", ""), 2))
        assertFalse(validTranslation(listOf("uno\ndos"), 1))
    }
}
