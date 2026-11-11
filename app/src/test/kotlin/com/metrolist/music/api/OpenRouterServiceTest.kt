package com.metrolist.music.api

import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenRouterServiceTest {
    @Test
    fun `translation parsing rejects short responses instead of padding them`() {
        assertTrue(parseTranslationContent("```json\n[\"uno\"]\n```", 2).isFailure)
    }

    @Test
    fun `translation parsing handles the structured output object shape`() {
        assertEquals(
            listOf("uno", "dos"),
            parseTranslationContent("""{"lines": ["uno", "dos"]}""", 2).getOrThrow(),
        )
    }

    @Test
    fun `translation rejects invalid lines and unsolicited prose`() {
        listOf(
            """{"lines":["one","two","extra"]}""",
            """{"lines":["one",""]}""",
            """{"lines":["one",12]}""",
            """{"lines":["one",null]}""",
            """{"lines":["one","two\nthree"]}""",
            "Here is the translation:\none\ntwo",
            "{\"lines\":[\"one\",",
        ).forEach { assertTrue(it, parseTranslationContent(it, 2).isFailure) }
    }

    @Test
    fun `complete fenced response preserves line order and punctuation`() {
        assertEquals(listOf("1. Uno", "- Dos"), parseTranslationContent("```json\n[\"1. Uno\",\"- Dos\"]\n```", 2).getOrThrow())
    }

    @Test
    fun `API failures use status fallback for malformed or empty bodies`() {
        assertEquals("bad key", apiErrorMessage("""{"error":{"message":"bad key"}}""", 401, "Unauthorized"))
        assertEquals("HTTP 401: Unauthorized", apiErrorMessage("not json", 401, "Unauthorized"))
        assertEquals("HTTP 429: Too Many Requests", apiErrorMessage("", 429, "Too Many Requests"))
    }

    @Test
    fun `request uses structured outputs with a lines schema and strict parameter routing`() {
        val request =
            buildTranslationRequest(
                text = "one\ntwo",
                targetLanguage = "Spanish",
                model = "model",
                mode = "Translated",
                customSystemPrompt = "",
            )

        val responseFormat = request.getValue("response_format").jsonObject
        assertEquals("json_schema", responseFormat.getValue("type").jsonPrimitive.content)
        val schema = responseFormat.getValue("json_schema").jsonObject.getValue("schema").jsonObject
        assertEquals("object", schema.getValue("type").jsonPrimitive.content)
        val lines = schema.getValue("properties").jsonObject.getValue("lines").jsonObject
        assertEquals("array", lines.getValue("type").jsonPrimitive.content)
        assertEquals("string", lines.getValue("items").jsonObject.getValue("type").jsonPrimitive.content)
        assertTrue(request.getValue("provider").jsonObject.getValue("require_parameters").jsonPrimitive.boolean)
    }

    @Test
    fun `openrouter-only provider routing is omitted for direct providers`() {
        val request =
            buildTranslationRequest(
                text = "one",
                targetLanguage = "Spanish",
                model = "mercury-2",
                mode = "Translated",
                customSystemPrompt = "",
                baseUrl = "https://api.inceptionlabs.ai/v1/chat/completions",
            )

        assertTrue("provider" !in request)
        assertTrue(request.containsKey("response_format"))
    }

    @Test
    fun `streaming romanization uses the romanization prompt`() {
        val request =
            buildTranslationRequest(
                text = "東京",
                targetLanguage = "English",
                model = "model",
                mode = "Romanized",
                customSystemPrompt = "",
                stream = true,
            )

        assertTrue(request.getValue("stream").jsonPrimitive.boolean)
        assertTrue(
            request
                .getValue("messages")
                .jsonArray[1]
                .jsonObject
                .getValue("content")
                .jsonPrimitive
                .content
                .startsWith("Romanize"),
        )
    }
}
