package skul9x.example.makesound

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import skul9x.example.makesound.player.AudioPlayerManager
import skul9x.example.makesound.player.MediaPlayerAdapter
import skul9x.example.makesound.player.PlaybackStatus
import skul9x.example.makesound.player.VoicePlayerState
import skul9x.example.makesound.player.VoiceSamplePlayer
import java.io.File

/**
 * Single verification test for Phase 02: Independent Voice Sample Player Subsystem.
 *
 * Core functionality verified:
 * 1. State machine transitions of VoicePlayerState across play, pause, resume, stop, completion, and error.
 * 2. Strict single-active sample constraint (automatically halting preceding sample on new voice invocation).
 * 3. In-memory byte buffer playback with safe temporary file creation and zero-leak cleanup.
 * 4. Comprehensive error handling for invalid paths, empty byte buffers, and underlying adapter failures.
 * 5. Deterministic lifecycle release and resource teardown.
 * 6. Isolated coexistence with AudioPlayerManager ensuring non-interfering independent state flows.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class Phase2VoiceSamplePlayerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    // =========================================================================
    // Mock MediaPlayerAdapter for deterministic state machine testing
    // =========================================================================
    private class FakeMediaPlayerAdapter : MediaPlayerAdapter {
        var isPlayingInternal = false
        var currentPositionInternal = 0
        var durationInternal = 3000
        var isPrepared = false
        var isReleased = false
        var storedDataSource: String? = null

        var shouldThrowOnSetDataSource = false
        var shouldThrowOnPrepare = false
        var shouldThrowOnStart = false

        var setDataSourceCallCount = 0
        var prepareCallCount = 0
        var startCallCount = 0
        var stopCallCount = 0
        var pauseCallCount = 0
        var resetCallCount = 0
        var releaseCallCount = 0

        var completionCallback: (() -> Unit)? = null
        var errorCallback: ((Int, Int) -> Boolean)? = null

        override fun setDataSource(path: String) {
            if (shouldThrowOnSetDataSource || path == "throw_error_path") {
                throw IllegalArgumentException("Simulated setDataSource failure: $path")
            }
            storedDataSource = path
            setDataSourceCallCount++
        }

        override fun prepare() {
            if (shouldThrowOnPrepare) {
                throw IllegalStateException("Simulated prepare failure")
            }
            if (storedDataSource == null) throw IllegalStateException("No data source configured")
            isPrepared = true
            prepareCallCount++
        }

        override fun start() {
            if (shouldThrowOnStart) {
                throw IllegalStateException("Simulated start failure")
            }
            if (!isPrepared) throw IllegalStateException("Cannot start unprepared player")
            isPlayingInternal = true
            startCallCount++
        }

        override fun pause() {
            isPlayingInternal = false
            pauseCallCount++
        }

        override fun stop() {
            isPlayingInternal = false
            stopCallCount++
        }

        override fun seekTo(msec: Int) {
            currentPositionInternal = msec.coerceIn(0, durationInternal)
        }

        override fun isPlaying(): Boolean = isPlayingInternal

        override fun getCurrentPosition(): Int = currentPositionInternal

        override fun getDuration(): Int = durationInternal

        override fun reset() {
            isPlayingInternal = false
            isPrepared = false
            storedDataSource = null
            resetCallCount++
        }

        override fun release() {
            isPlayingInternal = false
            isPrepared = false
            isReleased = true
            releaseCallCount++
        }

        override fun setOnCompletionListener(listener: (() -> Unit)?) {
            completionCallback = listener
        }

        override fun setOnErrorListener(listener: ((what: Int, extra: Int) -> Boolean)?) {
            errorCallback = listener
        }

        fun triggerCompletion() {
            isPlayingInternal = false
            completionCallback?.invoke()
        }

        fun triggerError(what: Int = 1, extra: Int = -1004): Boolean {
            isPlayingInternal = false
            return errorCallback?.invoke(what, extra) ?: false
        }
    }

    // =========================================================================
    // Test 1: Initial state verification
    // =========================================================================
    @Test
    fun testInitialStateIsCleanAndIdle() {
        val adapter = FakeMediaPlayerAdapter()
        val player = VoiceSamplePlayer(adapter)

        val state = player.playerState.value
        assertEquals(VoicePlayerState.IDLE, state)
        assertNull(state.currentVoiceName)
        assertFalse(state.isPlaying)
        assertNull(state.error)
        assertTrue(state.isIdle)
        assertNull(player.currentVoiceName)
        assertFalse(player.isPlaying)
        assertNull(player.getActiveTempFile())
    }

    // =========================================================================
    // Test 2: Play sample lifecycle (start and state update)
    // =========================================================================
    @Test
    fun testPlayVoiceSampleSuccess() {
        val adapter = FakeMediaPlayerAdapter()
        val player = VoiceSamplePlayer(adapter)

        val dummyAudioFile = tempFolder.newFile("sample_doan.wav").apply {
            writeBytes(ByteArray(100) { 0x01 })
        }

        val success = player.playVoiceSample("Đoan", dummyAudioFile.absolutePath)
        assertTrue("playVoiceSample should return true", success)

        val state = player.playerState.value
        assertEquals("Đoan", state.currentVoiceName)
        assertTrue(state.isPlaying)
        assertNull(state.error)
        assertFalse(state.isIdle)

        assertEquals("Đoan", player.currentVoiceName)
        assertTrue(player.isPlaying)

        assertEquals(1, adapter.setDataSourceCallCount)
        assertEquals(1, adapter.prepareCallCount)
        assertEquals(1, adapter.startCallCount)
        assertEquals(dummyAudioFile.absolutePath, adapter.storedDataSource)
    }

    // =========================================================================
    // Test 3: Natural audio completion resets player state
    // =========================================================================
    @Test
    fun testNaturalCompletionResetsState() {
        val adapter = FakeMediaPlayerAdapter()
        val player = VoiceSamplePlayer(adapter)
        val file = tempFolder.newFile("sample_mai.wav").apply { writeBytes(ByteArray(50)) }

        player.playVoiceSample("Mai", file.absolutePath)
        assertTrue(player.isPlaying)
        assertEquals("Mai", player.currentVoiceName)

        // Trigger natural completion
        adapter.triggerCompletion()

        val stateAfterCompletion = player.playerState.value
        assertFalse(stateAfterCompletion.isPlaying)
        assertNull(stateAfterCompletion.currentVoiceName)
        assertNull(stateAfterCompletion.error)
        assertTrue(stateAfterCompletion.isIdle)
        assertFalse(player.isPlaying)
        assertNull(player.currentVoiceName)
    }

    // =========================================================================
    // Test 4: Explicit stop halts playback and resets state
    // =========================================================================
    @Test
    fun testStopVoiceSampleHaltsPlaybackAndClearsState() {
        val adapter = FakeMediaPlayerAdapter()
        val player = VoiceSamplePlayer(adapter)
        val file = tempFolder.newFile("sample_nam.wav").apply { writeBytes(ByteArray(50)) }

        player.playVoiceSample("Nam", file.absolutePath)
        assertTrue(player.isPlaying)

        player.stopVoiceSample()

        val state = player.playerState.value
        assertFalse(state.isPlaying)
        assertNull(state.currentVoiceName)
        assertNull(state.error)
        assertTrue(state.isIdle)
        assertEquals(1, adapter.stopCallCount)
    }

    // =========================================================================
    // Test 5: Pause and resume transitions
    // =========================================================================
    @Test
    fun testPauseAndResumeVoiceSample() {
        val adapter = FakeMediaPlayerAdapter()
        val player = VoiceSamplePlayer(adapter)
        val file = tempFolder.newFile("sample_linh.wav").apply { writeBytes(ByteArray(50)) }

        player.playVoiceSample("Linh", file.absolutePath)
        assertTrue(player.isPlaying)

        // Pause
        val pauseResult = player.pauseVoiceSample()
        assertTrue(pauseResult)
        assertFalse(player.isPlaying)
        assertEquals("Linh", player.currentVoiceName)
        assertEquals(1, adapter.pauseCallCount)

        // Resume
        val resumeResult = player.resumeVoiceSample()
        assertTrue(resumeResult)
        assertTrue(player.isPlaying)
        assertEquals("Linh", player.currentVoiceName)
        assertEquals(2, adapter.startCallCount)
    }

    // =========================================================================
    // Test 6: Single-Active constraint auto-stops prior voice sample
    // =========================================================================
    @Test
    fun testSingleActiveConstraintAutoStopsPrevious() {
        val adapter = FakeMediaPlayerAdapter()
        val player = VoiceSamplePlayer(adapter)

        val fileA = tempFolder.newFile("voice_a.wav").apply { writeBytes(ByteArray(64)) }
        val fileB = tempFolder.newFile("voice_b.wav").apply { writeBytes(ByteArray(64)) }

        // Start Voice A
        player.playVoiceSample("VoiceA", fileA.absolutePath)
        assertEquals("VoiceA", player.currentVoiceName)
        assertTrue(player.isPlaying)

        // Start Voice B while Voice A is playing
        val successB = player.playVoiceSample("VoiceB", fileB.absolutePath)
        assertTrue(successB)

        // Voice A must have been halted, and Voice B now active
        assertEquals(1, adapter.stopCallCount)
        assertEquals(2, adapter.startCallCount)
        assertEquals(fileB.absolutePath, adapter.storedDataSource)
        assertEquals("VoiceB", player.currentVoiceName)
        assertTrue(player.isPlaying)
        assertNull(player.playerState.value.error)
    }

    // =========================================================================
    // Test 7: Byte buffer playback and safe temporary file lifecycle
    // =========================================================================
    @Test
    fun testPlayVoiceBytesAndTempFileManagement() {
        val adapter = FakeMediaPlayerAdapter()
        val player = VoiceSamplePlayer(adapter)
        val cacheDir = tempFolder.newFolder("test_preview_cache")

        val bytes1 = ByteArray(128) { (it % 100).toByte() }
        val success1 = player.playVoiceBytes("Quynh", bytes1, cacheDir)
        assertTrue(success1)
        assertTrue(player.isPlaying)
        assertEquals("Quynh", player.currentVoiceName)

        val tempFile1 = player.getActiveTempFile()
        assertNotNull("Temp file should be created", tempFile1)
        assertTrue("Temp file must exist on disk", tempFile1!!.exists())
        assertEquals(bytes1.size.toLong(), tempFile1.length())

        // Play another voice's bytes -> tempFile1 must be cleaned up, tempFile2 created
        val bytes2 = ByteArray(256) { (it % 50).toByte() }
        val success2 = player.playVoiceBytes("Huy", bytes2, cacheDir)
        assertTrue(success2)
        assertEquals("Huy", player.currentVoiceName)

        assertFalse("Previous temp file must be cleaned up", tempFile1.exists())
        val tempFile2 = player.getActiveTempFile()
        assertNotNull(tempFile2)
        assertTrue("New temp file must exist on disk", tempFile2!!.exists())
        assertEquals(bytes2.size.toLong(), tempFile2.length())

        // Release must clean up any remaining temp files
        player.release()
        assertFalse("Active temp file must be deleted on release", tempFile2.exists())
        assertNull(player.getActiveTempFile())
    }

    // =========================================================================
    // Test 8: Error handling for invalid paths, empty bytes, and adapter errors
    // =========================================================================
    @Test
    fun testInvalidInputsAndAdapterErrorHandling() {
        val adapter = FakeMediaPlayerAdapter()
        val player = VoiceSamplePlayer(adapter)
        val cacheDir = tempFolder.newFolder("err_cache")

        // 1. Blank path
        val blankResult = player.playVoiceSample("BlankVoice", "   ")
        assertFalse(blankResult)
        assertFalse(player.isPlaying)
        assertNotNull(player.playerState.value.error)
        assertTrue(player.playerState.value.error!!.contains("Invalid audio path"))

        // 2. Empty byte buffer
        val emptyBytesResult = player.playVoiceBytes("EmptyVoice", ByteArray(0), cacheDir)
        assertFalse(emptyBytesResult)
        assertFalse(player.isPlaying)
        assertNotNull(player.playerState.value.error)
        assertTrue(player.playerState.value.error!!.contains("empty audio bytes"))

        // 3. Adapter throws during prepare
        adapter.shouldThrowOnPrepare = true
        val validFile = tempFolder.newFile("valid_dummy.wav").apply { writeBytes(ByteArray(20)) }
        val failResult = player.playVoiceSample("ErrorVoice", validFile.absolutePath)
        assertFalse(failResult)
        assertFalse(player.isPlaying)
        assertNull(player.currentVoiceName)
        assertNotNull(player.playerState.value.error)
        assertTrue(player.playerState.value.error!!.contains("prepare failure"))
    }

    // =========================================================================
    // Test 9: Async error listener triggers error state
    // =========================================================================
    @Test
    fun testAsyncErrorListenerTransitionsState() {
        val adapter = FakeMediaPlayerAdapter()
        val player = VoiceSamplePlayer(adapter)
        val file = tempFolder.newFile("err_test.wav").apply { writeBytes(ByteArray(20)) }

        player.playVoiceSample("FragileVoice", file.absolutePath)
        assertTrue(player.isPlaying)

        // Trigger MediaPlayer asynchronous error callback
        val handled = adapter.triggerError(what = 1, extra = -1004)
        assertTrue(handled)

        val state = player.playerState.value
        assertFalse(state.isPlaying)
        assertNull(state.currentVoiceName)
        assertNotNull(state.error)
        assertTrue(state.error!!.contains("Playback error"))
        assertTrue(state.error!!.contains("what: 1"))
        assertTrue(state.error!!.contains("extra: -1004"))
    }

    // =========================================================================
    // Test 10: Lifecycle teardown and release
    // =========================================================================
    @Test
    fun testLifecycleTeardownAndRelease() {
        val adapter = FakeMediaPlayerAdapter()
        val player = VoiceSamplePlayer(adapter)
        val file = tempFolder.newFile("release_test.wav").apply { writeBytes(ByteArray(20)) }

        player.playVoiceSample("ReleaseVoice", file.absolutePath)
        assertTrue(player.isPlaying)

        player.release()

        val state = player.playerState.value
        assertEquals(VoicePlayerState.IDLE, state)
        assertFalse(player.isPlaying)
        assertNull(player.currentVoiceName)
        assertTrue(adapter.isReleased)
        assertEquals(1, adapter.releaseCallCount)
    }

    // =========================================================================
    // Test 11: Coexistence with AudioPlayerManager (Non-interference)
    // =========================================================================
    @Test
    fun testCoexistenceWithAudioPlayerManager() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)

        val mainAdapter = FakeMediaPlayerAdapter().apply { durationInternal = 60000 }
        val previewAdapter = FakeMediaPlayerAdapter().apply { durationInternal = 2000 }

        val mainPlayer = AudioPlayerManager(coroutineScope = testScope, playerAdapter = mainAdapter)
        val previewPlayer = VoiceSamplePlayer(previewAdapter)

        val mainFile = tempFolder.newFile("main_story.wav").apply { writeBytes(ByteArray(500)) }
        val sampleFile = tempFolder.newFile("sample_preview.wav").apply { writeBytes(ByteArray(100)) }

        // 1. Start main player
        mainPlayer.load(mainFile)
        mainPlayer.play()
        assertEquals(PlaybackStatus.PLAYING, mainPlayer.playerState.value.status)
        assertTrue(mainPlayer.playerState.value.isPlaying)

        // 2. Play voice sample preview simultaneously
        previewPlayer.playVoiceSample("PreviewVoice", sampleFile.absolutePath)
        assertTrue(previewPlayer.isPlaying)
        assertEquals("PreviewVoice", previewPlayer.currentVoiceName)

        // Verify main player state is completely unaffected
        assertEquals(PlaybackStatus.PLAYING, mainPlayer.playerState.value.status)
        assertTrue(mainPlayer.playerState.value.isPlaying)
        assertEquals(mainFile.absolutePath, mainPlayer.playerState.value.audioFilePath)

        // 3. Stop preview player -> main player remains playing
        previewPlayer.stopVoiceSample()
        assertFalse(previewPlayer.isPlaying)
        assertNull(previewPlayer.currentVoiceName)

        assertEquals(PlaybackStatus.PLAYING, mainPlayer.playerState.value.status)
        assertTrue(mainPlayer.playerState.value.isPlaying)

        // 4. Teardown main player -> preview player remains functional
        mainPlayer.release()
        assertEquals(PlaybackStatus.IDLE, mainPlayer.playerState.value.status)

        previewPlayer.playVoiceSample("PreviewVoice2", sampleFile.absolutePath)
        assertTrue(previewPlayer.isPlaying)
        assertEquals("PreviewVoice2", previewPlayer.currentVoiceName)

        previewPlayer.release()
        assertFalse(previewPlayer.isPlaying)
    }
}
