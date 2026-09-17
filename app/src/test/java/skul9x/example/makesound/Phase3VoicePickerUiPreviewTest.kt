package skul9x.example.makesound

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import skul9x.example.makesound.engine.VoicePreset
import skul9x.example.makesound.engine.VoiceSampleManager
import skul9x.example.makesound.player.AudioPlayerManager
import skul9x.example.makesound.player.MediaPlayerAdapter
import skul9x.example.makesound.player.VoicePlayerState
import skul9x.example.makesound.player.VoiceSamplePlayer
import skul9x.example.makesound.ui.MainViewModel
import skul9x.example.makesound.ui.MakeAiSoundViewModel

/**
 * Single verification test for Phase 03: Voice Picker UI Preview Integration.
 *
 * Core functionality verified:
 * 1. State transitions when toggling audio sample previews for different voice presets.
 * 2. Strict user interaction separation: previewing voices does not alter the selectedVoice state.
 * 3. Single-active constraint: auditioning a new voice automatically halts the previous one.
 * 4. Toggling the currently playing voice off stops playback immediately.
 * 5. Dismissal safety: closing or dismissing the bottom sheet cleanly terminates active preview audio.
 * 6. Voice selection safety: picking a voice preset stops any active sample audition and updates selected voice.
 * 7. Natural completion and adapter error handling in the preview pipeline.
 * 8. Deterministic lifecycle release and zero-leak resource teardown on ViewModel clear.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class Phase3VoicePickerUiPreviewTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private class FakeMediaPlayerAdapter : MediaPlayerAdapter {
        var isPlayingInternal = false
        var currentPositionInternal = 0
        var durationInternal = 2500
        var isPrepared = false
        var isReleased = false
        var storedDataSource: String? = null

        var setDataSourceCallCount = 0
        var prepareCallCount = 0
        var startCallCount = 0
        var stopCallCount = 0
        var resetCallCount = 0
        var releaseCallCount = 0

        var completionCallback: (() -> Unit)? = null
        var errorCallback: ((Int, Int) -> Boolean)? = null

        override fun setDataSource(path: String) {
            storedDataSource = path
            setDataSourceCallCount++
        }

        override fun prepare() {
            isPrepared = true
            prepareCallCount++
        }

        override fun start() {
            isPlayingInternal = true
            startCallCount++
        }

        override fun pause() {
            isPlayingInternal = false
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

    private class TestableViewModel(
        synthesizer: skul9x.example.makesound.engine.VieNeuStudioSynthesizer?,
        playerManager: AudioPlayerManager,
        voiceSampleManager: VoiceSampleManager,
        voiceSamplePlayer: VoiceSamplePlayer,
        ioDispatcher: kotlinx.coroutines.CoroutineDispatcher,
        customScope: kotlinx.coroutines.CoroutineScope
    ) : MakeAiSoundViewModel(
        synthesizer = synthesizer,
        playerManager = playerManager,
        voiceSampleManager = voiceSampleManager,
        voiceSamplePlayer = voiceSamplePlayer,
        ioDispatcher = ioDispatcher,
        customScope = customScope
    ) {
        public override fun onCleared() {
            super.onCleared()
        }
    }

    private val sampleVoices = listOf(
        VoicePreset(
            name = "Minh Đức",
            description = "Nam · Bắc · Tin tức",
            gender = "male",
            region = "Bắc",
            style = "tin_tuc",
            speakerEmb = FloatArray(192)
        ),
        VoicePreset(
            name = "Trúc Ly",
            description = "Nữ · Bắc · Tự nhiên",
            gender = "female",
            region = "Bắc",
            style = "tu_nhien",
            speakerEmb = FloatArray(192)
        ),
        VoicePreset(
            name = "Thái Sơn",
            description = "Nam · Nam · Kể chuyện",
            gender = "male",
            region = "Nam",
            style = "doc_truyen",
            speakerEmb = FloatArray(192)
        ),
        VoicePreset(
            name = "Hương Giang",
            description = "Nữ · Trung · Tự nhiên",
            gender = "female",
            region = "Trung",
            style = "tu_nhien",
            speakerEmb = FloatArray(192)
        )
    )

    // =========================================================================
    // Test 1: Initial State and Baseline Verification
    // =========================================================================
    @Test
    fun testInitialPreviewStateIsIdleAndIsolated() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)
        val fakeAdapter = FakeMediaPlayerAdapter()
        val samplePlayer = VoiceSamplePlayer(fakeAdapter)
        val sampleManager = VoiceSampleManager()

        val viewModel = TestableViewModel(
            synthesizer = null,
            playerManager = AudioPlayerManager(coroutineScope = testScope, playerAdapter = fakeAdapter),
            voiceSampleManager = sampleManager,
            voiceSamplePlayer = samplePlayer,
            ioDispatcher = testDispatcher,
            customScope = testScope
        )

        viewModel.setAvailableVoices(sampleVoices)

        // Baseline checks
        assertEquals(VoicePlayerState.IDLE, viewModel.voicePlayerState.value)
        assertFalse(viewModel.voicePlayerState.value.isPlaying)
        assertNull(viewModel.voicePlayerState.value.currentVoiceName)
        assertEquals("Minh Đức", viewModel.uiState.value.selectedVoice?.name)
        assertFalse(viewModel.uiState.value.showVoicePicker)

        viewModel.onCleared()
    }

    // =========================================================================
    // Test 2: Toggle Preview for Voice A and Verify Selection Isolation
    // =========================================================================
    @Test
    fun testToggleVoicePreviewStartsAudioWithoutAlteringSelectedVoice() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)
        val fakeAdapter = FakeMediaPlayerAdapter()
        val samplePlayer = VoiceSamplePlayer(fakeAdapter)
        val sampleManager = VoiceSampleManager()

        val viewModel = TestableViewModel(
            synthesizer = null,
            playerManager = AudioPlayerManager(coroutineScope = testScope, playerAdapter = fakeAdapter),
            voiceSampleManager = sampleManager,
            voiceSamplePlayer = samplePlayer,
            ioDispatcher = testDispatcher,
            customScope = testScope
        )

        viewModel.setAvailableVoices(sampleVoices)
        viewModel.showVoicePicker(true)
        assertTrue(viewModel.uiState.value.showVoicePicker)

        // Selected voice starts as "Minh Đức"
        assertEquals("Minh Đức", viewModel.uiState.value.selectedVoice?.name)

        // User auditions "Trúc Ly"
        viewModel.toggleVoicePreview(sampleVoices[1])
        testScheduler.advanceUntilIdle()

        // Preview state must reflect active playback for Trúc Ly
        assertTrue("voicePlayerState must be playing", viewModel.voicePlayerState.value.isPlaying)
        assertEquals("Trúc Ly", viewModel.voicePlayerState.value.currentVoiceName)
        assertTrue("Underlying adapter must be playing", fakeAdapter.isPlaying())
        assertEquals(1, fakeAdapter.startCallCount)

        // STRICT SELECTION ISOLATION: Selected voice MUST NOT have changed
        assertEquals(
            "Auditioning a voice preview must never change active selectedVoice",
            "Minh Đức",
            viewModel.uiState.value.selectedVoice?.name
        )
        // Bottom sheet remains open
        assertTrue(viewModel.uiState.value.showVoicePicker)

        viewModel.onCleared()
    }

    // =========================================================================
    // Test 3: Single-Active Constraint Across Voices and Toggle-Off Behavior
    // =========================================================================
    @Test
    fun testSingleActiveConstraintAndToggleOffBehavior() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)
        val fakeAdapter = FakeMediaPlayerAdapter()
        val samplePlayer = VoiceSamplePlayer(fakeAdapter)
        val sampleManager = VoiceSampleManager()

        val viewModel = TestableViewModel(
            synthesizer = null,
            playerManager = AudioPlayerManager(coroutineScope = testScope, playerAdapter = fakeAdapter),
            voiceSampleManager = sampleManager,
            voiceSamplePlayer = samplePlayer,
            ioDispatcher = testDispatcher,
            customScope = testScope
        )

        viewModel.setAvailableVoices(sampleVoices)

        // 1. Start preview for "Trúc Ly"
        viewModel.toggleVoicePreview(sampleVoices[1])
        testScheduler.advanceUntilIdle()
        assertTrue(viewModel.voicePlayerState.value.isPlaying)
        assertEquals("Trúc Ly", viewModel.voicePlayerState.value.currentVoiceName)

        // 2. While "Trúc Ly" is playing, audition "Thái Sơn" (Voice B)
        viewModel.toggleVoicePreview(sampleVoices[2])
        testScheduler.advanceUntilIdle()

        // Must switch seamlessly to "Thái Sơn" and halt "Trúc Ly"
        assertTrue(viewModel.voicePlayerState.value.isPlaying)
        assertEquals("Thái Sơn", viewModel.voicePlayerState.value.currentVoiceName)
        assertTrue(fakeAdapter.isPlaying())
        assertTrue("Prior voice must have been stopped", fakeAdapter.stopCallCount >= 1)

        // Selected voice remains unchanged
        assertEquals("Minh Đức", viewModel.uiState.value.selectedVoice?.name)

        // 3. Toggle off: Tap "Thái Sơn" again while it is currently playing
        viewModel.toggleVoicePreview(sampleVoices[2])
        testScheduler.advanceUntilIdle()

        // Playback must halt immediately
        assertFalse(viewModel.voicePlayerState.value.isPlaying)
        assertNull(viewModel.voicePlayerState.value.currentVoiceName)
        assertFalse(fakeAdapter.isPlaying())

        // Selected voice remains unchanged
        assertEquals("Minh Đức", viewModel.uiState.value.selectedVoice?.name)

        viewModel.onCleared()
    }

    // =========================================================================
    // Test 4: Bottom Sheet Dismissal Cleanly Terminates Active Preview
    // =========================================================================
    @Test
    fun testBottomSheetDismissalStopsPreviewAudio() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)
        val fakeAdapter = FakeMediaPlayerAdapter()
        val samplePlayer = VoiceSamplePlayer(fakeAdapter)
        val sampleManager = VoiceSampleManager()

        val viewModel = TestableViewModel(
            synthesizer = null,
            playerManager = AudioPlayerManager(coroutineScope = testScope, playerAdapter = fakeAdapter),
            voiceSampleManager = sampleManager,
            voiceSamplePlayer = samplePlayer,
            ioDispatcher = testDispatcher,
            customScope = testScope
        )

        viewModel.setAvailableVoices(sampleVoices)
        viewModel.showVoicePicker(true)

        // Start preview for "Hương Giang"
        viewModel.toggleVoicePreview(sampleVoices[3])
        testScheduler.advanceUntilIdle()
        assertTrue(viewModel.voicePlayerState.value.isPlaying)
        assertEquals("Hương Giang", viewModel.voicePlayerState.value.currentVoiceName)

        // Dismiss the bottom sheet
        viewModel.showVoicePicker(false)
        testScheduler.advanceUntilIdle()

        // Audio preview must be halted automatically on dismissal
        assertFalse("Playback must stop on sheet dismissal", viewModel.voicePlayerState.value.isPlaying)
        assertNull(viewModel.voicePlayerState.value.currentVoiceName)
        assertFalse(fakeAdapter.isPlaying())
        assertFalse(viewModel.uiState.value.showVoicePicker)

        viewModel.onCleared()
    }

    // =========================================================================
    // Test 5: Voice Selection Automatically Terminates Active Preview
    // =========================================================================
    @Test
    fun testVoiceSelectionStopsPreviewAndUpdatesSelectedVoice() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)
        val fakeAdapter = FakeMediaPlayerAdapter()
        val samplePlayer = VoiceSamplePlayer(fakeAdapter)
        val sampleManager = VoiceSampleManager()

        val viewModel = TestableViewModel(
            synthesizer = null,
            playerManager = AudioPlayerManager(coroutineScope = testScope, playerAdapter = fakeAdapter),
            voiceSampleManager = sampleManager,
            voiceSamplePlayer = samplePlayer,
            ioDispatcher = testDispatcher,
            customScope = testScope
        )

        viewModel.setAvailableVoices(sampleVoices)
        viewModel.showVoicePicker(true)

        // Audition "Trúc Ly"
        viewModel.toggleVoicePreview(sampleVoices[1])
        testScheduler.advanceUntilIdle()
        assertTrue(viewModel.voicePlayerState.value.isPlaying)

        // User chooses to select "Trúc Ly"
        viewModel.onVoiceSelected(sampleVoices[1])
        testScheduler.advanceUntilIdle()

        // Preview should be stopped, voice selected, sheet closed
        assertFalse(viewModel.voicePlayerState.value.isPlaying)
        assertNull(viewModel.voicePlayerState.value.currentVoiceName)
        assertFalse(fakeAdapter.isPlaying())
        assertEquals("Trúc Ly", viewModel.uiState.value.selectedVoice?.name)
        assertFalse(viewModel.uiState.value.showVoicePicker)

        viewModel.onCleared()
    }

    // =========================================================================
    // Test 6: Natural Completion and Error Handling
    // =========================================================================
    @Test
    fun testNaturalCompletionAndAdapterErrors() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)
        val fakeAdapter = FakeMediaPlayerAdapter()
        val samplePlayer = VoiceSamplePlayer(fakeAdapter)
        val sampleManager = VoiceSampleManager()

        val viewModel = TestableViewModel(
            synthesizer = null,
            playerManager = AudioPlayerManager(coroutineScope = testScope, playerAdapter = fakeAdapter),
            voiceSampleManager = sampleManager,
            voiceSamplePlayer = samplePlayer,
            ioDispatcher = testDispatcher,
            customScope = testScope
        )

        viewModel.setAvailableVoices(sampleVoices)

        // 1. Natural completion
        viewModel.toggleVoicePreview(sampleVoices[0])
        testScheduler.advanceUntilIdle()
        assertTrue(viewModel.voicePlayerState.value.isPlaying)

        fakeAdapter.triggerCompletion()
        assertFalse(viewModel.voicePlayerState.value.isPlaying)
        assertNull(viewModel.voicePlayerState.value.currentVoiceName)
        assertTrue(viewModel.voicePlayerState.value.isIdle)

        // 2. Adapter async error
        viewModel.toggleVoicePreview(sampleVoices[0])
        testScheduler.advanceUntilIdle()
        assertTrue(viewModel.voicePlayerState.value.isPlaying)

        val handled = fakeAdapter.triggerError(what = 1, extra = -1004)
        assertTrue(handled)
        assertFalse(viewModel.voicePlayerState.value.isPlaying)
        assertNull(viewModel.voicePlayerState.value.currentVoiceName)
        assertNotNull(viewModel.voicePlayerState.value.error)

        // 3. Clean recovery after error
        viewModel.stopVoicePreview()
        assertTrue(viewModel.voicePlayerState.value.isIdle)

        viewModel.onCleared()
    }

    // =========================================================================
    // Test 7: Lifecycle Teardown and Resource Release
    // =========================================================================
    @Test
    fun testLifecycleReleaseCleansUpResources() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)
        val previewAdapter = FakeMediaPlayerAdapter()
        val mainAdapter = FakeMediaPlayerAdapter()
        val samplePlayer = VoiceSamplePlayer(previewAdapter)
        val sampleManager = VoiceSampleManager()

        val viewModel = TestableViewModel(
            synthesizer = null,
            playerManager = AudioPlayerManager(coroutineScope = testScope, playerAdapter = mainAdapter),
            voiceSampleManager = sampleManager,
            voiceSamplePlayer = samplePlayer,
            ioDispatcher = testDispatcher,
            customScope = testScope
        )

        viewModel.setAvailableVoices(sampleVoices)
        viewModel.toggleVoicePreview(sampleVoices[1])
        testScheduler.advanceUntilIdle()
        assertTrue(viewModel.voicePlayerState.value.isPlaying)

        // Invoke ViewModel onCleared()
        viewModel.onCleared()

        assertFalse(viewModel.voicePlayerState.value.isPlaying)
        assertNull(viewModel.voicePlayerState.value.currentVoiceName)
        assertTrue(previewAdapter.isReleased)
        assertEquals(1, previewAdapter.releaseCallCount)
        assertTrue(mainAdapter.isReleased)
        assertEquals(1, mainAdapter.releaseCallCount)
    }
}
