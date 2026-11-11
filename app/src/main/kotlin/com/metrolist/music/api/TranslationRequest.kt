package com.metrolist.music.api

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.IOException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

// Cancel the socket as well as the coroutine, including while a streaming read blocks.
internal suspend fun <T> withTranslationResponse(
    client: OkHttpClient,
    request: Request,
    block: suspend (Response) -> T,
): T = coroutineScope {
    val call = client.newCall(request)
    val cancellation = launch(start = CoroutineStart.UNDISPATCHED) {
        try {
            awaitCancellation()
        } finally {
            call.cancel()
        }
    }
    try {
        withContext(Dispatchers.IO) { call.execute().use { block(it) } }
    } catch (error: IOException) {
        currentCoroutineContext().ensureActive()
        throw error
    } finally {
        cancellation.cancel()
    }
}
