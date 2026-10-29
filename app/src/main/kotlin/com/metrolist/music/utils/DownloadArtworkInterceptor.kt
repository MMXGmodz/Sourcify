package com.metrolist.music.utils

import coil3.intercept.Interceptor
import coil3.request.ErrorResult
import coil3.request.ImageResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class DownloadArtworkInterceptor(private val store: DownloadArtworkStore) : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val url = chain.request.data.toString()
        val file = withContext(Dispatchers.IO) { store.fileFor(url) } ?: return chain.proceed()
        val result = chain.withRequest(chain.request.newBuilder().data(file).build()).proceed()
        if (result !is ErrorResult) return result
        withContext(Dispatchers.IO) { store.invalidate(url) }
        return chain.proceed()
    }
}
