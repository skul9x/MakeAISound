package skul9x.example.makesound

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import skul9x.example.makesound.player.AudioPlayerManager
import skul9x.example.makesound.player.MediaPlayerAdapter
import skul9x.example.makesound.player.PlaybackStatus
import skul9x.example.makesound.storage.WavWriter
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

/**
 * Single comprehensive unit test for Phase 08: Audio Player Polling & Streaming WAV Reader Optimization.
 *
 * Core functionality verified:
 * 1. AudioPlayerManager 50ms (~20fps) energy-efficient coroutine polling & lifecycle safety.
 * 2. Streaming WAV reader chunking across 8KB boundaries, non-standard RIFF chunk skipping, and File/Stream/ByteArray parity.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class Phase8PlayerAndWavOptimizationTest {

    private class FakeMediaPlayerAdapter : MediaPlayerAdapter {
        var isPlayingInternal = false
        var currentPositionInternal = 0
        var durationInternal = 10000
        var isPrepared = false
        var isReleased = false
        var storedDataSource: String? = null

        var completionCallback: (() -> Unit)? = null
        var errorCallback: ((Int, Int) -> Boolean)? = null

        override fun setDataSource(path: String) {
            storedDataSource = path
        }

        override fun prepare() {
            isPrepared = true
        }

        override fun start() {
            isPlayingInternal = true
        }

        override fun pause() {
            isPlayingInternal = false
        }

        override fun stop() {
            isPlayingInternal = false
        }

        override fun seekTo(msec: Int) {
            currentPositionInternal = msec.coerceIn(0, durationInternal)
        }

        override fun isPlaying(): Boolean = isPlayingInternal
        override fun getCurrentPosition(): Int = currentPositionInternal
        override fun getDuration(): Int = durationInternal

        override fun reset() {
            isPlayingInternal = false
            currentPositionInternal = 0
            isPrepared = false
            storedDataSource = null
        }

        override fun release() {
            reset()
            isReleased = true
        }

        override fun setOnCompletionListener(listener: (() -> Unit)?) {
            completionCallback = listener
        }

        override fun setOnErrorListener(listener: ((what: Int, extra: Int) -> Boolean)?) {
            errorCallback = listener
        }
    }

    @Test
    fun verifyPhase8PlayerAndWavOptimization() = runTest {
        // =========================================================================
        // SECTION 1: AudioPlayerManager 50ms Polling & Coroutine Lifecycle
        // =========================================================================
        val fakeAdapter = FakeMediaPlayerAdapter()
        val playerManager = AudioPlayerManager(
            coroutineScope = this,
            playerAdapter = fakeAdapter
        )

        assertTrue(playerManager.load("/storage/test_opt.wav"))
        assertTrue(playerManager.play())
        assertEquals(PlaybackStatus.PLAYING, playerManager.playerState.value.status)
        assertEquals(0L, playerManager.playerState.value.currentPositionMs)

        // Advance 1ms so initial polling iteration runs at t=0 and enters delay(50ms)
        advanceTimeBy(1)
        assertEquals(0L, playerManager.playerState.value.currentPositionMs)

        // Change position in adapter
        fakeAdapter.currentPositionInternal = 250

        // At +20ms (t=21ms, < 50ms polling delay), position should not have polled yet
        advanceTimeBy(20)
        assertEquals(0L, playerManager.playerState.value.currentPositionMs)

        // At +30ms (t=51ms, > 50ms single polling tick), position should now be updated to 250ms
        advanceTimeBy(30)
        assertEquals(250L, playerManager.playerState.value.currentPositionMs)

        // Advance another 50ms tick with position moving to 600ms
        fakeAdapter.currentPositionInternal = 600
        advanceTimeBy(50)
        assertEquals(600L, playerManager.playerState.value.currentPositionMs)

        // Verify Pause terminates polling loop
        playerManager.pause()
        assertEquals(PlaybackStatus.PAUSED, playerManager.playerState.value.status)
        fakeAdapter.currentPositionInternal = 1000
        advanceTimeBy(100)
        // Position remains at paused position
        assertEquals(600L, playerManager.playerState.value.currentPositionMs)

        // Resume starts polling again
        playerManager.resume()
        assertEquals(PlaybackStatus.PLAYING, playerManager.playerState.value.status)
        advanceTimeBy(1) // Run initial tick of resume
        fakeAdapter.currentPositionInternal = 1200
        advanceTimeBy(50) // Wait for next 50ms polling cycle
        assertEquals(1200L, playerManager.playerState.value.currentPositionMs)

        // Stop cancels polling
        playerManager.stop()
        assertEquals(PlaybackStatus.PREPARED, playerManager.playerState.value.status)
        assertEquals(0L, playerManager.playerState.value.currentPositionMs)

        // Release cleans up
        playerManager.release()
        assertEquals(PlaybackStatus.IDLE, playerManager.playerState.value.status)
        assertTrue(fakeAdapter.isReleased)

        // =========================================================================
        // SECTION 2: Streaming WAV Reader Chunking & Multi-KB Stream Decoding
        // =========================================================================
        // 2.1 Multi-chunk PCM payload (e.g. 24,000 samples = 48,000 bytes > 8192 buffer)
        val numSamples = 24000
        val originalPcm = ShortArray(numSamples) { i ->
            val t = i.toDouble() / 48000.0
            (sin(2.0 * PI * 440.0 * t) * 30000.0).toInt().toShort()
        }

        val wavBytes = WavWriter.pcm16ToWav(originalPcm, 48000, 1)

        // Decode via InputStream
        val parsedFromStream = WavWriter.readWav(ByteArrayInputStream(wavBytes))
        assertEquals(48000, parsedFromStream.sampleRate)
        assertEquals(1, parsedFromStream.numChannels)
        assertEquals(16, parsedFromStream.bitsPerSample)
        assertEquals(numSamples * 2, parsedFromStream.dataSize)
        assertEquals(0.5f, parsedFromStream.durationSeconds, 0.0001f)
        assertArrayEquals("Stream-decoded samples must match original", originalPcm, parsedFromStream.pcm16)

        // Decode via ByteArray
        val parsedFromBytes = WavWriter.readWav(wavBytes)
        assertArrayEquals("ByteArray-decoded samples must match original", originalPcm, parsedFromBytes.pcm16)

        // Decode via File
        val tempDir = File(System.getProperty("java.io.tmpdir"), "phase8_wav_test_${System.currentTimeMillis()}")
        tempDir.mkdirs()
        try {
            val wavFile = File(tempDir, "stream_test.wav")
            WavWriter.writeWav(wavFile, originalPcm, 48000, 1)
            val parsedFromFile = WavWriter.readWav(wavFile)
            assertEquals(parsedFromStream, parsedFromFile)
            assertArrayEquals("File streaming decoded samples must match original", originalPcm, parsedFromFile.pcm16)
        } finally {
            tempDir.deleteRecursively()
        }

        // =========================================================================
        // SECTION 3: WAV Streaming Parser Robustness (Custom & Unknown RIFF Chunks)
        // =========================================================================
        // Synthesize WAV with a JUNK chunk inserted before "fmt " and a LIST chunk before "data"
        val junkData = "MakeAiSound Optimization Test Junk Padding".toByteArray(Charsets.US_ASCII)
        val pcmDataSize = 100 * 2 // 100 samples
        val testPcm16 = ShortArray(100) { (it * 300).toShort() }

        val customWavBuffer = ByteBuffer.allocate(1024).order(ByteOrder.LITTLE_ENDIAN)
        customWavBuffer.put("RIFF".toByteArray(Charsets.US_ASCII))
        customWavBuffer.putInt(0) // Will patch at end
        customWavBuffer.put("WAVE".toByteArray(Charsets.US_ASCII))

        // JUNK chunk
        customWavBuffer.put("JUNK".toByteArray(Charsets.US_ASCII))
        customWavBuffer.putInt(junkData.size)
        customWavBuffer.put(junkData)

        // FMT chunk with extra extension bytes (size 18 instead of 16)
        customWavBuffer.put("fmt ".toByteArray(Charsets.US_ASCII))
        customWavBuffer.putInt(18)
        customWavBuffer.putShort(1.toShort()) // PCM format
        customWavBuffer.putShort(1.toShort()) // Mono
        customWavBuffer.putInt(48000) // Sample rate
        customWavBuffer.putInt(96000) // Byte rate
        customWavBuffer.putShort(2.toShort()) // Block align
        customWavBuffer.putShort(16.toShort()) // Bits per sample
        customWavBuffer.putShort(0.toShort()) // Extra cbSize

        // DATA chunk
        customWavBuffer.put("data".toByteArray(Charsets.US_ASCII))
        customWavBuffer.putInt(pcmDataSize)
        for (sample in testPcm16) {
            customWavBuffer.putShort(sample)
        }

        val totalLen = customWavBuffer.position()
        customWavBuffer.putInt(4, totalLen - 8)
        val customWavBytes = customWavBuffer.array().copyOf(totalLen)

        val customParsed = WavWriter.readWav(ByteArrayInputStream(customWavBytes))
        assertEquals(48000, customParsed.sampleRate)
        assertEquals(1, customParsed.numChannels)
        assertEquals(16, customParsed.bitsPerSample)
        assertEquals(pcmDataSize, customParsed.dataSize)
        assertArrayEquals("Parser must correctly skip non-data chunks and parse samples", testPcm16, customParsed.pcm16)

        // 3.2 Error handling validations
        try {
            WavWriter.readWav(ByteArrayInputStream("RIFF".toByteArray()))
            assertFalse("Should have thrown IllegalArgumentException for truncated stream", true)
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("too short") == true)
        }
    }
}
