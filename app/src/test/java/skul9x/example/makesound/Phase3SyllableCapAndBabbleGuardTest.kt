package skul9x.example.makesound

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import skul9x.example.makesound.engine.VieNeuOnnxEngine

/**
 * Verification Test for Phase 03: Syllable-Aware Babble Guard & Engine Robustness.
 *
 * Verifies:
 * 1. Syllable Counting (`VieNeuOnnxEngine.syllableCount`):
 *    - Single-syllable words ("Chào", "Dạ", "Ổn", "Hi", "tʃˈaː2w.") count as 1.
 *    - 2-syllable phrases ("Xin chào", "Cảm ơn", "Tạm biệt", "ˌoʊkˈeɪ.", "kˈə4n tˈə6n.") count as 2.
 *    - 3-syllable phrases ("Tôi hiểu rồi", "Tuyệt vời quá") count as 3.
 *    - 4-syllable phrases ("Chúc bạn ngủ ngon", "Hẹn gặp lại nhé", "sˈækaɪ, ɲˈə6t̪ bˈaː4n.") count as 4.
 *    - Markup tags (<|emotion_\d+|> and <en>...</en>) are ignored.
 *    - Empty or punctuation-only strings count as 1 syllable.
 * 2. Cue-Only Detection (`VieNeuOnnxEngine.isCueOnly`):
 *    - "<|emotion_1|>", "<|emotion_2|> <|emotion_3|>", "<|emotion_1|>." return true.
 *    - "Xin chào <|emotion_1|>", "Chào", "" return false.
 * 3. Frame Cap Verification (`VieNeuOnnxEngine.maxExpectedFrames`):
 *    - 1-syllable input returns exactly 13 frames (SINGLE_WORD_MAX_FRAMES).
 *    - 2-syllable input returns exactly 18 frames (13 + 5 * 1).
 *    - 3-syllable input returns exactly 23 frames (13 + 5 * 2).
 *    - 4-syllable input returns exactly 28 frames (13 + 5 * 3).
 *    - Cue-only inputs return exactly 13 frames.
 *    - Word + cue inputs bypass syllable cap (linear formula > 13).
 *    - Sentences > 4 syllables and long paragraphs return linear cap >= 50 frames.
 * 4. Deterministic computation:
 *    - Repeated calls produce strictly identical frame caps.
 */
class Phase3SyllableCapAndBabbleGuardTest {

    @Test
    fun verifyPhase3SyllableCapAndBabbleGuard() {
        // =========================================================================
        // 0. Constants Verification
        // =========================================================================
        assertEquals("SINGLE_WORD_MAX_FRAMES must be 13", 13, VieNeuOnnxEngine.SINGLE_WORD_MAX_FRAMES)
        assertEquals("SYLLABLE_CAP_PER_EXTRA must be 5", 5, VieNeuOnnxEngine.SYLLABLE_CAP_PER_EXTRA)
        assertEquals("SYLLABLE_CAP_MAX_SYL must be 4", 4, VieNeuOnnxEngine.SYLLABLE_CAP_MAX_SYL)
        assertEquals("SINGLE_WORD_MAX_PHONES must be 24", 24, VieNeuOnnxEngine.SINGLE_WORD_MAX_PHONES)
        assertEquals("MAX_FRAMES_PER_PHONE must be 2.0", 2.0, VieNeuOnnxEngine.MAX_FRAMES_PER_PHONE, 1e-6)
        assertEquals("FRAME_CAP_SLACK must be 24", 24, VieNeuOnnxEngine.FRAME_CAP_SLACK)

        // =========================================================================
        // 1. Syllable Counting (syllableCount)
        // =========================================================================
        // Single syllable words
        val singleSyllables = listOf("Chào", "Dạ", "Ổn", "Hi", "tʃˈaː2w.")
        for (word in singleSyllables) {
            assertEquals("Word '$word' must count as 1 syllable", 1, VieNeuOnnxEngine.syllableCount(word))
        }

        // 2-syllable phrases
        val twoSyllables = listOf("Xin chào", "Cảm ơn", "Tạm biệt", "ˌoʊkˈeɪ.", "kˈə4n tˈə6n.", "ɗˌyə6c xˌoŋ.")
        for (phrase in twoSyllables) {
            assertEquals("Phrase '$phrase' must count as 2 syllables", 2, VieNeuOnnxEngine.syllableCount(phrase))
        }

        // 3-syllable phrases
        val threeSyllables = listOf("Tôi hiểu rồi", "Tuyệt vời quá", "ŋˈɛ hˈaj kwˈaːɜ <|emotion_1|>.")
        for (phrase in threeSyllables) {
            assertEquals("Phrase '$phrase' must count as 3 syllables", 3, VieNeuOnnxEngine.syllableCount(phrase))
        }

        // 4-syllable phrases
        val fourSyllables = listOf("Chúc bạn ngủ ngon", "Hẹn gặp lại nhé", "sˈækaɪ, ɲˈə6t̪ bˈaː4n.")
        for (phrase in fourSyllables) {
            assertEquals("Phrase '$phrase' must count as 4 syllables", 4, VieNeuOnnxEngine.syllableCount(phrase))
        }

        // English multisyllabic words
        assertEquals("English 'create' must count as 2 syllables", 2, VieNeuOnnxEngine.syllableCount("kɹiːˈeɪt"))
        assertEquals("English 'notification' must count as 5 syllables", 5, VieNeuOnnxEngine.syllableCount("nˌoʊɾɪfɪkˈeɪʃən."))

        // Markup tags ignored
        assertEquals("Markup <en> must be ignored", 3, VieNeuOnnxEngine.syllableCount("<en>hello world</en>"))
        assertEquals("Markup <|emotion_1|> must be ignored", 1, VieNeuOnnxEngine.syllableCount("<|emotion_1|> Chào"))

        // Empty and punctuation-only strings count as 1 syllable
        assertEquals("Empty string must count as 1 syllable", 1, VieNeuOnnxEngine.syllableCount(""))
        assertEquals("Null string must count as 1 syllable", 1, VieNeuOnnxEngine.syllableCount(null))
        assertEquals("Punctuation string '...' must count as 1 syllable", 1, VieNeuOnnxEngine.syllableCount("..."))
        assertEquals("Punctuation string '!?,' must count as 1 syllable", 1, VieNeuOnnxEngine.syllableCount("!?,"))
        assertEquals("Whitespace-only string must count as 1 syllable", 1, VieNeuOnnxEngine.syllableCount("   "))

        // =========================================================================
        // 2. Emotion Cue Only Check (isCueOnly)
        // =========================================================================
        assertTrue("Single emotion cue must be cue-only", VieNeuOnnxEngine.isCueOnly("<|emotion_1|>"))
        assertTrue("Multiple emotion cues must be cue-only", VieNeuOnnxEngine.isCueOnly("<|emotion_2|> <|emotion_3|>"))
        assertTrue("Emotion cue with punctuation must be cue-only", VieNeuOnnxEngine.isCueOnly("<|emotion_1|>."))
        assertTrue("Emotion cue with whitespace and punctuation must be cue-only", VieNeuOnnxEngine.isCueOnly("  <|emotion_4|>!  "))

        assertFalse("Text with emotion cue must NOT be cue-only", VieNeuOnnxEngine.isCueOnly("Xin chào <|emotion_1|>"))
        assertFalse("Pure text must NOT be cue-only", VieNeuOnnxEngine.isCueOnly("Xin chào"))
        assertFalse("Single word must NOT be cue-only", VieNeuOnnxEngine.isCueOnly("Chào"))
        assertFalse("Empty string must NOT be cue-only", VieNeuOnnxEngine.isCueOnly(""))
        assertFalse("Null string must NOT be cue-only", VieNeuOnnxEngine.isCueOnly(null))

        // =========================================================================
        // 3. Syllable-Aware Frame Cap Verification (maxExpectedFrames)
        // =========================================================================
        // 1-syllable inputs: exactly 13 frames
        assertEquals("1-syllable 'Chào' cap must be exactly 13 frames", 13, VieNeuOnnxEngine.maxExpectedFrames("Chào"))
        assertEquals("1-syllable 'Dạ' cap must be exactly 13 frames", 13, VieNeuOnnxEngine.maxExpectedFrames("Dạ"))
        assertEquals("1-syllable 'Ổn' cap must be exactly 13 frames", 13, VieNeuOnnxEngine.maxExpectedFrames("Ổn"))
        assertEquals("1-syllable 'Hi' cap must be exactly 13 frames", 13, VieNeuOnnxEngine.maxExpectedFrames("Hi"))
        assertEquals("1-syllable phonemes 'tʃˈaː2w.' cap must be exactly 13 frames", 13, VieNeuOnnxEngine.maxExpectedFrames("tʃˈaː2w."))
        assertEquals("Empty string cap must be exactly 13 frames", 13, VieNeuOnnxEngine.maxExpectedFrames(""))
        assertEquals("Null string cap must be exactly 13 frames", 13, VieNeuOnnxEngine.maxExpectedFrames(null))

        // 2-syllable inputs: exactly 18 frames (13 + 5)
        assertEquals("2-syllable 'Xin chào' cap must be exactly 18 frames", 18, VieNeuOnnxEngine.maxExpectedFrames("Xin chào"))
        assertEquals("2-syllable 'Cảm ơn' cap must be exactly 18 frames", 18, VieNeuOnnxEngine.maxExpectedFrames("Cảm ơn"))
        assertEquals("2-syllable 'Tạm biệt' cap must be exactly 18 frames", 18, VieNeuOnnxEngine.maxExpectedFrames("Tạm biệt"))
        assertEquals("2-syllable phonemes 'kˈə4n tˈə6n.' cap must be exactly 18 frames", 18, VieNeuOnnxEngine.maxExpectedFrames("kˈə4n tˈə6n."))
        assertEquals("2-syllable English 'ˌoʊkˈeɪ.' cap must be exactly 18 frames", 18, VieNeuOnnxEngine.maxExpectedFrames("ˌoʊkˈeɪ."))

        // 3-syllable inputs: exactly 23 frames (13 + 10)
        assertEquals("3-syllable 'Tôi hiểu rồi' cap must be exactly 23 frames", 23, VieNeuOnnxEngine.maxExpectedFrames("Tôi hiểu rồi"))
        assertEquals("3-syllable 'Tuyệt vời quá' cap must be exactly 23 frames", 23, VieNeuOnnxEngine.maxExpectedFrames("Tuyệt vời quá"))

        // 4-syllable inputs: exactly 28 frames (13 + 15)
        assertEquals("4-syllable 'Chúc bạn ngủ ngon' cap must be exactly 28 frames", 28, VieNeuOnnxEngine.maxExpectedFrames("Chúc bạn ngủ ngon"))
        assertEquals("4-syllable 'Hẹn gặp lại nhé' cap must be exactly 28 frames", 28, VieNeuOnnxEngine.maxExpectedFrames("Hẹn gặp lại nhé"))
        assertEquals("4-syllable phonemes 'sˈækaɪ, ɲˈə6t̪ bˈaː4n.' cap must be exactly 28 frames", 28, VieNeuOnnxEngine.maxExpectedFrames("sˈækaɪ, ɲˈə6t̪ bˈaː4n."))

        // Cue-only inputs: exactly 13 frames (~1.0s cap)
        assertEquals("Cue-only '<|emotion_1|>' cap must be 13 frames", 13, VieNeuOnnxEngine.maxExpectedFrames("<|emotion_1|>"))
        assertEquals("Cue-only '<|emotion_2|>' cap must be 13 frames", 13, VieNeuOnnxEngine.maxExpectedFrames("<|emotion_2|>"))
        assertEquals("Cue-only '<|emotion_1|>.' cap must be 13 frames", 13, VieNeuOnnxEngine.maxExpectedFrames("<|emotion_1|>."))
        assertEquals("Cue-only '<|emotion_2|> <|emotion_3|>' cap must be 13 frames", 13, VieNeuOnnxEngine.maxExpectedFrames("<|emotion_2|> <|emotion_3|>"))

        // Word + emotion cues: bypass syllable cap (generates real speech + laughter/breath)
        assertTrue("Word + cue must bypass syllable cap", VieNeuOnnxEngine.maxExpectedFrames("tʃˈaː2w <|emotion_1|>") > 13)
        assertTrue("Word + cue with punctuation must bypass syllable cap", VieNeuOnnxEngine.maxExpectedFrames("tʃˈaː2w <|emotion_1|>.") > 13)
        assertTrue("2-syllables + cue must bypass 18-frame cap", VieNeuOnnxEngine.maxExpectedFrames("Xin chào <|emotion_1|>") > 18)

        // > 4 syllables: standard linear formula cap
        val fiveSyllableWord = "nˌoʊɾɪfɪkˈeɪʃən."
        val expectedLinear = VieNeuOnnxEngine.FRAME_CAP_SLACK + Math.ceil(VieNeuOnnxEngine.MAX_FRAMES_PER_PHONE * fiveSyllableWord.length).toInt()
        assertEquals("5-syllable word must use linear cap", expectedLinear, VieNeuOnnxEngine.maxExpectedFrames(fiveSyllableWord))
        assertTrue("5-syllable word cap must be > 28 frames", VieNeuOnnxEngine.maxExpectedFrames(fiveSyllableWord) > 28)

        // Long paragraph input: linear cap >= 50 frames
        val longParagraph = "Đây là một đoạn văn bản rất dài được sử dụng để kiểm tra giới hạn frame tuyến tính của mô hình VieNeuOnnxEngine xem có vượt quá năm mươi frame hay không theo đúng chuẩn của VieNeu-TTS v3.8.1."
        val longCap = VieNeuOnnxEngine.maxExpectedFrames(longParagraph)
        assertTrue("Long paragraph cap ($longCap) must be >= 50 frames", longCap >= 50)

        // Markup tags in English text
        assertEquals(
            "maxExpectedFrames must ignore markup tags",
            VieNeuOnnxEngine.maxExpectedFrames("abc def"),
            VieNeuOnnxEngine.maxExpectedFrames("<en>abc def</en>")
        )

        // Abnormally long single word (> 24 chars) falls back to linear cap
        val abnormallyLongWord = "a".repeat(30)
        assertTrue("Abnormally long word must exceed SINGLE_WORD_MAX_FRAMES", VieNeuOnnxEngine.maxExpectedFrames(abnormallyLongWord) > 13)

        // =========================================================================
        // 4. Deterministic Calculation Verification
        // =========================================================================
        val testInputs = listOf(
            "Chào",
            "Xin chào",
            "Tôi hiểu rồi",
            "Chúc bạn ngủ ngon",
            "<|emotion_1|>",
            longParagraph
        )
        for (input in testInputs) {
            val first = VieNeuOnnxEngine.maxExpectedFrames(input)
            val second = VieNeuOnnxEngine.maxExpectedFrames(input)
            val third = VieNeuOnnxEngine.maxExpectedFrames(input)
            assertEquals("Cap calculation for '$input' must be deterministic", first, second)
            assertEquals("Cap calculation for '$input' must be deterministic", first, third)
        }
    }
}
