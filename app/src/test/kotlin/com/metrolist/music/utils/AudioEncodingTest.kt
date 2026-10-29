package com.metrolist.music.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class AudioEncodingTest {
    @Test fun youtubeContainersKeepTheirActualLossyCodec() {
        assertEquals(AudioEncoding.LOSSY, audioEncoding("audio/webm", "opus"))
        assertEquals(AudioEncoding.LOSSY, audioEncoding("audio/mp4", "mp4a.40.2"))
        assertEquals(AudioEncoding.LOSSY, audioEncoding("audio/aac"))
    }

    @Test fun onlyRecognizedAudioTracksAreMarkedLossless() {
        assertEquals(AudioEncoding.LOSSLESS, audioEncoding("audio/flac"))
        assertEquals(AudioEncoding.LOSSLESS, audioEncoding("audio/raw"))
        assertEquals(AudioEncoding.LOSSLESS, audioEncoding("audio/mp4", "alac"))
        assertEquals(AudioEncoding.UNKNOWN, audioEncoding("audio/wav"))
        assertEquals(AudioEncoding.UNKNOWN, audioEncoding(null))
        assertEquals(AudioEncoding.UNKNOWN, audioEncoding("audio/mp4"))
    }
}
