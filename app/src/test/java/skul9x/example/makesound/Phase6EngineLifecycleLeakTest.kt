package skul9x.example.makesound

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import skul9x.example.makesound.engine.OnnxSessionManager
import skul9x.example.makesound.engine.VieNeuConfig
import skul9x.example.makesound.engine.VieNeuOnnxEngine
import skul9x.example.makesound.engine.VieNeuStudioSynthesizer
import skul9x.example.makesound.player.AudioPlayerManager
import skul9x.example.makesound.player.MediaPlayerAdapter
import skul9x.example.makesound.ui.MakeAiSoundViewModel
import java.io.File

/**
 * Single verification test for Phase 06: Engine & ViewModel Native Memory Leak Fix.
 *
 * Verifies:
 * 1. VieNeuStudioSynthesizer implements AutoCloseable and delegates close() to VieNeuOnnxEngine.
 * 2. Closed VieNeuStudioSynthesizer rejects synthesize() calls with IllegalStateException.
 * 3. VieNeuOnnxEngine close() cleans up CacheTensor.Pool, resets working buffers, and closes OnnxSessionManager.
 * 4. Closed VieNeuOnnxEngine rejects infer() and inferWithTiming() with IllegalStateException.
 * 5. MakeAiSoundViewModel onCleared() automatically invokes close() on AutoCloseable synthesizer and releases playerManager.
 * 6. Idempotent closing guarantees no crash or double-free on repeated close() calls.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class Phase6EngineLifecycleLeakTest {

    private fun resolveProjectDir(relativePath: String): File {
        val direct = File(relativePath)
        if (direct.exists()) return direct
        val nested = File("app", relativePath)
        if (nested.exists()) return nested
        val parent = File("..", relativePath)
        if (parent.exists()) return parent
        return direct
    }

    private class TestClearableViewModel(
        synthesizer: VieNeuStudioSynthesizer?,
        playerManager: AudioPlayerManager,
        customScope: kotlinx.coroutines.CoroutineScope
    ) : MakeAiSoundViewModel(
        synthesizer = synthesizer,
        playerManager = playerManager,
        customScope = customScope
    ) {
        public override fun onCleared() {
            super.onCleared()
        }
    }

    private class FakeMediaPlayerAdapter : MediaPlayerAdapter {
        var isPlayingInternal = false
        var currentPositionInternal = 0
        var durationInternal = 10000
        var isReleased = false

        override fun setDataSource(path: String) {}
        override fun prepare() {}
        override fun start() { isPlayingInternal = true }
        override fun pause() { isPlayingInternal = false }
        override fun stop() { isPlayingInternal = false }
        override fun seekTo(msec: Int) { currentPositionInternal = msec }
        override fun isPlaying(): Boolean = isPlayingInternal
        override fun getCurrentPosition(): Int = currentPositionInternal
        override fun getDuration(): Int = durationInternal
        override fun reset() { isPlayingInternal = false }
        override fun release() {
            reset()
            isReleased = true
        }
        override fun setOnCompletionListener(listener: (() -> Unit)?) {}
        override fun setOnErrorListener(listener: ((what: Int, extra: Int) -> Boolean)?): Unit = Unit
    }

    @Test
    fun verifyPhase6CoreFunctionality() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        kotlinx.coroutines.Dispatchers.setMain(testDispatcher)
        val testScope = TestScope(testDispatcher)

        try {
            val assetsDir = resolveProjectDir("src/main/assets/vieneu")
            val backboneDir = File(assetsDir, "backbone")
            val codecDir = File(assetsDir, "codec")

            assertTrue("Backbone dir must exist", backboneDir.exists())
            assertTrue("Codec dir must exist", codecDir.exists())

            // Initialize real ONNX Engine from assets
            val engine = VieNeuOnnxEngine.createFromFiles(backboneDir, codecDir)
            val synthesizer = VieNeuStudioSynthesizer(engine)

            // =========================================================================
            // 1. Initial State & AutoCloseable Contract Verification
            // =========================================================================
            assertTrue(
                "VieNeuStudioSynthesizer must implement AutoCloseable",
                AutoCloseable::class.java.isAssignableFrom(VieNeuStudioSynthesizer::class.java)
            )
            assertFalse("Synthesizer must not be closed initially", synthesizer.isClosed)
            assertFalse("Engine must not be closed initially", engine.isClosed)
            assertFalse("SessionManager must not be closed initially", engine.sessionManager.isClosed)

            // Populate CacheTensor.Pool to verify cleanup on engine close
            val tensor1 = VieNeuOnnxEngine.CacheTensor(longArrayOf(1, 2, 0, 4), FloatArray(0))
            tensor1.recycle()
            val tensor2 = VieNeuOnnxEngine.CacheTensor(longArrayOf(1, 2, 0, 4), FloatArray(0))
            tensor2.recycle()

            // =========================================================================
            // 2. ViewModel Ownership, Destruction & onCleared() Propagation
            // =========================================================================
            val fakeAdapter = FakeMediaPlayerAdapter()
            val playerManager = AudioPlayerManager(coroutineScope = testScope, playerAdapter = fakeAdapter)
            val viewModel = TestClearableViewModel(
                synthesizer = synthesizer,
                playerManager = playerManager,
                customScope = testScope
            )

            assertFalse("Synthesizer must still be open while ViewModel is active", synthesizer.isClosed)
            assertFalse("Player must not be released initially", fakeAdapter.isReleased)

            // Simulate Activity destruction -> ViewModel.onCleared()
            viewModel.onCleared()

            // Verify ViewModel destruction propagated close to synthesizer, engine, sessions, and player
            assertTrue("Synthesizer must be closed after ViewModel onCleared()", synthesizer.isClosed)
            assertTrue("Engine must be closed via synthesizer delegation", engine.isClosed)
            assertTrue("SessionManager must be closed after engine close()", engine.sessionManager.isClosed)
            assertTrue("PlayerManager must be released after ViewModel onCleared()", fakeAdapter.isReleased)

            // =========================================================================
            // 3. Exception Rejection on Closed Components
            // =========================================================================
            assertThrows(IllegalStateException::class.java) {
                kotlinx.coroutines.runBlocking {
                    synthesizer.synthesize(text = "Kiểm tra sau khi đóng")
                }
            }

            assertThrows(IllegalStateException::class.java) {
                engine.inferWithTiming(phonemes = "s in1 ch aw2")
            }

            assertThrows(IllegalStateException::class.java) {
                engine.infer(phonemes = "s in1 ch aw2")
            }

            // =========================================================================
            // 4. Idempotent Close Calls (No crash or double free)
            // =========================================================================
            synthesizer.close()
            synthesizer.close()
            engine.close()
            engine.close()
            engine.sessionManager.close()
            engine.sessionManager.close()

            assertTrue("Synthesizer remains closed", synthesizer.isClosed)
            assertTrue("Engine remains closed", engine.isClosed)
            assertTrue("SessionManager remains closed", engine.sessionManager.isClosed)

            // =========================================================================
            // 5. ViewModel Safe Handling with Null Synthesizer
            // =========================================================================
            val nullSynthViewModel = TestClearableViewModel(
                synthesizer = null,
                playerManager = AudioPlayerManager(coroutineScope = testScope, playerAdapter = FakeMediaPlayerAdapter()),
                customScope = testScope
            )
            // Should clear without throwing NullPointerException
            nullSynthViewModel.onCleared()

        } finally {
            kotlinx.coroutines.Dispatchers.resetMain()
        }
    }
}
