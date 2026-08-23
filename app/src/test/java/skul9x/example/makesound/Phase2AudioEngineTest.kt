package skul9x.example.makesound

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import skul9x.example.makesound.engine.ChannelWindow
import skul9x.example.makesound.engine.PauseType
import skul9x.example.makesound.engine.PcmUtils
import skul9x.example.makesound.engine.RepetitionHistory
import skul9x.example.makesound.engine.SmartTextSegmenter
import skul9x.example.makesound.engine.VieNeuConfig
import skul9x.example.makesound.engine.VieNeuTokenizer
import skul9x.example.makesound.engine.VoicePresets
import skul9x.example.makesound.telemetry.ChunkPerformanceMetrics
import skul9x.example.makesound.telemetry.LogLevel
import skul9x.example.makesound.telemetry.PerformanceMetricsTracker
import skul9x.example.makesound.telemetry.StudioLogger
import java.io.File

/**
 * Phase 2 Comprehensive FP32 VieNeu AI Engine & Performance Telemetry Verification Test.
 *
 * Verifies:
 * 1. Smart linguistic text segmentation of Vietnamese sentences with abbreviations (`TP.HCM`, `BS.`, `1.500.000đ`, timestamps)
 *    and atomic `<en>English phrase</en>` preservation.
 * 2. `RepetitionHistory` sliding-window eviction behavior at window boundary 64.
 * 3. Emotion tag extraction (`[cười]`, `[thở dài]`, `[hắng giọng]`, `[chuckle]`, `[sigh]`) and tokenizer mapping.
 * 4. Voice preset loading and speaker embedding vector dimensions (192-d) across all 8 preset voices.
 * 5. PCM micro-fading (5ms linear ramp), float-to-short scaling, and calibrated V3 gap silence generation.
 * 6. `PerformanceMetricsTracker` telemetry calculations (RTF, stage latencies) and `StudioLogger` circular buffer / formatted export.
 */
class Phase2AudioEngineTest {

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
    fun verifyPhase2CoreFunctionality() {
        // =========================================================================
        // 1. Verify Smart Linguistic Segmentation & English Word Preservation (<en>)
        // =========================================================================
        val sampleText = "BS. Nguyễn Văn A tại TP.HCM đã khám lúc 08:30 sáng. Giá thuốc là 1.500.000đ. Anh ấy nói: <en>Good morning world</en> rất chuẩn."
        val chunks = SmartTextSegmenter.segment(sampleText, maxCharsPerChunk = 120)

        assertTrue("Should segment into chunks", chunks.isNotEmpty())
        val combinedText = chunks.joinToString(" ") { it.text }

        // Assert abbreviations preserved intact
        assertTrue("TP.HCM abbreviation must be preserved", combinedText.contains("TP.HCM"))
        assertTrue("BS. honorific must be preserved", combinedText.contains("BS."))
        assertTrue("1.500.000đ numeral must be preserved", combinedText.contains("1.500.000đ") || combinedText.contains("1.500.000"))
        assertTrue("Timestamp 08:30 must be preserved", combinedText.contains("08:30"))
        assertTrue("Embedded <en>...</en> tag must be preserved atomically", combinedText.contains("<en>Good morning world</en>"))

        // Verify quote/bracket depth-aware sentence scanning
        val dialogueText = "Có phải kiểu như: \"Rồi sao nữa? Mình phải làm đến bao giờ?\", đúng không anh?"
        val dialogueSentences = SmartTextSegmenter.splitIntoSentences(dialogueText)
        assertEquals("Dialogue inside quotes should not split into multiple sentences", 1, dialogueSentences.size)

        // Verify terminal punctuation normalization (puncNorm)
        val shortPhrase = "Xin chào"
        assertEquals("Short phrase (<5 words) should end with period", "Xin chào.", SmartTextSegmenter.puncNorm(shortPhrase))

        // =========================================================================
        // 2. Verify Sliding-Window Repetition Penalty (RepetitionHistory window = 64)
        // =========================================================================
        val repHistory = RepetitionHistory(nChannels = 16, window = 64)
        val channel0 = repHistory[0]

        // Add 64 distinct codes (0 until 64)
        for (i in 0 until 64) {
            channel0.add(i)
        }
        assertEquals("Channel window should contain 64 unique codes", 64, channel0.uniqueCount)
        assertEquals("Window size should be 64", 64, channel0.windowCount)
        assertTrue("Code 0 should be present", channel0.contains(0))
        assertTrue("Code 63 should be present", channel0.contains(63))

        // Add 65th code (64) -> code 0 must be evicted
        channel0.add(64)
        assertEquals("Window count should remain capped at 64", 64, channel0.windowCount)
        assertEquals("Unique count should remain 64", 64, channel0.uniqueCount)
        assertFalse("Code 0 must be evicted after 65th insertion", channel0.contains(0))
        assertTrue("Code 1 should now be oldest code present", channel0.contains(1))
        assertTrue("Newly added code 64 should be present", channel0.contains(64))

        // Test duplicate code eviction behavior: Add duplicate code 64
        channel0.add(64)
        // Now code 1 is evicted, code 64 has count 2
        assertFalse("Code 1 must be evicted", channel0.contains(1))
        assertEquals("Code 64 should have count 2", 2, channel0.getCount(64))

        // =========================================================================
        // 3. Verify Emotion Tag Extraction & Tokenizer Mapping
        // =========================================================================
        val assetsDir = resolveProjectDir("src/main/assets/vieneu")
        val tokenizerFile = File(assetsDir, "backbone/tokenizer.json")
        assertTrue("tokenizer.json must exist", tokenizerFile.exists())

        val tokenizer = VieNeuTokenizer.fromFile(tokenizerFile)
        assertNotNull("Tokenizer instance should be created", tokenizer)
        assertTrue("Tokenizer vocab size should be >= 400", tokenizer.vocabSize >= 400)

        // Verify emotion tag resolution
        val textWithCues = "Anh ấy cười [cười] rồi thở dài [thở dài] và hắng giọng [hắng giọng]."
        val resolvedCues = VieNeuTokenizer.resolveEmotionTags(textWithCues)
        assertTrue("Should contain <|emotion_1|>", resolvedCues.contains("<|emotion_1|>"))
        assertTrue("Should contain <|emotion_2|>", resolvedCues.contains("<|emotion_2|>"))
        assertTrue("Should contain <|emotion_3|>", resolvedCues.contains("<|emotion_3|>"))

        // Verify English emotion cue synonyms
        assertEquals("<|emotion_1|>", VieNeuTokenizer.getEmotionToken("[chuckle]"))
        assertEquals("<|emotion_2|>", VieNeuTokenizer.getEmotionToken("[sigh]"))
        assertEquals("<|emotion_3|>", VieNeuTokenizer.getEmotionToken("[clear throat]"))

        // Verify Tokenizer BPE encoding handles emotion tokens
        val tokenIds = tokenizer.encode("<|emotion_1|> s in1 ch aw2 <|emotion_2|>")
        assertTrue("Encoded token IDs must not be empty", tokenIds.isNotEmpty())
        val emotion1Id = tokenizer.getTokenId("<|emotion_1|>")
        val emotion2Id = tokenizer.getTokenId("<|emotion_2|>")
        assertNotNull("<|emotion_1|> must exist in tokenizer vocab", emotion1Id)
        assertNotNull("<|emotion_2|> must exist in tokenizer vocab", emotion2Id)
        assertTrue("Token IDs must contain emotion1 token ID", tokenIds.contains(emotion1Id!!))
        assertTrue("Token IDs must contain emotion2 token ID", tokenIds.contains(emotion2Id!!))

        // =========================================================================
        // 4. Verify Voice Presets Loading & 8 Preset Voices
        // =========================================================================
        val voicesFile = File(assetsDir, "voices_v3_turbo.json")
        assertTrue("voices_v3_turbo.json must exist", voicesFile.exists())

        VoicePresets.loadFromJson(voicesFile.readText(Charsets.UTF_8))
        val allVoices = VoicePresets.availableVoices
        assertTrue("Voice presets must have at least 8 voices", allVoices.size >= 8)

        val expected8Voices = listOf(
            "Trúc Ly", "Minh Đức", "Phạm Tuyên", "Thái Sơn",
            "Xuân Vĩnh", "Thanh Bình", "Ngọc Linh", "Đoan Trang"
        )
        for (vName in expected8Voices) {
            val preset = VoicePresets.getVoice(vName)
            assertNotNull("Voice preset '$vName' must be loaded", preset)
            assertEquals("Speaker embedding dimension must be 192", 192, preset!!.speakerEmb.size)
            assertTrue("Voice '$vName' gender display must not be blank", preset.genderDisplay.isNotBlank())
            assertTrue("Voice '$vName' formatted display name must not be blank", preset.getFormattedDisplayName().isNotBlank())
        }

        // =========================================================================
        // 5. Verify PCM Micro-Fading, Scaling & Calibrated Gap Silences
        // =========================================================================
        // Float to Short conversion
        val floatSamples = floatArrayOf(-1.0f, -0.5f, 0.0f, 0.5f, 1.0f)
        val shortSamples = PcmUtils.floatToShort(floatSamples)
        assertEquals((-32767).toShort(), shortSamples[0])
        assertEquals((0).toShort(), shortSamples[2])
        assertEquals((32767).toShort(), shortSamples[4])

        // Short to Float conversion
        val roundtripFloat = PcmUtils.shortToFloat(shortSamples)
        assertEquals(-1.0f, roundtripFloat[0], 0.001f)
        assertEquals(0.0f, roundtripFloat[2], 0.001f)
        assertEquals(1.0f, roundtripFloat[4], 0.001f)

        // Calibrated gap silence verification
        val paraSilence = PcmUtils.generateSilence(PcmUtils.V3_GAP_SILENCE_MS["para"]!!, VieNeuConfig.SAMPLE_RATE)
        val sentenceSilence = PcmUtils.generateSilence(PcmUtils.V3_GAP_SILENCE_MS["sentence"]!!, VieNeuConfig.SAMPLE_RATE)
        val minorSilence = PcmUtils.generateSilence(PcmUtils.V3_GAP_SILENCE_MS["minor"]!!, VieNeuConfig.SAMPLE_RATE)

        assertEquals("Paragraph gap silence @ 48kHz (350ms) = 16800 samples", 16800, paraSilence.size)
        assertEquals("Sentence gap silence @ 48kHz (180ms) = 8640 samples", 8640, sentenceSilence.size)
        assertEquals("Minor clause gap silence @ 48kHz (40ms) = 1920 samples", 1920, minorSilence.size)

        // Micro-fading (5ms linear ramp)
        val dummyAudio = ShortArray(4800) { 10000.toShort() } // 100ms at 48kHz
        PcmUtils.applyMicroFade(dummyAudio, fadeDurationMs = 5.0f, sampleRate = 48000)
        assertEquals("Start sample after micro fade-in must be 0", (0).toShort(), dummyAudio[0])
        assertTrue("Middle sample should remain unaffected", dummyAudio[2400] == (10000).toShort())
        assertTrue("End sample after micro fade-out must be near 0", dummyAudio.last() < 100)

        // =========================================================================
        // 6. Verify PerformanceMetricsTracker & StudioLogger
        // =========================================================================
        StudioLogger.clear()
        StudioLogger.i("TestRunner", "Starting verification of telemetry logger")
        StudioLogger.d("Engine", "Prefill completed in 15ms", stage = "Prefill")
        StudioLogger.w("Engine", "Approaching max frame bounds", stage = "Decode")

        val recentLogs = StudioLogger.getRecentLogs()
        assertEquals("Should contain 3 log entries", 3, recentLogs.size)
        val exportedLogs = StudioLogger.exportLogsToString()
        assertTrue("Exported logs must contain tag and message", exportedLogs.contains("[Engine] [Prefill] Prefill completed in 15ms"))

        PerformanceMetricsTracker.startSession("TestSession_01", "Trúc Ly")
        val sampleMetric1 = ChunkPerformanceMetrics(
            chunkIndex = 0,
            totalChunks = 2,
            textSnippet = "Xin chào Việt Nam",
            charCount = 17,
            phonemeCount = 20,
            g2pDurationMs = 8L,
            prefillDurationMs = 12L,
            decodeDurationMs = 90L,
            codecDurationMs = 25L,
            totalSynthesisDurationMs = 135L,
            audioDurationMs = 1500L,
            rtf = 135f / 1500f,
            peakMemoryMb = 128.5
        )
        val sampleMetric2 = ChunkPerformanceMetrics(
            chunkIndex = 1,
            totalChunks = 2,
            textSnippet = "Chào mừng bạn đến với VieNeu",
            charCount = 28,
            phonemeCount = 32,
            g2pDurationMs = 10L,
            prefillDurationMs = 14L,
            decodeDurationMs = 110L,
            codecDurationMs = 30L,
            totalSynthesisDurationMs = 164L,
            audioDurationMs = 2000L,
            rtf = 164f / 2000f,
            peakMemoryMb = 132.0
        )
        PerformanceMetricsTracker.recordChunk(sampleMetric1)
        PerformanceMetricsTracker.recordChunk(sampleMetric2)

        val sessionSummary = PerformanceMetricsTracker.finishSession()
        assertEquals("Total characters must be 45", 45, sessionSummary.totalChars)
        assertEquals("Total phonemes must be 52", 52, sessionSummary.totalPhonemes)
        assertEquals("Total synthesis duration must be 299 ms", 299L, sessionSummary.totalSynthesisDurationMs)
        assertEquals("Total audio duration must be 3500 ms", 3500L, sessionSummary.totalAudioDurationMs)
        assertTrue("Average RTF must be calculated correctly (~0.085x)", sessionSummary.averageRtf < 0.15f)

        val formattedSummary = sessionSummary.formatSummary()
        assertTrue("Formatted summary must contain session details", formattedSummary.contains("TestSession_01"))
        assertTrue("Formatted summary must contain voice name", formattedSummary.contains("Trúc Ly"))
    }
}
