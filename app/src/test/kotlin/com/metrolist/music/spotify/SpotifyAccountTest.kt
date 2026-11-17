package com.metrolist.music.spotify

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class SpotifyAccountTest {
    private lateinit var prefs: SharedPreferences
    private lateinit var key: SecretKey
    private var clock = 1_000_000L
    private var calls = 0
    private var status = 200
    private var body = token("access-1", "refresh-1")
    private val requests = mutableListOf<Map<String, String>>()
    private val clientId = "a".repeat(32)

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        prefs = context.getSharedPreferences("spotify_account_test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    }

    private fun account(preferences: SharedPreferences = prefs): SpotifyAccount {
        val client = OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
            calls++
            assertEquals("https://accounts.spotify.com/api/token", chain.request().url.toString())
            assertNull(chain.request().header("Authorization"))
            val encoded = Buffer().also { chain.request().body!!.writeTo(it) }.readUtf8()
            val uri = Uri.parse("https://test/?$encoded")
            requests += uri.queryParameterNames.associateWith { uri.getQueryParameter(it)!! }
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .message("Response").code(status).body(body.toResponseBody()).build()
        }).build()
        return SpotifyAccount(preferences, { key }, client) { clock }
    }

    private fun callback(authorization: Uri): Uri = Uri.parse(SPOTIFY_REDIRECT_URI).buildUpon()
        .appendQueryParameter("state", authorization.getQueryParameter("state"))
        .appendQueryParameter("code", "private-authorization-code").build()

    @Test
    fun pkceSurvivesRestartAndSecretsStayEncrypted() = runBlocking {
        val authorize = account().beginAuthorization(clientId)
        assertEquals("S256", authorize.getQueryParameter("code_challenge_method"))
        assertEquals("playlist-read-private playlist-read-collaborative", authorize.getQueryParameter("scope"))
        assertEquals(SPOTIFY_REDIRECT_URI, authorize.getQueryParameter("redirect_uri"))
        assertFalse(prefs.all.toString().contains(authorize.getQueryParameter("state")!!))
        val restarted = account()
        restarted.completeAuthorization(callback(authorize))
        assertTrue(restarted.connected.value)
        assertEquals("access-1", account().accessToken())
        val request = requests.single()
        assertEquals(43, request["code_verifier"]!!.length)
        val digest = MessageDigest.getInstance("SHA-256").digest(request["code_verifier"]!!.toByteArray(Charsets.US_ASCII))
        val challenge = Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        assertEquals(authorize.getQueryParameter("code_challenge"), challenge)
        assertEquals("private-authorization-code", request["code"])
        assertFalse(request.containsKey("client_secret"))
        assertFalse(prefs.all.toString().contains("access-1"))
        assertFalse(prefs.all.toString().contains("refresh-1"))
        assertFalse(prefs.all.toString().contains(request["code_verifier"]!!))
        assertFalse(prefs.contains("pending"))
        expect(SpotifyImportFailure.AUTH_REQUIRED) { restarted.completeAuthorization(callback(authorize)) }
        assertEquals(1, calls)
    }

    @Test
    fun callbackRejectsWrongStateDuplicatesAndUnexpectedRedirectParts() = runBlocking {
        val account = account()
        val authorize = account.beginAuthorization(clientId)
        val valid = callback(authorize)
        val malformed = listOf(
            valid.toString().replace("//oauth/", "//other/"),
            valid.toString().replace("//oauth/", "//user@oauth/"),
            valid.toString().replace("//oauth/", "//oauth:443/"),
            valid.toString().replace("/callback?", "/callback/extra?"),
            valid.toString() + "#fragment",
            valid.toString() + "&code=duplicate",
            valid.toString() + "&%63ode=encoded-duplicate",
            valid.toString() + "&st%61te=encoded-duplicate",
            valid.toString() + "&%zz=invalid-escape",
            valid.toString() + "&error=access_denied",
            valid.toString().replace(authorize.getQueryParameter("state")!!, "wrong-state"),
        )
        malformed.forEach { expect(SpotifyImportFailure.INVALID) { account.completeAuthorization(Uri.parse(it)) } }
        assertEquals(0, calls)
        account.completeAuthorization(valid)
        assertTrue(account.connected.value)
    }

    @Test
    fun pendingAuthorizationExpiresAndDenialConsumesState() = runBlocking {
        val account = account()
        val first = account.beginAuthorization(clientId)
        clock += 600_001
        expect(SpotifyImportFailure.AUTH_REQUIRED) { account.completeAuthorization(callback(first)) }
        val second = account.beginAuthorization(clientId)
        val denied = Uri.parse(SPOTIFY_REDIRECT_URI).buildUpon()
            .appendQueryParameter("state", second.getQueryParameter("state"))
            .appendQueryParameter("error", "access_denied").build()
        expect(SpotifyImportFailure.ACCESS_DENIED) { account.completeAuthorization(denied) }
        expect(SpotifyImportFailure.AUTH_REQUIRED) { account.completeAuthorization(callback(second)) }
        assertEquals(0, calls)
    }

    @Test
    fun refreshRotatesTokensAndRevocationDisconnects() = runBlocking {
        val account = account()
        account.completeAuthorization(callback(account.beginAuthorization(clientId)))
        body = token("access-2", "refresh-2")
        assertEquals("access-2", account.accessToken(forceRefresh = true))
        assertEquals("refresh-1", requests.last()["refresh_token"])
        clock += 3_600_000
        body = token("access-3", null)
        assertEquals("access-3", account().accessToken())
        assertEquals("refresh-2", requests.last()["refresh_token"])
        status = 400
        body = """{"error":"invalid_grant","error_description":"private server detail"}"""
        expect(SpotifyImportFailure.AUTH_REQUIRED) { account.accessToken(forceRefresh = true) }
        assertFalse(account.connected.value)
        assertFalse(prefs.contains("session"))
    }

    @Test
    fun concurrentForcedRefreshesShareOneExchangeEvenWithUnchangedAccessToken() = runBlocking {
        val synchronizeReads = AtomicBoolean(false)
        val reads = AtomicInteger()
        val barrier = CountDownLatch(2)
        val preferences = object : SharedPreferences by prefs {
            override fun getString(key: String?, defValue: String?): String? {
                val value = prefs.getString(key, defValue)
                if (key == "session" && synchronizeReads.get() && reads.incrementAndGet() <= 2) {
                    barrier.countDown()
                    assertTrue(barrier.await(5, TimeUnit.SECONDS))
                }
                return value
            }
        }
        val account = account(preferences)
        account.completeAuthorization(callback(account.beginAuthorization(clientId)))
        body = token("access-1", "refresh-2")
        synchronizeReads.set(true)
        val first = async(Dispatchers.Default) { account.accessToken(forceRefresh = true) }
        val second = async(Dispatchers.Default) { account.accessToken(forceRefresh = true) }
        assertEquals("access-1", first.await())
        assertEquals("access-1", second.await())
        assertEquals(2, calls)
        assertEquals("refresh-1", requests.last()["refresh_token"])
    }

    @Test
    fun invalidScopeAndOversizedResponsesNeverStoreTokens() = runBlocking {
        val account = account()
        body = token("access-1", "refresh-1").replace("playlist-read-collaborative", "")
        expect(SpotifyImportFailure.ACCESS_DENIED) {
            account.completeAuthorization(callback(account.beginAuthorization(clientId)))
        }
        body = " ".repeat(65_537)
        expect(SpotifyImportFailure.UNAVAILABLE) {
            account.completeAuthorization(callback(account.beginAuthorization(clientId)))
        }
        assertFalse(account.connected.value)
        assertFalse(prefs.contains("session"))
    }

    @Test
    fun unavailableKeystoreFailsClosedAndDisconnectClearsPendingAndSession() = runBlocking {
        val account = SpotifyAccount(prefs, { error("private keystore failure") })
        expect(SpotifyImportFailure.SETUP) { account.beginAuthorization(clientId) }
        assertFalse(prefs.contains("pending"))
        val working = account()
        working.completeAuthorization(callback(working.beginAuthorization(clientId)))
        working.disconnect()
        assertFalse(working.connected.value)
        assertFalse(prefs.contains("session"))
        assertFalse(prefs.contains("pending"))
        expect(SpotifyImportFailure.AUTH_REQUIRED) { working.accessToken() }
    }

    private suspend fun expect(reason: SpotifyImportFailure, block: suspend () -> Unit) {
        try {
            block()
            fail("Expected $reason")
        } catch (error: SpotifyImportException) {
            assertEquals(reason, error.reason)
            assertEquals(reason.name, error.message)
        }
    }

    private fun token(access: String, refresh: String?): String =
        """{"access_token":"$access",${refresh?.let { "\"refresh_token\":\"$it\"," }.orEmpty()}"token_type":"Bearer","expires_in":3600,"scope":"playlist-read-private playlist-read-collaborative"}"""
}
