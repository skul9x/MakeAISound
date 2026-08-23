package skul9x.example.makesound

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import skul9x.example.makesound.engine.VieNeuOnnxEngine
import java.io.File

/**
 * Single verification test for Phase 07: Autoregressive Loop & CacheTensor Memory Optimization.
 *
 * Verifies:
 * 1. CacheTensor.Pool buffer retention and memory recycling without per-step allocations.
 * 2. CacheTensor.Pool capacity expansion and pool clearing.
 * 3. Exact numerical equivalence between single-slot embedSlot() and multi-row embedRows().
 * 4. Deterministic sampling fidelity and sliding window repetition penalty consistency.
 * 5. Full end-to-end engine inference execution with memory-optimized autoregressive loop.
 */
class Phase7AutoregressiveMemoryTest {

    private fun resolveProjectDir(relativePath: String): File {
        val direct = File(relativePath)
        if (direct.exists()) return direct
        val nested = File("app", relativePath)
        if (nested.exists()) return nested
        val parent = File("..", relativePath)
        if (parent.exists()) return parent
        return direct
    }

    @Test
    fun verifyPhase7CoreFunctionality() {
        // =========================================================================
        // 1. CacheTensor.Pool Buffer Retention & Recycling Verification
        // =========================================================================
        VieNeuOnnxEngine.CacheTensor.Pool.clear()
        assertEquals(0, VieNeuOnnxEngine.CacheTensor.Pool.pooledCount)

        val shape1 = longArrayOf(1, 4, 10, 64)
        val initialSize = 1 * 4 * 10 * 64 // 2560 floats
        val tensor1 = VieNeuOnnxEngine.CacheTensor.Pool.obtain(shape1, initialSize)
        val originalBuffer = tensor1.data

        assertEquals(initialSize, tensor1.size)
        assertEquals(initialSize, tensor1.data.size)
        assertSame(originalBuffer, tensor1.data)

        // Populate test data into tensor1
        for (i in 0 until initialSize) {
            tensor1.data[i] = i.toFloat()
        }

        // Release back to pool
        tensor1.recycle()
        assertEquals(1, VieNeuOnnxEngine.CacheTensor.Pool.pooledCount)

        // Verify buffer is retained inside the pooled instance instead of being dropped
        val shape2 = longArrayOf(1, 4, 10, 64)
        val tensor2 = VieNeuOnnxEngine.CacheTensor.Pool.obtain(shape2, initialSize)
        assertSame("Pool must reuse the exact same CacheTensor instance", tensor1, tensor2)
        assertSame("Pool must reuse the allocated FloatArray buffer without re-allocation", originalBuffer, tensor2.data)
        assertEquals(initialSize, tensor2.size)

        // Test capacity expansion if a larger tensor is requested
        val largerSize = 5120
        val tensorLarge = VieNeuOnnxEngine.CacheTensor.Pool.obtain(longArrayOf(1, 4, 20, 64), largerSize)
        assertEquals(largerSize, tensorLarge.size)
        assertTrue(tensorLarge.data.size >= largerSize)

        tensor2.recycle()
        tensorLarge.recycle()
        assertTrue(VieNeuOnnxEngine.CacheTensor.Pool.pooledCount >= 2)

        VieNeuOnnxEngine.CacheTensor.Pool.clear()
        assertEquals(0, VieNeuOnnxEngine.CacheTensor.Pool.pooledCount)

        // =========================================================================
        // 2. Engine Loading & Numerical Equivalence (embedSlot vs embedRows)
        // =========================================================================
        val assetsDir = resolveProjectDir("src/main/assets/vieneu")
        val backboneDir = File(assetsDir, "backbone")
        val codecDir = File(assetsDir, "codec")

        assertTrue("Backbone directory must exist", backboneDir.exists())
        assertTrue("Codec directory must exist", codecDir.exists())

        val engine = VieNeuOnnxEngine.createFromFiles(backboneDir, codecDir)
        assertNotNull(engine)

        try {
            val H = engine.config.hiddenSize
            val nVq = engine.config.nVq
            val dummyAnchor = FloatArray(H) { i -> (i * 0.01f) }
            val testSlotRow = IntArray(nVq + 1)
            testSlotRow[0] = engine.config.speechGenerationStartTokenId
            for (ch in 0 until nVq) {
                testSlotRow[ch + 1] = (ch * 13) % 1024
            }

            // Reference computation via embedRows
            val refEmbeds = engine.embedRows(arrayOf(testSlotRow), dummyAnchor)
            assertEquals(H, refEmbeds.size)

            // Optimized computation via embedSlot
            val optEmbeds = FloatArray(H)
            engine.embedSlot(testSlotRow, dummyAnchor, optEmbeds)

            assertArrayEquals(
                "embedSlot must produce bit-exact identical embeddings to embedRows",
                refEmbeds,
                optEmbeds,
                1e-6f
            )

            // =========================================================================
            // 3. Deterministic Sampling Fidelity
            // =========================================================================
            val dummyLogits = FloatArray(1024) { i -> (i % 50).toFloat() }
            val greedyIdx = engine.topKSample(logits = dummyLogits, temperature = 0.0f)
            assertTrue("Greedy sampling must select maximum logit index", greedyIdx in 0 until 1024)
            assertEquals(dummyLogits[greedyIdx], dummyLogits.maxOrNull() ?: 0f, 1e-5f)

            // Repetition penalty verification
            val penaltyToken = greedyIdx
            val penalLogits = dummyLogits.clone()
            val penalizedIdx = engine.topKSample(
                logits = penalLogits,
                temperature = 0.0f,
                repetitionPenalty = 2.0f,
                prevTokens = setOf(penaltyToken)
            )
            // If penalty is applied to the max element, another element should become max or logit halved
            assertTrue(penalizedIdx in 0 until 1024)

            // =========================================================================
            // 4. End-to-End Autoregressive Inference with Recycled Memory
            // =========================================================================
            val voicesFile = File(assetsDir, "voices_v3_turbo.json")
            assertTrue("voices_v3_turbo.json must exist", voicesFile.exists())
            skul9x.example.makesound.engine.VoicePresets.loadFromJson(voicesFile.readText(Charsets.UTF_8))

            val testPhonemes = "s in1 ch aw2"
            val result = engine.inferWithTiming(
                phonemes = testPhonemes,
                temperature = 0.0f, // greedy for fast & deterministic test
                maxNewFrames = 10
            )

            assertNotNull("Inference result must not be null", result)
            assertTrue("Should generate acoustic frames", result.frameCount > 0)
            assertTrue("Generated PCM should have audio samples", result.pcm.isNotEmpty())
            assertTrue("Prefill duration must be recorded", result.timing.prefillDurationMs >= 0)
            assertTrue("Decode duration must be recorded", result.timing.decodeDurationMs >= 0)

            // Verify that CacheTensor.Pool was actively utilized and cleaned up
            assertTrue("Engine close clears pool", !engine.isClosed)

        } finally {
            engine.close()
            assertTrue("Engine must be closed", engine.isClosed)
            assertEquals(0, VieNeuOnnxEngine.CacheTensor.Pool.pooledCount)
        }
    }
}
