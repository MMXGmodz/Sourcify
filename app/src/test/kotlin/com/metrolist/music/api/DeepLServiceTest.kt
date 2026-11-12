package com.metrolist.music.api

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.fail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeepLServiceTest {
    @Test
    fun `long lyrics use batches of at most 50 and preserve every line in order`() = runBlocking {
        val input = (1..121).map { "line $it" }
        val sizes = mutableListOf<Int>()
        val result = translateDeepLBatches(input) { batch ->
            sizes += batch.size
            Result.success(batch.map { "translated $it" })
        }
        assertEquals(listOf(50, 50, 21), sizes)
        assertEquals(input.map { "translated $it" }, result.getOrThrow())
    }

    @Test
    fun `batch failure discards earlier translations and stops further requests`() = runBlocking {
        var calls = 0
        val result = translateDeepLBatches((1..121).map(Int::toString)) { batch ->
            calls++
            if (calls == 2) Result.failure(Exception("quota exceeded")) else Result.success(batch)
        }
        assertTrue(result.isFailure)
        assertEquals("quota exceeded", result.exceptionOrNull()?.message)
        assertEquals(2, calls)
    }

    @Test
    fun `invalid batch fails and cancellation propagates`() = runBlocking {
        assertTrue(translateDeepLBatches(listOf("one", "two")) { Result.success(listOf("one")) }.isFailure)
        var calls = 0
        try {
            translateDeepLBatches((1..60).map(Int::toString)) { batch ->
                calls++
                if (calls == 2) throw CancellationException("cancelled")
                Result.success(batch)
            }
            fail("Expected cancellation")
        } catch (_: CancellationException) {
            assertEquals(2, calls)
        }
    }

    @Test
    fun `valid response preserves order`() {
        assertEquals(listOf("uno", "dos"), parseDeepLTranslation("""{"translations":[{"text":"uno"},{"text":"dos"}]}""", 2).getOrThrow())
    }

    @Test
    fun `missing extra empty and malformed translations fail`() {
        listOf(
            """{"translations":[{"text":"uno"}]}""",
            """{"translations":[{"text":"uno"},{"text":"dos"},{"text":"extra"}]}""",
            """{"translations":[{"text":"uno"},{"text":""}]}""",
            """{"translations":[{"text":"uno"},{}]}""",
            """{"translations":[{"text":"uno"},{"text":"dos\ntres"}]}""",
            """{"translations":[{"text":"uno"},{"text":12}]}""",
            """{"message":"quota exceeded"}""",
            "invalid JSON",
        ).forEach { assertTrue(it, parseDeepLTranslation(it, 2).isFailure) }
    }
}
