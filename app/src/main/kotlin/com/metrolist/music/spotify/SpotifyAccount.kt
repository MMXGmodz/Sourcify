package com.metrolist.music.spotify

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.net.URLDecoder
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal class SpotifyAccount internal constructor(
    private val prefs: SharedPreferences,
    private val key: () -> SecretKey = ::spotifyKey,
    client: OkHttpClient = OkHttpClient(),
    private val now: () -> Long = System::currentTimeMillis,
) : SpotifyTokens {
    private val http = client.newBuilder()
        .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
        .callTimeout(30, TimeUnit.SECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    private val mutex = Mutex()
    private val mutableConnected = MutableStateFlow(runCatching { read("session") != null }.getOrDefault(false))
    val connected: StateFlow<Boolean> = mutableConnected.asStateFlow()
    val clientId: String get() = prefs.getString("client_id", "").orEmpty()

    suspend fun beginAuthorization(clientId: String): Uri = withContext(Dispatchers.IO) {
        mutex.withLock {
            val id = clientId.trim()
            if (!id.matches(Regex("[a-fA-F0-9]{32}"))) fail(SpotifyImportFailure.SETUP)
            val verifier = randomValue()
            val state = randomValue()
            val pending = JSONObject().put("client_id", id).put("verifier", verifier)
                .put("state", state).put("created_at", now())
            val encrypted = encrypt(pending.toString())
            val edit = prefs.edit().putString("client_id", id).putString("pending", encrypted).remove("session")
            if (!edit.commit()) fail(SpotifyImportFailure.SETUP)
            mutableConnected.value = false
            Uri.parse("https://accounts.spotify.com/authorize").buildUpon()
                .appendQueryParameter("client_id", id)
                .appendQueryParameter("response_type", "code")
                .appendQueryParameter("redirect_uri", SPOTIFY_REDIRECT_URI)
                .appendQueryParameter("scope", SCOPES)
                .appendQueryParameter("state", state)
                .appendQueryParameter("code_challenge_method", "S256")
                .appendQueryParameter("code_challenge", encode(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))))
                .build()
        }
    }

    internal suspend fun completeAuthorization(uri: Uri) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val parameters = validateCallback(uri)
            val pending = read("pending") ?: fail(SpotifyImportFailure.AUTH_REQUIRED)
            val state = parameters["state"] ?: fail(SpotifyImportFailure.INVALID)
            val expected = pending.getString("state")
            if (!MessageDigest.isEqual(expected.toByteArray(Charsets.UTF_8), state.toByteArray(Charsets.UTF_8))) {
                fail(SpotifyImportFailure.INVALID)
            }
            val age = now() - pending.getLong("created_at")
            if (age !in 0..PENDING_TTL) {
                remove("pending")
                fail(SpotifyImportFailure.AUTH_REQUIRED)
            }
            // Consume before exchanging: a callback can authorize exactly one token request.
            remove("pending")
            if (parameters["error"] != null) fail(SpotifyImportFailure.ACCESS_DENIED)
            val code = parameters["code"]?.takeIf { it.isNotBlank() && it.length <= 4096 }
                ?: fail(SpotifyImportFailure.INVALID)
            val id = pending.getString("client_id")
            val result = tokenRequest(FormBody.Builder()
                .add("grant_type", "authorization_code").add("code", code)
                .add("redirect_uri", SPOTIFY_REDIRECT_URI).add("client_id", id)
                .add("code_verifier", pending.getString("verifier")).build())
            saveSession(result, id, null)
        }
    }

    override suspend fun accessToken(forceRefresh: Boolean): String = withContext(Dispatchers.IO) {
        // Capture before locking so concurrent 401s share one refresh, including rotated refresh tokens.
        val observed = prefs.getString("session", null)
        mutex.withLock {
            val session = readSession() ?: run {
                mutableConnected.value = false
                fail(SpotifyImportFailure.AUTH_REQUIRED)
            }
            val access = session.getString("access_token")
            if (session.getLong("expires_at") > now() + EXPIRY_CUSHION && (!forceRefresh || prefs.getString("session", null) != observed)) {
                return@withLock access
            }
            val result = try {
                tokenRequest(FormBody.Builder().add("grant_type", "refresh_token")
                    .add("refresh_token", session.getString("refresh_token"))
                    .add("client_id", session.getString("client_id")).build())
            } catch (error: SpotifyImportException) {
                if (error.reason == SpotifyImportFailure.AUTH_REQUIRED) {
                    remove("session")
                    mutableConnected.value = false
                }
                throw error
            }
            saveSession(result, session.getString("client_id"), session.getString("refresh_token"))
        }
    }

    suspend fun disconnect() = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!prefs.edit().remove("session").remove("pending").commit()) fail(SpotifyImportFailure.SETUP)
            mutableConnected.value = false
        }
    }

    private fun saveSession(result: JSONObject, id: String, previousRefresh: String?): String {
        try {
            val access = result.getString("access_token").takeIf { it.isNotBlank() && it.length <= 16384 }
                ?: fail(SpotifyImportFailure.UNAVAILABLE)
            val refresh = result.optString("refresh_token").ifBlank { previousRefresh.orEmpty() }
                .takeIf { it.isNotBlank() && it.length <= 16384 } ?: fail(SpotifyImportFailure.UNAVAILABLE)
            if (!result.getString("token_type").equals("Bearer", true)) fail(SpotifyImportFailure.UNAVAILABLE)
            val expires = result.getLong("expires_in")
            if (expires !in 1..86400) fail(SpotifyImportFailure.UNAVAILABLE)
            if (result.has("scope") || previousRefresh == null) {
                val granted = result.optString("scope").split(' ').toSet()
                if (!granted.containsAll(SCOPES.split(' '))) fail(SpotifyImportFailure.ACCESS_DENIED)
            }
            val session = JSONObject().put("access_token", access).put("refresh_token", refresh)
                .put("expires_at", now() + expires * 1000).put("client_id", id)
            if (!prefs.edit().putString("session", encrypt(session.toString(), "session")).commit()) fail(SpotifyImportFailure.SETUP)
            mutableConnected.value = true
            return access
        } catch (error: SpotifyImportException) {
            throw error
        } catch (_: Exception) {
            fail(SpotifyImportFailure.UNAVAILABLE)
        }
    }

    private suspend fun tokenRequest(body: FormBody): JSONObject = suspendCancellableCoroutine { continuation ->
        val call = http.newCall(Request.Builder().url("https://accounts.spotify.com/api/token").post(body).build())
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWith(Result.failure(SpotifyImportException(SpotifyImportFailure.UNAVAILABLE)))
            }

            override fun onResponse(call: Call, response: Response) {
                val result = runCatching {
                    response.use {
                        val source = it.body?.source() ?: fail(SpotifyImportFailure.UNAVAILABLE)
                        if (source.request(MAX_RESPONSE + 1)) fail(SpotifyImportFailure.UNAVAILABLE)
                        val json = try { JSONObject(source.readUtf8()) } catch (_: Exception) { fail(SpotifyImportFailure.UNAVAILABLE) }
                        if (!it.isSuccessful) {
                            fail(when {
                                it.code == 429 -> SpotifyImportFailure.RATE_LIMIT
                                it.code == 400 && json.optString("error") == "invalid_grant" -> SpotifyImportFailure.AUTH_REQUIRED
                                it.code == 400 && json.optString("error") == "invalid_client" -> SpotifyImportFailure.SETUP
                                it.code == 401 -> SpotifyImportFailure.SETUP
                                it.code == 403 -> SpotifyImportFailure.ACCESS_DENIED
                                else -> SpotifyImportFailure.UNAVAILABLE
                            })
                        }
                        json
                    }
                }.recoverCatching { error ->
                    if (error is SpotifyImportException || error is CancellationException) throw error
                    fail(SpotifyImportFailure.UNAVAILABLE)
                }
                if (continuation.isActive) continuation.resumeWith(result)
            }
        })
    }

    private fun readSession(): JSONObject? = try {
        read("session")
    } catch (error: SpotifyImportException) {
        mutableConnected.value = false
        throw error
    }

    private fun read(name: String): JSONObject? {
        val value = prefs.getString(name, null) ?: return null
        return try {
            val bytes = Base64.decode(value, Base64.NO_WRAP)
            if (bytes.size < 28) fail(SpotifyImportFailure.AUTH_REQUIRED)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            cipher.updateAAD(name.toByteArray(Charsets.UTF_8))
            JSONObject(String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8))
        } catch (_: Exception) {
            fail(SpotifyImportFailure.AUTH_REQUIRED)
        }
    }

    private fun encrypt(value: String, name: String = "pending"): String = try {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        cipher.updateAAD(name.toByteArray(Charsets.UTF_8))
        Base64.encodeToString(cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    } catch (_: Exception) {
        fail(SpotifyImportFailure.SETUP)
    }

    private fun remove(name: String) {
        if (!prefs.edit().remove(name).commit()) fail(SpotifyImportFailure.SETUP)
    }

    companion object {
        private const val SCOPES = "playlist-read-private playlist-read-collaborative"
        private const val PENDING_TTL = 10 * 60 * 1000L
        private const val EXPIRY_CUSHION = 60 * 1000L
        private const val MAX_RESPONSE = 64 * 1024L
        @Volatile private var instance: SpotifyAccount? = null

        fun get(context: Context): SpotifyAccount = instance ?: synchronized(this) {
            instance ?: SpotifyAccount(context.applicationContext.getSharedPreferences("spotify_account", Context.MODE_PRIVATE)).also { instance = it }
        }

        internal fun validateCallback(uri: Uri): Map<String, String> {
            try {
                if (uri.toString().length > 16384 || uri.scheme != "sourcifyspotify" || uri.encodedAuthority != "oauth" ||
                    uri.encodedPath != "/callback" || uri.fragment != null || uri.port != -1 || uri.userInfo != null) {
                    fail(SpotifyImportFailure.INVALID)
                }
                val parameters = mutableMapOf<String, String>()
                uri.encodedQuery?.split('&')?.forEach { pair ->
                    val parts = pair.split('=', limit = 2)
                    val name = URLDecoder.decode(parts[0], "UTF-8")
                    val value = URLDecoder.decode(parts.getOrElse(1) { "" }, "UTF-8")
                    if (parameters.put(name, value) != null) fail(SpotifyImportFailure.INVALID)
                }
                if (parameters.containsKey("code") && parameters.containsKey("error")) fail(SpotifyImportFailure.INVALID)
                return parameters
            } catch (error: SpotifyImportException) {
                throw error
            } catch (_: Exception) {
                fail(SpotifyImportFailure.INVALID)
            }
        }

        private fun randomValue(): String = encode(ByteArray(32).also { SecureRandom().nextBytes(it) })
        private fun encode(value: ByteArray): String = Base64.encodeToString(value, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        private fun fail(reason: SpotifyImportFailure): Nothing = throw SpotifyImportException(reason)
    }
}

@Synchronized
private fun spotifyKey(): SecretKey {
    val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    val alias = "sourcify_spotify_account_v1"
    (store.getKey(alias, null) as? SecretKey)?.let { return it }
    return KeyGenerator.getInstance("AES", "AndroidKeyStore").apply {
        init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256).setRandomizedEncryptionRequired(true).build())
    }.generateKey()
}
