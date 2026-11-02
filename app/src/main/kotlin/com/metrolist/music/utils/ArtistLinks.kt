package com.metrolist.music.utils

import android.net.Uri
import android.content.Intent

/** Keep the channel ID when YouTube appends a tab, query parameters or a slash. */
internal fun artistRouteFromLink(uri: Uri): String? {
    if (uri.scheme?.lowercase() !in setOf("https", "http") ||
        uri.host?.lowercase() !in setOf("music.youtube.com", "youtube.com", "www.youtube.com", "m.youtube.com")) return null
    val segments = uri.pathSegments
    val id = segments.getOrNull(1)?.takeIf { it.matches(Regex("[A-Za-z0-9_-]+")) } ?: return null
    if (segments.firstOrNull() !in setOf("channel", "c") &&
        !(segments.firstOrNull() == "browse" && id.startsWith("UC"))) return null
    return "artist/${Uri.encode(id)}"
}

internal fun externalLinkUri(intent: Intent): Uri? {
    val uri = intent.data ?: intent.getStringExtra(Intent.EXTRA_TEXT)?.let { text ->
        Regex("https?://[^\\s<>\"]+").find(text)?.value?.let(Uri::parse)
    }
    return uri?.takeIf { it.isHierarchical }
}
