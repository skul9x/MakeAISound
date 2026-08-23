package skul9x.example.makesound.engine

import android.content.Context

/**
 * Grapheme-to-Phoneme facade forwarding to JNI-bound `com.skul9x.doctruyen.tts.vieneu.SeaG2P`,
 * with full emotion cue preservation matching upstream VieNeu-TTS v3 Turbo.
 */
object SeaG2P {
    val EMOTION_TAG_TO_K = mapOf(
        "cười" to 1, "cuoi" to 1, "chuckle" to 1, "laugh" to 1, "laughter" to 1,
        "thở dài" to 2, "tho dai" to 2, "sigh" to 2,
        "hắng giọng" to 3, "hang giong" to 3, "clear throat" to 3, "clearthroat" to 3
    )

    private val EMOTION_SPLIT_REGEX = Regex("""(\[[^\]]+\]|<\|emotion_\d+\|>)""")
    private val ATTACHING_PUNCT = ".,!?;:…)]}\"'’”".toSet()

    fun isWarmedUp(): Boolean = com.skul9x.doctruyen.tts.vieneu.SeaG2P.isWarmedUp()

    fun clearCache() = com.skul9x.doctruyen.tts.vieneu.SeaG2P.clearCache()

    fun getCacheSize(): Int = com.skul9x.doctruyen.tts.vieneu.SeaG2P.getCacheSize()

    fun init(context: Context? = null) = com.skul9x.doctruyen.tts.vieneu.SeaG2P.init(context)

    fun warmUp(context: Context? = null) = com.skul9x.doctruyen.tts.vieneu.SeaG2P.warmUp(context)

    fun phonemize(text: String): String = com.skul9x.doctruyen.tts.vieneu.SeaG2P.phonemize(text)

    /**
     * Map a raw `[tag]` / `<|emotion_k|>` string to its `<|emotion_k|>` token representation.
     */
    fun getEmotionToken(tag: String): String? {
        val t = tag.trim()
        if (t.startsWith("<|") && t.endsWith("|>")) return t
        if (t.startsWith("[") && t.endsWith("]")) {
            val inner = t.substring(1, t.length - 1).trim().lowercase()
            val k = EMOTION_TAG_TO_K[inner]
            if (k != null) return "<|emotion_$k|>"
        }
        return null
    }

    /**
     * Phonemize text while preserving inline non-verbal emotion cues as special tokens `<|emotion_k|>`.
     *
     * Non-verbal cues ([cười], [thở dài], [hắng giọng], [chuckle], [sigh], [clear throat])
     * are converted to <|emotion_1|>, <|emotion_2|>, <|emotion_3|> tokens in the phoneme stream
     * instead of being phonemized as ordinary spoken words.
     *
     * @param text Raw or segmented text chunk.
     * @param phonemizer Underlying G2P phonemizer lambda (defaults to native [phonemize]).
     * @return Phonemized string with preserved emotion tokens and standardized punctuation.
     */
    fun phonemizeWithEmotions(
        text: String,
        phonemizer: (String) -> String = { phonemize(it) }
    ): String {
        if (!text.contains('[') && !text.contains("<|emotion_")) {
            return phonemizer(text)
        }

        var out = ""
        val matches = EMOTION_SPLIT_REGEX.findAll(text).toList()
        var lastIdx = 0

        for (match in matches) {
            val start = match.range.first
            val end = match.range.last + 1

            if (start > lastIdx) {
                val subText = text.substring(lastIdx, start)
                val ph = if (subText.isNotBlank()) phonemizer(subText) else ""
                if (ph.isNotEmpty()) {
                    if (out.isEmpty()) {
                        out = ph
                    } else if (ph[0] in ATTACHING_PUNCT) {
                        out += ph
                    } else {
                        out += " $ph"
                    }
                }
            }

            val rawTag = match.value
            val token = getEmotionToken(rawTag)
            if (token != null) {
                out = if (out.isEmpty()) token else "$out $token"
            } else {
                val ph = phonemizer(rawTag)
                if (ph.isNotEmpty()) {
                    if (out.isEmpty()) {
                        out = ph
                    } else if (ph[0] in ATTACHING_PUNCT) {
                        out += ph
                    } else {
                        out += " $ph"
                    }
                }
            }

            lastIdx = end
        }

        if (lastIdx < text.length) {
            val subText = text.substring(lastIdx)
            val ph = if (subText.isNotBlank()) phonemizer(subText) else ""
            if (ph.isNotEmpty()) {
                if (out.isEmpty()) {
                    out = ph
                } else if (ph[0] in ATTACHING_PUNCT) {
                    out += ph
                } else {
                    out += " $ph"
                }
            }
        }

        return SmartTextSegmenter.puncNorm(out)
    }
}

