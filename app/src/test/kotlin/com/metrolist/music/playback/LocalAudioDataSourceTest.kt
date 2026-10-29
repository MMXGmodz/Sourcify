package com.metrolist.music.playback

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.DataReader
import androidx.media3.common.Format
import androidx.media3.datasource.*
import androidx.media3.extractor.*
import androidx.media3.common.util.ParsableByteArray
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class LocalAudioDataSourceTest {
    @get:Rule val temporary = TemporaryFolder()
    private val id = "local:9fd1d6e2-dd38-4919-8c1b-ac9474d89dca"

    @Test fun localOriginalAndSeekReadNeverTouchTheNetworkFactory() {
        val bytes = fixture("wav")
        val file = temporary.newFile().apply { writeBytes(bytes) }
        val factory = LocalAudioDataSourceFactory({ error("Local audio touched the network/cache resolver") }) { file }
        DataSourceInputStream(factory.createDataSource(), spec()).use { assertArrayEquals(bytes, it.readBytes()) }
        DataSourceInputStream(factory.createDataSource(), spec(100, 43)).use {
            assertArrayEquals(bytes.copyOfRange(100, 143), it.readBytes())
        }
    }

    @Test fun missingLocalFileFailsLocallyAndRemoteAudioStillUsesItsResolver() {
        var remoteOpens = 0
        val factory = LocalAudioDataSourceFactory({ remoteOpens++; ByteArrayDataSource(byteArrayOf(7, 8, 9)) }) { null }
        val source = factory.createDataSource()
        try { source.open(spec()); fail("Missing file must fail") } catch (_: DataSourceException) { } finally { source.close() }
        assertEquals(0, remoteOpens)
        DataSourceInputStream(factory.createDataSource(), DataSpec.Builder().setUri("test-remote").setKey("test-remote").build()).use {
            assertArrayEquals(byteArrayOf(7, 8, 9), it.readBytes())
        }
        assertEquals(1, remoteOpens)
    }

    @Test fun actualFlacAndPcmWavProduceAudioSamplesThroughTheSharedExtractorSet() {
        mapOf("flac" to "audio/flac", "wav" to "audio/raw").forEach { (extension, mime) ->
            val file = temporary.newFile("audio.$extension").apply { writeBytes(fixture(extension)) }
            val factory = LocalAudioDataSourceFactory({ error("Lossless playback used a network source") }) { file }
            var source = factory.createDataSource()
            var input = DefaultExtractorInput(source, 0, source.open(spec()))
            val extractor = DefaultExtractorsFactory().createExtractors().first { candidate ->
                try { candidate.sniff(input) } finally { input.resetPeekPosition() }
            }
            var actualFormat: Format? = null
            var samples = 0
            extractor.init(object : ExtractorOutput {
                override fun endTracks() { }
                override fun seekMap(seekMap: SeekMap) { }
                override fun track(id: Int, type: Int): TrackOutput = object : TrackOutput {
                    override fun format(format: Format) { actualFormat = format }
                    override fun sampleData(input: DataReader, length: Int, allowEndOfInput: Boolean, sampleDataPart: Int): Int =
                        input.read(ByteArray(length.coerceAtMost(65536)), 0, length.coerceAtMost(65536))
                    override fun sampleData(data: ParsableByteArray, length: Int, sampleDataPart: Int) { data.skipBytes(length) }
                    override fun sampleMetadata(timeUs: Long, flags: Int, size: Int, offset: Int, cryptoData: TrackOutput.CryptoData?) { samples++ }
                }
            })
            try {
                val position = PositionHolder()
                var ended = false
                repeat(1000) {
                    if (!ended) when (extractor.read(input, position)) {
                        Extractor.RESULT_END_OF_INPUT -> ended = true
                        Extractor.RESULT_SEEK -> {
                            source.close()
                            source = factory.createDataSource()
                            val length = source.open(spec(position.position))
                            input = DefaultExtractorInput(source, position.position, if (length == C.LENGTH_UNSET.toLong()) length else length + position.position)
                        }
                    }
                }
                assertTrue("$extension extractor did not reach EOF", ended)
                assertEquals(mime, actualFormat?.sampleMimeType)
                assertEquals(44100, actualFormat?.sampleRate)
                assertTrue("$extension had no decoded audio packets", samples > 0)
            } finally { extractor.release(); source.close() }
        }
    }

    private fun fixture(extension: String) = checkNotNull(javaClass.getResourceAsStream("/audio/lossless.$extension")).use { it.readBytes() }
    private fun spec(position: Long = 0, length: Long = C.LENGTH_UNSET.toLong()) = DataSpec.Builder()
        .setUri(Uri.parse(id)).setKey(id).setPosition(position).setLength(length).build()
}
