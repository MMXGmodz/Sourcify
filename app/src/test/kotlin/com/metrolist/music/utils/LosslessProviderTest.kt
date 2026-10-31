package com.metrolist.music.utils

import android.app.Application
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.json.JSONObject
import java.util.Base64
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlinx.coroutines.runBlocking

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class LosslessProviderTest {
    @Test fun missingCatalogDurationCannotDisablePreviewDetection() {
        val response = JSONObject("""{"data":[{"id":42,"title":"Title","duration":0,"artist":{"name":"Artist"}}]}""")
        assertTrue(parseLosslessCatalog(LosslessProvider.DEEZER, response).isEmpty())
    }

    @Test fun documentedHifiSearchResponseUsesDataItems() {
        val body = JSONObject("""{"version":"2.6","data":{"limit":25,"items":[{"id":42,"title":"Title","duration":180,"artist":{"name":"Artist"}}]}}""")
        assertEquals("42", parseLosslessCatalog(LosslessProvider.TIDAL, body).single().id)
    }

    @Test fun decodedSampleCountMustReachTheDeclaredEndOfTheTrack() {
        val counter = FlacSampleCounter(44100L * 180)
        counter.add(44100L * 90)
        try { counter.finish(); fail("Truncated audio must not be committed") } catch (_: IOException) { }
        counter.add(44100L * 90)
        counter.finish()
        try { counter.add(1); fail("Unexpected extra samples") } catch (_: IOException) { }
    }
    @Test fun tidalManifestAcceptsOnlyFullUnencryptedProgressiveFlac() {
        val manifest = JSONObject().put("mimeType", "audio/flac").put("encryptionType", "NONE")
            .put("urls", org.json.JSONArray(listOf("https://audio.example/song.flac")))
        fun response() = JSONObject().put("data", JSONObject().put("assetPresentation", "FULL")
            .put("manifestMimeType", "application/vnd.tidal.bts")
            .put("manifest", Base64.getEncoder().encodeToString(manifest.toString().toByteArray())))
        assertEquals("https://audio.example/song.flac", parseLosslessStream(LosslessProvider.TIDAL, response()).url)
        manifest.put("encryptionType", "OLD_AES")
        try { parseLosslessStream(LosslessProvider.TIDAL, response()); fail("Encrypted audio must not be downloaded as FLAC") } catch (_: IOException) { }
        manifest.put("encryptionType", "NONE").put("mimeType", "audio/mp4")
        try { parseLosslessStream(LosslessProvider.TIDAL, response()); fail("AAC must not be labeled FLAC") } catch (_: IOException) { }
    }

    @Test fun nativeCatalogShapesPreserveProviderIdentityAndDoNotInventAudioQuality() {
        val track = JSONObject("""{"id":42,"title":"Title","duration":180,"artist":{"name":"Artist"},"album":{"title":"Album","image":{"large":"https://img.example/a.jpg"}}}""")
        for ((provider, body) in listOf(
            LosslessProvider.TIDAL to JSONObject().put("data", JSONObject().put("tracks", JSONObject().put("items", org.json.JSONArray().put(track)))),
            LosslessProvider.QOBUZ to JSONObject().put("data", JSONObject().put("tracks", JSONObject().put("items", org.json.JSONArray().put(track)))),
            LosslessProvider.DEEZER to JSONObject().put("data", org.json.JSONArray().put(track)),
        )) {
            val result = parseLosslessCatalog(provider, body).single()
            assertEquals(provider, result.provider); assertEquals("42", result.id); assertEquals("Artist", result.artist)
        }
    }

    @Test fun unsafeEndpointsAndPreviewResolversAreRejected() {
        for (url in listOf("http://example.com", "https://user:password@example.com", "file:///tmp/a", "https://example.com/?token=secret")) {
            try { losslessEndpoint(url); fail(url) } catch (_: IllegalArgumentException) { }
        }
        for (body in listOf("""{"url":"https://audio.example/preview.flac","duration":30,"format":"FLAC","is_preview":true}""",
            """{"download_url":"https://audio.example/encrypted","requires_client_decryption":true}""")) {
            try { parseLosslessStream(LosslessProvider.DEEZER, JSONObject(body)); fail("Unsupported response accepted") } catch (_: IOException) { }
        }
    }

    @Test fun flacTransferRejectsTruncationAndPreviewDuration() = runBlocking {
        // STREAMINFO: 44.1kHz, stereo, 16-bit, 180 seconds; metadata alone is not playable audio.
        val bytes = ByteArray(64)
        "fLaC".toByteArray().copyInto(bytes); bytes[4] = 0x80.toByte(); bytes[7] = 34
        val packed = (44100L shl 44) or (1L shl 41) or (15L shl 36) or (44100L * 180)
        for (i in 0..7) bytes[18+i] = (packed ushr (56-i*8)).toByte()
        assertEquals(44100, inspectFlacHeader(bytes, 180).sampleRate)
        val output = ByteArrayOutputStream()
        copyLosslessOriginal(ByteArrayInputStream(bytes), output, bytes.size.toLong(), 180)
        assertArrayEquals(bytes, output.toByteArray())
        try { copyLosslessOriginal(ByteArrayInputStream(bytes), ByteArrayOutputStream(), 100, 180); fail("Truncation") } catch (_: IOException) { }
        try { inspectFlacHeader(bytes, 240); fail("Preview length") } catch (_: IOException) { }
    }
}
