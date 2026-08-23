package skul9x.example.makesound

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import skul9x.example.makesound.engine.SeaG2P
import skul9x.example.makesound.engine.SmartTextSegmenter
import skul9x.example.makesound.engine.VieNeuTokenizer
import java.io.File

/**
 * Phase 3 Comprehensive Emotion Cue G2P Fix Verification Test.
 *
 * Verifies:
 * 1. `SeaG2P.phonemizeWithEmotions` correctly preserves `<|emotion_1|>`, `<|emotion_2|>`, `<|emotion_3|>`
 *    for non-verbal tags ([cười], [thở dài], [hắng giọng]) and English aliases ([chuckle], [sigh], [clear throat]).
 * 2. Phonemization strips brackets and prevents spelling out emotion names as spoken words ("cười" phonemes).
 * 3. Punctuation binding rules (trailing periods, commas attach directly to emotion tokens without rogue spaces).
 * 4. Tokenizer BPE encodes `<|emotion_1|>`, `<|emotion_2|>`, `<|emotion_3|>` directly to Token IDs 9, 10, 11.
 * 5. `SmartTextSegmenter` segmenting & HTML stripping with emotion tokens preserved intact.
 */
class Phase3EmotionCueG2pFixTest {

    private fun resolveProjectDir(relativePath: String): File {
        val direct = File(relativePath)
        if (direct.exists()) return direct
        val nested = File("app", relativePath)
        if (nested.exists()) return nested
        val parent = File("..", relativePath)
        if (parent.exists()) return parent
        return direct
    }

    // Deterministic mock phonemizer simulating SeaG2P phonemize output
    private val mockPhonemizer: (String) -> String = { text ->
        val trimmed = text.trim()
        when {
            trimmed.isEmpty() -> ""
            trimmed == "." -> "."
            trimmed == "," -> ","
            trimmed == "!" -> "!"
            trimmed == "?" -> "?"
            trimmed.startsWith(",") -> ", " + mockPhonemizer(trimmed.substring(1).trim())
            trimmed.startsWith(".") -> ". " + mockPhonemizer(trimmed.substring(1).trim())
            else -> {
                // Split words and map known words
                val words = trimmed.split(Regex("\\s+"))
                val phWords = words.map { word ->
                    when (word.lowercase().trimEnd('.', ',', '!', '?')) {
                        "tôi" -> "t o j"
                        "anh" -> "a n h"
                        "ấy" -> "a j"
                        "nói" -> "n o j"
                        "rồi" -> "r o j"
                        "cười" -> "k ư ờ j" // Phonemes for literal spoken word "cười"
                        "thở" -> "t h ở"
                        "dài" -> "z a j"
                        "hắng" -> "h a n g"
                        "giọng" -> "z o n g"
                        "xin" -> "s i n"
                        "chào" -> "c a w"
                        "được" -> "d u o k"
                        else -> word.lowercase()
                    }
                }
                val trailingPunc = when {
                    trimmed.endsWith("...") -> "..."
                    trimmed.endsWith(".") -> "."
                    trimmed.endsWith(",") -> ","
                    trimmed.endsWith("!") -> "!"
                    trimmed.endsWith("?") -> "?"
                    else -> ""
                }
                phWords.joinToString(" ") + trailingPunc
            }
        }
    }

    @Test
    fun verifyPhase3EmotionCueG2pPreservation() {
        // =========================================================================
        // 1. Verify Emotion Tag Token Resolution
        // =========================================================================
        assertEquals("<|emotion_1|>", SeaG2P.getEmotionToken("[cười]"))
        assertEquals("<|emotion_1|>", SeaG2P.getEmotionToken("[cuoi]"))
        assertEquals("<|emotion_1|>", SeaG2P.getEmotionToken("[chuckle]"))
        assertEquals("<|emotion_1|>", SeaG2P.getEmotionToken("[laugh]"))
        assertEquals("<|emotion_1|>", SeaG2P.getEmotionToken("[laughter]"))
        assertEquals("<|emotion_1|>", SeaG2P.getEmotionToken("<|emotion_1|>"))

        assertEquals("<|emotion_2|>", SeaG2P.getEmotionToken("[thở dài]"))
        assertEquals("<|emotion_2|>", SeaG2P.getEmotionToken("[tho dai]"))
        assertEquals("<|emotion_2|>", SeaG2P.getEmotionToken("[sigh]"))
        assertEquals("<|emotion_2|>", SeaG2P.getEmotionToken("<|emotion_2|>"))

        assertEquals("<|emotion_3|>", SeaG2P.getEmotionToken("[hắng giọng]"))
        assertEquals("<|emotion_3|>", SeaG2P.getEmotionToken("[hang giong]"))
        assertEquals("<|emotion_3|>", SeaG2P.getEmotionToken("[clear throat]"))
        assertEquals("<|emotion_3|>", SeaG2P.getEmotionToken("[clearthroat]"))
        assertEquals("<|emotion_3|>", SeaG2P.getEmotionToken("<|emotion_3|>"))

        // Unrecognized tag returns null
        assertEquals(null, SeaG2P.getEmotionToken("[unknown_tag]"))

        // =========================================================================
        // 2. Verify Phonemization with Emotions (Preserves <|emotion_k|>, No Literal Words)
        // =========================================================================
        // 2.1 Laughter cue: [cười]
        val textWithLaughter = "Anh ấy cười [cười]."
        val phonesLaughter = SeaG2P.phonemizeWithEmotions(textWithLaughter, mockPhonemizer)
        assertTrue("Must contain <|emotion_1|>", phonesLaughter.contains("<|emotion_1|>"))
        // The first "cười" is part of text "Anh ấy cười", but the cue "[cười]" should NOT generate another "k ư ờ j"
        assertTrue("Trailing punctuation must attach to emotion token", phonesLaughter.endsWith("<|emotion_1|>."))

        // 2.2 Pure emotion cue
        val pureCue = "[cười]"
        val phonesPure = SeaG2P.phonemizeWithEmotions(pureCue, mockPhonemizer)
        assertEquals("<|emotion_1|>.", phonesPure)
        assertFalse("Must NOT contain literal phonemes of 'cười'", phonesPure.contains("k ư ờ j"))

        // 2.3 Sigh cue: [thở dài] and [sigh]
        val textWithSigh = "Tôi [thở dài], rồi nói [sigh]."
        val phonesSigh = SeaG2P.phonemizeWithEmotions(textWithSigh, mockPhonemizer)
        assertTrue("Must contain <|emotion_2|>", phonesSigh.contains("<|emotion_2|>"))
        assertTrue("Comma must attach properly to preceding emotion token", phonesSigh.contains("<|emotion_2|>, "))
        assertTrue("Period must attach properly at the end", phonesSigh.endsWith("<|emotion_2|>."))

        // 2.4 Clear throat cue: [hắng giọng] and [clear throat]
        val textWithThroat = "Anh ấy [hắng giọng] trước khi phát biểu [clear throat]."
        val phonesThroat = SeaG2P.phonemizeWithEmotions(textWithThroat, mockPhonemizer)
        assertTrue("Must contain <|emotion_3|>", phonesThroat.contains("<|emotion_3|>"))
        assertTrue("Period must attach properly at the end", phonesThroat.endsWith("<|emotion_3|>."))

        // 2.5 Fast-path: Normal text without emotion cues
        val normalText = "Xin chào các bạn."
        val normalPhones = SeaG2P.phonemizeWithEmotions(normalText, mockPhonemizer)
        assertFalse("Normal text should not contain emotion tokens", normalPhones.contains("<|emotion_"))

        // =========================================================================
        // 3. Verify Tokenizer BPE Encodes Emotion Tokens to Expected IDs (9, 10, 11)
        // =========================================================================
        val assetsDir = resolveProjectDir("src/main/assets/vieneu")
        val tokenizerFile = File(assetsDir, "backbone/tokenizer.json")
        assertTrue("tokenizer.json must exist", tokenizerFile.exists())

        val tokenizer = VieNeuTokenizer.fromFile(tokenizerFile)
        val id1 = tokenizer.getTokenId("<|emotion_1|>")
        val id2 = tokenizer.getTokenId("<|emotion_2|>")
        val id3 = tokenizer.getTokenId("<|emotion_3|>")

        assertNotNull("<|emotion_1|> must be present in tokenizer vocabulary", id1)
        assertNotNull("<|emotion_2|> must be present in tokenizer vocabulary", id2)
        assertNotNull("<|emotion_3|> must be present in tokenizer vocabulary", id3)

        assertEquals("Token ID for <|emotion_1|> must be 9", 9, id1)
        assertEquals("Token ID for <|emotion_2|> must be 10", 10, id2)
        assertEquals("Token ID for <|emotion_3|> must be 11", 11, id3)

        // Verify encode of phonemes string containing emotions
        val encodedIds = tokenizer.encode("s i n  c a w  <|emotion_1|>. t o j  <|emotion_2|>!")
        assertTrue("Encoded IDs must contain Token ID 9", encodedIds.contains(9))
        assertTrue("Encoded IDs must contain Token ID 10", encodedIds.contains(10))

        // =========================================================================
        // 4. Verify SmartTextSegmenter Emotion Preservation & HTML Handling
        // =========================================================================
        val htmlWithEmotions = "<p>Anh ấy cười [cười].</p><p>Tôi thở dài <|emotion_2|>.</p>"
        val structured = SmartTextSegmenter.htmlToStructuredText(htmlWithEmotions)
        assertTrue("HTML cleaning must preserve [cười]", structured.contains("[cười]"))
        assertTrue("HTML cleaning must preserve <|emotion_2|>", structured.contains("<|emotion_2|>"))

        val segmentedChunks = SmartTextSegmenter.segment(htmlWithEmotions)
        assertEquals("Should segment into 2 paragraph chunks", 2, segmentedChunks.size)
        assertTrue("First chunk must contain [cười]", segmentedChunks[0].text.contains("[cười]"))
        assertTrue("Second chunk must contain <|emotion_2|>", segmentedChunks[1].text.contains("<|emotion_2|>"))
    }
}
