package com.metrolist.music.utils

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Base64

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class LosslessClientTest {
    @Test fun connectionTestExercisesCatalogManifestAndActualFlacHeader() = runBlocking {
        val requests = java.util.concurrent.CopyOnWriteArrayList<Request>()
        val header = ByteArray(42)
        "fLaC".toByteArray().copyInto(header); header[4] = 0x80.toByte(); header[7] = 34
        val packed = (44100L shl 44) or (1L shl 41) or (15L shl 36) or (44100L * 180)
        for (i in 0..7) header[18+i] = (packed ushr (56-i*8)).toByte()
        val manifest = Base64.getEncoder().encodeToString("""{"mimeType":"audio/flac","encryptionType":"NONE","urls":["https://audio.example/song.flac"]}""".toByteArray())
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request(); requests += request
            val body = when (request.url.encodedPath) {
                "/search/" -> """{"data":{"items":[{"id":42,"title":"Title","artist":{"name":"Artist"},"duration":180}]}}""".toResponseBody()
                "/track/" -> JSONObject().put("data", JSONObject().put("assetPresentation", "FULL")
                    .put("manifestMimeType", "application/vnd.tidal.bts").put("manifest", manifest)).toString().toResponseBody()
                "/song.flac" -> header.toResponseBody()
                else -> error("Unexpected client request")
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(body).build()
        }.build()
        val context = ApplicationProvider.getApplicationContext<Context>()
        LosslessRepository(context, client, client).test(LosslessProvider.TIDAL, "https://catalog.example")
        assertEquals(listOf("/search/", "/track/", "/song.flac"), requests.map { it.url.encodedPath })
        assertEquals("42", requests[1].url.queryParameter("id"))
        assertEquals("LOSSLESS", requests[1].url.queryParameter("quality"))
        assertEquals("bytes=0-41", requests[2].header("Range"))
    }

    @Test fun queuedOrUnauthorizedProviderNeverPassesConnectionTest() = runBlocking {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(202).message("Pending")
                .body("{\"status\":\"pending\"}".toResponseBody()).build()
        }.build()
        try {
            LosslessRepository(ApplicationProvider.getApplicationContext(), client, client).test(LosslessProvider.TIDAL, "https://catalog.example")
            fail("An API queue is not a verified audio connection")
        } catch (_: java.io.IOException) { }
    }
}
