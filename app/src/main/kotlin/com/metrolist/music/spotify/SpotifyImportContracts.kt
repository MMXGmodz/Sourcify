package com.metrolist.music.spotify

import java.io.IOException

internal const val SPOTIFY_REDIRECT_URI = "sourcifyspotify://oauth/callback"

internal enum class SpotifyImportFailure { SETUP, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, RATE_LIMIT, UNAVAILABLE, INVALID, LIMIT }
internal class SpotifyImportException(val reason: SpotifyImportFailure) : IOException(reason.name)

internal interface SpotifyTokens {
    suspend fun accessToken(forceRefresh: Boolean = false): String
}
