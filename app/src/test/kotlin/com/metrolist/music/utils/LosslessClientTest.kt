package com.metrolist.music.utils

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import androidx.datastore.preferences.core.edit
import com.metrolist.music.constants.DownloadBackgroundAllowedKey
import kotlinx.coroutines.flow.first
import org.junit.Before
import org.junit.After
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowNetworkInfo
import org.robolectric.shadows.ShadowNetworkCapabilities
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
    private var previousBackground: Boolean? = null

    @Suppress("DEPRECATION")
    private fun connect(mobile: Boolean) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val shadow = shadowOf(manager)
        shadow.setActiveNetworkInfo(ShadowNetworkInfo.newInstance(NetworkInfo.DetailedState.CONNECTED,
            if (mobile) ConnectivityManager.TYPE_MOBILE else ConnectivityManager.TYPE_WIFI, 0, true, true))
        val caps = ShadowNetworkCapabilities.newInstance()
        shadowOf(caps).addTransportType(if (mobile) NetworkCapabilities.TRANSPORT_CELLULAR else NetworkCapabilities.TRANSPORT_WIFI)
        shadowOf(caps).addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        val network = requireNotNull(manager.activeNetwork)
        shadow.setNetworkCapabilities(network, caps)
        shadow.networkCallbacks.toList().forEach { it.onCapabilitiesChanged(network, caps) }
    }

    @Before fun allowUnmeteredProviderRequests() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        previousBackground = context.dataStore.data.first()[DownloadBackgroundAllowedKey]
        context.dataStore.edit { it[DownloadBackgroundAllowedKey] = true }
        connect(mobile = false)
    }

    @After fun restoreDownloadPreference() = runBlocking {
        ApplicationProvider.getApplicationContext<Context>().dataStore.edit {
            previousBackground?.let { value -> it[DownloadBackgroundAllowedKey] = value }
                ?: it.remove(DownloadBackgroundAllowedKey)
        }
        Unit
    }

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
        var requests = 0
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            requests++
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(202).message("Pending")
                .body("{\"status\":\"pending\"}".toResponseBody()).build()
        }.build()
        try {
            LosslessRepository(ApplicationProvider.getApplicationContext(), client, client).test(LosslessProvider.TIDAL, "https://catalog.example")
            fail("An API queue is not a verified audio connection")
        } catch (error: java.io.IOException) {
            assertTrue(error.message.orEmpty().contains("202"))
        }
        assertEquals("The provider response must actually be checked", 1, requests)
    }

    @Test fun mobileConnectionBlocksProviderAudioTestBeforeAnyHttpRequest() = runBlocking {
        connect(mobile = true)
        var requests = 0
        val client = OkHttpClient.Builder().addInterceptor {
            requests++
            error("No provider request is allowed on mobile")
        }.build()
        try {
            LosslessRepository(ApplicationProvider.getApplicationContext(), client, client)
                .test(LosslessProvider.TIDAL, "https://catalog.example")
            fail("Mobile provider downloads must be blocked")
        } catch (error: java.io.IOException) {
            assertTrue(error.message.orEmpty().contains("paused"))
        }
        assertEquals(0, requests)
    }
}
