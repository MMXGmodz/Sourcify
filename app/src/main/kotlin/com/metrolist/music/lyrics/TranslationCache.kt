package com.metrolist.music.lyrics

internal data class TranslationCacheKey(
    val text: String,
    val language: String,
    val mode: String,
    val provider: String,
    val baseUrl: String,
    val model: String,
    val prompt: String = "",
    val formality: String = "default",
    val credentialFingerprint: String = "",
)

internal class TranslationCache<K, V>(private val capacity: Int) {
    private val values = object : LinkedHashMap<K, V>(capacity, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?) = size > capacity
    }
    @Synchronized operator fun get(key: K): V? = values[key]
    @Synchronized operator fun set(key: K, value: V) { values[key] = value }
    @Synchronized fun clear() = values.clear()
}

internal fun validTranslation(lines: List<String>, expectedCount: Int): Boolean =
    expectedCount > 0 && lines.size == expectedCount && lines.all {
        it.isNotBlank() && !it.contains('\n') && !it.contains('\r')
    }

internal fun translationCredentialFingerprint(key: String): String =
    java.security.MessageDigest.getInstance("SHA-256").digest(key.toByteArray())
        .joinToString("") { "%02x".format(it) }
