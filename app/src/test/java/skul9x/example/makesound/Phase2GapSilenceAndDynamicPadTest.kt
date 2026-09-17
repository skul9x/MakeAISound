package skul9x.example.makesound

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import skul9x.example.makesound.engine.PauseType
import skul9x.example.makesound.engine.PcmUtils
import skul9x.example.makesound.engine.SmartTextSegmenter
import skul9x.example.makesound.engine.VieNeuConfig
import kotlin.math.PI
import kotlin.math.sin

/**
 * Verification Test for Phase 02: V3 Gap Silence Calibration & Dynamic Silence Padding.
 *
 * Verifies:
 * 1. Pause Durations: SmartTextSegmenter.segment() produces 700ms for paragraphs, 500ms for sentences, and 300ms for clauses.
 * 2. Edge Silence Detection:
 *    - Synthetic buffer with 100ms leading silence + tone + 200ms trailing silence correctly measures ~100ms lead and ~200ms tail.
 *    - All-silence buffer returns full length.
 *    - Empty buffer returns (0, 0).
 * 3. Dynamic Padding Computation:
 *    - When tail + lead is 300ms and target is 500ms, pausePadSamples returns exactly 200ms worth of samples.
 *    - When tail + lead exceeds target (e.g. 600ms vs 500ms), pausePadSamples returns 0.
 *    - All-silence previous chunk counts entire duration as trailing silence.
 * 4. Synthesis Joining:
 *    - End-to-end joining of multi-chunk audio results in clean concatenation without discontinuity or double-gap padding.
 */
class Phase2GapSilenceAndDynamicPadTest {

    private fun generateTone(
        durationMs: Int,
        freqHz: Float = 440.0f,
        amplitude: Float = 0.5f,
        sampleRate: Int = VieNeuConfig.SAMPLE_RATE
    ): ShortArray {
        val count = ((durationMs.toLong() * sampleRate) / 1000L).toInt()
        val pcm = ShortArray(count)
        val angularFreq = 2.0 * PI * freqHz / sampleRate
        for (i in 0 until count) {
            val sample = sin(angularFreq * i) * amplitude
            pcm[i] = (sample * 32767.0).toInt().coerceIn(-32768, 32767).toShort()
        }
        return pcm
    }

    private fun concatenate(vararg arrays: ShortArray): ShortArray {
        val totalSize = arrays.sumOf { it.size }
        val result = ShortArray(totalSize)
        var offset = 0
        for (arr in arrays) {
            System.arraycopy(arr, 0, result, offset, arr.size)
            offset += arr.size
        }
        return result
    }

    @Test
    fun verifyPhase2GapSilenceAndDynamicPadding() {
        val sampleRate = VieNeuConfig.SAMPLE_RATE // 48000 Hz

        // =========================================================================
        // 1. Pause Durations in SmartTextSegmenter
        // =========================================================================
        assertEquals("PARAGRAPH_BREAK default duration must be 700ms", 700, PauseType.PARAGRAPH_BREAK.defaultDurationMs)
        assertEquals("SENTENCE_BREAK default duration must be 500ms", 500, PauseType.SENTENCE_BREAK.defaultDurationMs)
        assertEquals("CLAUSE_BREAK default duration must be 300ms", 300, PauseType.CLAUSE_BREAK.defaultDurationMs)

        // Test Paragraph break segmentation
        val paraText = "Đoạn văn thứ nhất kết thúc tại đây.\n\nĐoạn văn thứ hai bắt đầu một câu chuyện mới."
        val paraChunks = SmartTextSegmenter.segment(paraText)
        assertTrue("Must segment into at least 2 chunks", paraChunks.size >= 2)
        assertEquals(PauseType.PARAGRAPH_BREAK, paraChunks[0].pauseType)
        assertEquals(700, paraChunks[0].pauseDurationMs)

        // Test Sentence break segmentation
        val sentText = "Đây là câu đầu tiên. Còn đây là câu thứ hai."
        val sentChunks = SmartTextSegmenter.segment(sentText)
        assertTrue("Must segment into 2 sentences", sentChunks.size >= 2)
        assertEquals(PauseType.SENTENCE_BREAK, sentChunks[0].pauseType)
        assertEquals(500, sentChunks[0].pauseDurationMs)

        // Test Intra-sentence clause break segmentation
        val clauseText = "Hôm nay trời nắng đẹp, gió thổi nhè nhẹ trên cánh đồng."
        val clauseChunks = SmartTextSegmenter.segment(clauseText, maxCharsPerChunk = 30)
        assertTrue("Must segment long sentence into clauses", clauseChunks.size >= 2)
        assertEquals(PauseType.CLAUSE_BREAK, clauseChunks[0].pauseType)
        assertEquals(300, clauseChunks[0].pauseDurationMs)

        // =========================================================================
        // 2. Edge Silence Detection (edgeSilence in PcmUtils)
        // =========================================================================
        val leadSilence = ShortArray(((100L * sampleRate) / 1000L).toInt()) // 100ms silence (4800 samples)
        val tone = generateTone(durationMs = 200, freqHz = 440f, amplitude = 0.5f, sampleRate = sampleRate) // 200ms tone
        val tailSilence = ShortArray(((200L * sampleRate) / 1000L).toInt()) // 200ms silence (9600 samples)
        val syntheticWave = concatenate(leadSilence, tone, tailSilence)

        val (measuredLead, measuredTail) = PcmUtils.edgeSilence(syntheticWave, sampleRate = sampleRate)

        // 10ms window = 480 samples tolerance
        val winSamples = (0.010f * sampleRate).toInt()
        val expectedLeadSamples = leadSilence.size
        val expectedTailSamples = tailSilence.size

        assertTrue(
            "Measured leading silence ($measuredLead) should be ~100ms ($expectedLeadSamples samples)",
            Math.abs(measuredLead - expectedLeadSamples) <= winSamples
        )
        assertTrue(
            "Measured trailing silence ($measuredTail) should be ~200ms ($expectedTailSamples samples)",
            Math.abs(measuredTail - expectedTailSamples) <= winSamples
        )

        // All-silence buffer returns full length as leading silence
        val allSilenceBuffer = ShortArray(sampleRate) // 1000ms silence
        val (allSilenceLead, allSilenceTail) = PcmUtils.edgeSilence(allSilenceBuffer, sampleRate = sampleRate)
        assertEquals("All-silence buffer must return full length", allSilenceBuffer.size, allSilenceLead)
        assertEquals("All-silence buffer trailing silence should be 0", 0, allSilenceTail)

        // Empty buffer returns (0, 0)
        val (emptyLead, emptyTail) = PcmUtils.edgeSilence(ShortArray(0), sampleRate = sampleRate)
        assertEquals(0, emptyLead)
        assertEquals(0, emptyTail)

        // =========================================================================
        // 3. Dynamic Padding Computation (pausePadSamples in PcmUtils)
        // =========================================================================
        // Case A: Tail (200ms) + Lead (100ms) = 300ms, target = 500ms
        // Expected padding: 500ms - 300ms = 200ms worth of samples = (200 * 48000) / 1000 = 9600 samples
        val prevChunkWith200msTail = concatenate(tone, tailSilence) // tail = 200ms
        val nextChunkWith100msLead = concatenate(leadSilence, tone) // lead = 100ms
        val targetPauseMs = 500
        val padSamples = PcmUtils.pausePadSamples(
            prevChunk = prevChunkWith200msTail,
            nextChunk = nextChunkWith100msLead,
            pauseMs = targetPauseMs,
            sampleRate = sampleRate
        )
        val expectedPadSamples = ((200L * sampleRate) / 1000L).toInt()
        assertEquals("Dynamic padding should supply exactly missing 200ms (9600 samples)", expectedPadSamples, padSamples)

        // Case B: Tail (400ms) + Lead (200ms) = 600ms, target = 500ms -> exceeds target
        val tailSilence400ms = ShortArray(((400L * sampleRate) / 1000L).toInt())
        val prevChunkWith400msTail = concatenate(tone, tailSilence400ms)
        val nextChunkWith200msLead = concatenate(tailSilence, tone)
        val padExceeded = PcmUtils.pausePadSamples(
            prevChunk = prevChunkWith400msTail,
            nextChunk = nextChunkWith200msLead,
            pauseMs = targetPauseMs,
            sampleRate = sampleRate
        )
        assertEquals("Dynamic padding should be 0 when existing silence exceeds target", 0, padExceeded)

        // Case C: Previous chunk is completely silent (300ms), next chunk has 100ms lead, target = 500ms
        val silentChunk300ms = ShortArray(((300L * sampleRate) / 1000L).toInt())
        val padFromSilentPrev = PcmUtils.pausePadSamples(
            prevChunk = silentChunk300ms,
            nextChunk = nextChunkWith100msLead,
            pauseMs = targetPauseMs,
            sampleRate = sampleRate
        )
        // tail (300ms) + lead (100ms) = 400ms; target (500ms) -> 100ms missing
        val expected100msPad = ((100L * sampleRate) / 1000L).toInt()
        assertEquals("All-silent prev chunk should count entire length as tail silence", expected100msPad, padFromSilentPrev)

        // =========================================================================
        // 4. Synthesis Joining (joinAudioChunks in PcmUtils)
        // =========================================================================
        val chunk1 = prevChunkWith200msTail
        val chunk2 = nextChunkWith100msLead
        val joined = PcmUtils.joinAudioChunks(
            chunks = listOf(chunk1, chunk2),
            gaps = listOf("sentence"),
            sampleRate = sampleRate
        )

        // Joined size must equal chunk1.size + padSamples + chunk2.size
        val expectedTotalLength = chunk1.size + padSamples + chunk2.size
        assertEquals("Joined audio size must match dynamic padded size", expectedTotalLength, joined.size)

        // Verify content continuity
        // First chunk samples match chunk1
        for (i in 0 until 1000) {
            assertEquals("Chunk 1 samples must be intact", chunk1[i], joined[i])
        }

        // Padding zone must be all zeroes
        val padStart = chunk1.size
        for (i in 0 until padSamples) {
            assertEquals("Padding samples must be pure zeroes", 0.toShort(), joined[padStart + i])
        }

        // Second chunk samples match chunk2
        val chunk2Start = chunk1.size + padSamples
        for (i in 0 until 1000) {
            assertEquals("Chunk 2 samples must be intact", chunk2[i], joined[chunk2Start + i])
        }
    }
}
