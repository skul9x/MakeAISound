package skul9x.example.makesound

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import skul9x.example.makesound.player.AudioPlayerManager
import skul9x.example.makesound.player.MediaPlayerAdapter
import skul9x.example.makesound.player.PlaybackProgressTracker
import skul9x.example.makesound.player.PlaybackStatus
import skul9x.example.makesound.player.PlayerState
import skul9x.example.makesound.player.WaveformSampler
import skul9x.example.makesound.storage.WavWriter
import java.io.File
import kotlin.math.PI
import kotlin.math.sin

/**
 * Single verification test for Phase 04: Audio Studio Player & Waveform Visualizer.
 *
 * Core functionality verified:
 * 1. WaveformSampler downsampling algorithm producing strictly bounded [0.0, 1.0] float arrays
 *    across empty, small, large, RMS, Peak, and WAV inputs.
 * 2. PlayerState time formatting (e.g. "00:15 / 00:45"), progress ratios, and PlaybackProgressTracker.
 * 3. State machine transitions across player lifecycle events (load, play, pause, seek, replay, stop, completion, error, release).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class Phase4AudioPlayerTest {

    // =========================================================================
    // 1. Mock MediaPlayerAdapter for deterministic state machine testing
    // =========================================================================
    private class FakeMediaPlayerAdapter : MediaPlayerAdapter {
        var isPlayingInternal = false
        var currentPositionInternal = 0
        var durationInternal = 45000 // 45 seconds default
        var isPrepared = false
        var isReleased = false
        var storedDataSource: String? = null

        var completionCallback: (() -> Unit)? = null
        var errorCallback: ((Int, Int) -> Boolean)? = null

        override fun setDataSource(path: String) {
            if (path == "invalid_path") {
                throw IllegalArgumentException("Invalid data source path")
            }
            storedDataSource = path
        }

        override fun prepare() {
            if (storedDataSource == null) throw IllegalStateException("No data source set")
            isPrepared = true
        }

        override fun start() {
            if (!isPrepared) throw IllegalStateException("Player not prepared")
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
    fun verifyPhase4CoreFunctionality() = runTest {
        // =========================================================================
        // SECTION 1: WaveformSampler Downsampling & Envelope Calculations
        // =========================================================================
        val barCount = 40

        // 1.1 Empty PCM test
        val emptyPcm = ShortArray(0)
        val emptyBars = WaveformSampler.samplePcm16(emptyPcm, barCount)
        assertEquals(barCount, emptyBars.size)
        assertTrue(emptyBars.all { it == 0f })

        // 1.2 Small PCM (< barCount samples)
        val smallPcm = ShortArray(10) { (it * 3000).toShort() }
        val smallBars = WaveformSampler.samplePcm16(smallPcm, barCount, useRms = true)
        assertEquals(barCount, smallBars.size)
        assertTrue("All amplitude values must be within [0.0, 1.0]", smallBars.all { it in 0f..1f })

        // 1.3 Synthesized Tone PCM (1 second @ 48kHz = 48,000 samples)
        val sampleRate = 48000
        val numSamples = 48000
        val sinePcm = ShortArray(numSamples)
        for (i in 0 until numSamples) {
            // Modulation: amplitude ramps up and down
            val envelope = sin(PI * i / numSamples)
            val wave = sin(2.0 * PI * 440.0 * i / sampleRate)
            sinePcm[i] = (envelope * wave * 30000.0).toInt().toShort()
        }

        // Test RMS Downsampling
        val rmsBars = WaveformSampler.samplePcm16(sinePcm, barCount, useRms = true, normalizeToPeak = true)
        assertEquals(barCount, rmsBars.size)
        assertTrue("All RMS bars must be in range [0.0, 1.0]", rmsBars.all { it in 0f..1f })
        // First and last bars should be lower than middle bars due to envelope
        assertTrue("Middle bar should be higher amplitude than first bar", rmsBars[barCount / 2] > rmsBars[0])
        assertEquals("Peak normalized bars should reach 1.0 at maximum", 1.0f, rmsBars.maxOrNull() ?: 0f, 0.001f)

        // Test Peak Downsampling
        val peakBars = WaveformSampler.samplePcm16(sinePcm, barCount, useRms = false, normalizeToPeak = true)
        assertEquals(barCount, peakBars.size)
        assertTrue("All Peak bars must be in range [0.0, 1.0]", peakBars.all { it in 0f..1f })
        assertEquals("Peak normalized peak bars should reach 1.0", 1.0f, peakBars.maxOrNull() ?: 0f, 0.001f)

        // 1.4 Float PCM Downsampling
        val floatPcm = FloatArray(numSamples) { sinePcm[it] / 32767f }
        val floatBars = WaveformSampler.sampleFloatPcm(floatPcm, barCount, useRms = true)
        assertEquals(barCount, floatBars.size)
        assertTrue("Float PCM bars must be in range [0.0, 1.0]", floatBars.all { it in 0f..1f })

        // 1.5 WAV bytes & Disk WAV File Downsampling
        val wavBytes = WavWriter.pcm16ToWav(sinePcm, sampleRate, 1)
        val wavBars = WaveformSampler.sampleWavBytes(wavBytes, barCount)
        assertEquals(barCount, wavBars.size)
        assertTrue("WAV bytes bars must be in range [0.0, 1.0]", wavBars.all { it in 0f..1f })

        val tempDir = File(System.getProperty("java.io.tmpdir"), "phase4_test_${System.currentTimeMillis()}")
        tempDir.mkdirs()
        try {
            val wavFile = File(tempDir, "sample_audio.wav")
            WavWriter.writeWav(wavFile, sinePcm, sampleRate, 1)
            val fileBars = WaveformSampler.sampleWavFile(wavFile, barCount)
            assertEquals(barCount, fileBars.size)
            assertArrayEquals("File bars must match bytes bars", wavBars, fileBars, 0.0001f)
        } finally {
            tempDir.deleteRecursively()
        }

        // =========================================================================
        // SECTION 2: PlayerState Time Formatting & PlaybackProgressTracker
        // =========================================================================
        // Time formatting checks
        assertEquals("00:00", PlayerState.formatTime(0L))
        assertEquals("00:15", PlayerState.formatTime(15000L))
        assertEquals("00:45", PlayerState.formatTime(45000L))
        assertEquals("01:15", PlayerState.formatTime(75000L))
        assertEquals("10:00", PlayerState.formatTime(600000L))
        assertEquals("00:00", PlayerState.formatTime(-5000L))

        val testState = PlayerState(
            status = PlaybackStatus.PLAYING,
            currentPositionMs = 15000L,
            durationMs = 45000L,
            audioFilePath = "/path/to/test.wav"
        )
        assertEquals("00:15", testState.formattedPosition)
        assertEquals("00:45", testState.formattedDuration)
        assertEquals("00:15 / 00:45", testState.formattedTimeDisplay)
        assertEquals(15000f / 45000f, testState.progress, 0.001f)
        assertTrue(testState.isPlaying)
        assertFalse(testState.isPaused)
        assertTrue(testState.isPrepared)

        // PlaybackProgressTracker checks
        assertEquals(0, PlaybackProgressTracker.calculateActiveBarIndex(0L, 45000L, 40))
        assertEquals(13, PlaybackProgressTracker.calculateActiveBarIndex(15000L, 45000L, 40)) // 1/3 * 40 = 13.33 -> 13
        assertEquals(39, PlaybackProgressTracker.calculateActiveBarIndex(45000L, 45000L, 40))
        assertEquals(0.5f, PlaybackProgressTracker.calculateProgress(22500L, 45000L), 0.0001f)
        assertEquals(22500L, PlaybackProgressTracker.calculateSeekPosition(0.5f, 45000L))

        // =========================================================================
        // SECTION 3: AudioPlayerManager State Machine Transitions & Polling
        // =========================================================================
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)
        val fakeAdapter = FakeMediaPlayerAdapter()
        val playerManager = AudioPlayerManager(
            coroutineScope = testScope,
            playerAdapter = fakeAdapter
        )

        // 3.1 Initial state
        assertEquals(PlaybackStatus.IDLE, playerManager.playerState.value.status)

        // 3.2 Load audio file
        val loadSuccess = playerManager.load("/storage/audio_sample.wav")
        assertTrue("Load must succeed", loadSuccess)
        assertEquals(PlaybackStatus.PREPARED, playerManager.playerState.value.status)
        assertEquals(45000L, playerManager.playerState.value.durationMs)
        assertEquals(0L, playerManager.playerState.value.currentPositionMs)
        assertEquals("/storage/audio_sample.wav", playerManager.playerState.value.audioFilePath)

        // 3.3 Start Playback
        val playSuccess = playerManager.play()
        assertTrue("Play must succeed", playSuccess)
        assertEquals(PlaybackStatus.PLAYING, playerManager.playerState.value.status)
        assertTrue(fakeAdapter.isPlaying())

        // Simulate time passage and position advancement in adapter
        fakeAdapter.currentPositionInternal = 5000
        testScheduler.advanceTimeBy(100) // Trigger coroutine polling cycle
        assertEquals(5000L, playerManager.playerState.value.currentPositionMs)

        // 3.4 Precision Seek while playing
        playerManager.seekTo(20000L)
        assertEquals(20000, fakeAdapter.currentPositionInternal)
        assertEquals(20000L, playerManager.playerState.value.currentPositionMs)

        // Seek by fraction
        playerManager.seekToFraction(0.6666667f)
        assertEquals(30000L, playerManager.playerState.value.currentPositionMs)

        // 3.5 Pause Playback
        val pauseSuccess = playerManager.pause()
        assertTrue("Pause must succeed", pauseSuccess)
        assertEquals(PlaybackStatus.PAUSED, playerManager.playerState.value.status)
        assertFalse(fakeAdapter.isPlaying())

        // 3.6 Resume Playback
        val resumeSuccess = playerManager.resume()
        assertTrue("Resume must succeed", resumeSuccess)
        assertEquals(PlaybackStatus.PLAYING, playerManager.playerState.value.status)

        // 3.7 Stop Playback
        playerManager.stop()
        assertEquals(PlaybackStatus.PREPARED, playerManager.playerState.value.status)
        assertEquals(0L, playerManager.playerState.value.currentPositionMs)

        // 3.8 Replay
        val replaySuccess = playerManager.replay()
        assertTrue("Replay must succeed", replaySuccess)
        assertEquals(PlaybackStatus.PLAYING, playerManager.playerState.value.status)

        // 3.9 Completion Event
        fakeAdapter.completionCallback?.invoke()
        assertEquals(PlaybackStatus.COMPLETED, playerManager.playerState.value.status)
        assertEquals(45000L, playerManager.playerState.value.currentPositionMs)

        // Playing from COMPLETED state restarts from 0
        playerManager.play()
        assertEquals(PlaybackStatus.PLAYING, playerManager.playerState.value.status)
        assertEquals(0L, playerManager.playerState.value.currentPositionMs)

        // 3.10 Error Event
        fakeAdapter.errorCallback?.invoke(1, -1004)
        assertEquals(PlaybackStatus.ERROR, playerManager.playerState.value.status)
        assertNotNull(playerManager.playerState.value.errorMessage)

        // Load error handling
        val invalidLoad = playerManager.load("invalid_path")
        assertFalse("Invalid load must return false", invalidLoad)
        assertEquals(PlaybackStatus.ERROR, playerManager.playerState.value.status)

        // 3.11 Release
        playerManager.release()
        assertEquals(PlaybackStatus.IDLE, playerManager.playerState.value.status)
        assertTrue(fakeAdapter.isReleased)
    }
}
