package com.metrolist.music.utils

internal enum class AudioEncoding { LOSSLESS, LOSSY, UNKNOWN }

// Classify the encoded audio track, not a filename or the container extension.
internal fun audioEncoding(mimeType: String?, codecs: String? = null): AudioEncoding {
    val mime = mimeType.orEmpty().substringBefore(';').trim().lowercase(java.util.Locale.ROOT)
    val codec = codecs.orEmpty().trim().lowercase(java.util.Locale.ROOT)
    return when {
        mime in setOf("audio/flac", "audio/x-flac", "audio/raw", "audio/alac") ||
            codec in setOf("flac", "alac", "pcm") -> AudioEncoding.LOSSLESS
        mime in setOf("audio/aac", "audio/mp4a-latm", "audio/mpeg", "audio/opus", "audio/vorbis") ||
            codec.split(',').map { it.trim() }.any {
                it.startsWith("mp4a") || it in setOf("aac", "opus", "vorbis", "mp3")
            } -> AudioEncoding.LOSSY
        else -> AudioEncoding.UNKNOWN
    }
}
