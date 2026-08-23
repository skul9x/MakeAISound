package skul9x.example.makesound.engine

import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.text.Normalizer
import java.util.regex.Pattern

/**
 * Byte-level BPE tokenizer matching HuggingFace Tokenizers for VieNeu-TTS.
 * Includes inline emotion cue resolution and special token handling.
 */
class VieNeuTokenizer(
    private val vocab: Map<String, Int>,
    private val bpeRanks: Map<Pair<String, String>, Int>,
    private val unkTokenId: Int = 43
) {
    val vocabSize: Int get() = vocab.size

    fun getTokenId(token: String): Int? = vocab[token]

    /**
     * Encode phoneme sequence or text string into BPE token IDs.
     * Automatically resolves inline emotion tags like `[cười]`, `[thở dài]`, `[hắng giọng]`
     * into `<|emotion_1|>`, `<|emotion_2|>`, `<|emotion_3|>` tokens before byte-level BPE tokenization.
     */
    fun encode(text: String): IntArray {
        if (text.isEmpty()) return intArrayOf()

        val textWithEmotions = resolveEmotionTags(text)
        val tokenIds = ArrayList<Int>()

        // Split text by special tokens (<|...|>) and normal text chunks
        val specialPattern = Pattern.compile("<\\|[a-zA-Z0-9_]+\\|>")
        val specialMatcher = specialPattern.matcher(textWithEmotions)
        var lastIdx = 0

        while (specialMatcher.find()) {
            val start = specialMatcher.start()
            val end = specialMatcher.end()
            if (start > lastIdx) {
                val subText = textWithEmotions.substring(lastIdx, start)
                encodeSubText(subText, tokenIds)
            }
            val specialToken = specialMatcher.group()
            val specialId = vocab[specialToken] ?: unkTokenId
            tokenIds.add(specialId)
            lastIdx = end
        }

        if (lastIdx < textWithEmotions.length) {
            val subText = textWithEmotions.substring(lastIdx)
            encodeSubText(subText, tokenIds)
        }

        return tokenIds.toIntArray()
    }

    private fun encodeSubText(text: String, tokenIds: ArrayList<Int>) {
        val normalized = Normalizer.normalize(text, Normalizer.Form.NFC)
        val matcher = PRE_TOKENIZER_PATTERN.matcher(normalized)

        while (matcher.find()) {
            val chunk = matcher.group()
            if (chunk.isEmpty()) continue

            val utf8Bytes = chunk.toByteArray(Charsets.UTF_8)
            val charBuf = StringBuilder(utf8Bytes.size)
            for (b in utf8Bytes) {
                val byteVal = b.toInt() and 0xFF
                charBuf.append(BYTE_ENCODER[byteVal] ?: '?')
            }

            val bpeResult = applyBpe(charBuf.toString())
            for (token in bpeResult) {
                val id = vocab[token] ?: unkTokenId
                tokenIds.add(id)
            }
        }
    }

    private fun applyBpe(token: String): List<String> {
        if (token.length <= 1) return listOf(token)

        var word: MutableList<String> = token.map { it.toString() }.toMutableList()
        var pairs = getPairs(word)
        if (pairs.isEmpty()) return listOf(token)

        while (true) {
            var minRank = Int.MAX_VALUE
            var bestPair: Pair<String, String>? = null

            for (pair in pairs) {
                val rank = bpeRanks[pair] ?: Int.MAX_VALUE
                if (rank < minRank) {
                    minRank = rank
                    bestPair = pair
                }
            }

            if (bestPair == null || minRank == Int.MAX_VALUE) {
                break
            }

            val first = bestPair.first
            val second = bestPair.second
            val newWord = ArrayList<String>()
            var i = 0
            while (i < word.size) {
                if (i < word.size - 1 && word[i] == first && word[i + 1] == second) {
                    newWord.add(first + second)
                    i += 2
                } else {
                    newWord.add(word[i])
                    i++
                }
            }

            word = newWord
            if (word.size <= 1) {
                break
            }
            pairs = getPairs(word)
        }

        return word
    }

    private fun getPairs(word: List<String>): Set<Pair<String, String>> {
        val pairs = LinkedHashSet<Pair<String, String>>()
        for (i in 0 until word.size - 1) {
            pairs.add(Pair(word[i], word[i + 1]))
        }
        return pairs
    }

    companion object {
        // Non-verbal emotion cue mapping
        val EMOTION_TAG_TO_K = mapOf(
            "chuckle" to 1, "cười" to 1, "cuoi" to 1, "laugh" to 1, "laughter" to 1,
            "sigh" to 2, "thở dài" to 2, "tho dai" to 2,
            "clear throat" to 3, "clearthroat" to 3, "hắng giọng" to 3, "hang giong" to 3
        )

        private val EMOTION_SPLIT_REGEX = Regex("""(\[[^\]]+\]|<\|emotion_\d+\|>)""")

        fun resolveEmotionTags(text: String): String {
            if (!text.contains('[') && !text.contains("<|emotion_")) return text

            val sb = StringBuilder()
            val parts = EMOTION_SPLIT_REGEX.findAll(text)
            var lastIdx = 0

            for (match in parts) {
                val spanStart = match.range.first
                val spanEnd = match.range.last + 1
                if (spanStart > lastIdx) {
                    sb.append(text.substring(lastIdx, spanStart))
                }
                val rawTag = match.value
                val emotionToken = getEmotionToken(rawTag)
                if (emotionToken != null) {
                    if (sb.isNotEmpty() && !sb.endsWith(" ")) {
                        sb.append(" ")
                    }
                    sb.append(emotionToken)
                } else {
                    sb.append(rawTag)
                }
                lastIdx = spanEnd
            }
            if (lastIdx < text.length) {
                sb.append(text.substring(lastIdx))
            }
            return sb.toString()
        }

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

        private val PRE_TOKENIZER_PATTERN = Pattern.compile(
            """(?i:'s|'t|'re|'ve|'m|'ll|'d)|<\|emotion_\d+\|>|[^\r\n\p{L}\p{N}]?\p{L}+|\p{N}| ?[^\s\p{L}\p{N}]+[\r\n]*|\s*[\r\n]+|\s+(?!\S)|\s+"""
        )

        private val BYTE_ENCODER: Array<Char> = run {
            val bs = ArrayList<Int>()
            for (b in '!'.code..'~'.code) bs.add(b)
            for (b in '¡'.code..'¬'.code) bs.add(b)
            for (b in '®'.code..'ÿ'.code) bs.add(b)
            val cs = ArrayList<Int>(bs)
            var n = 0
            for (b in 0..255) {
                if (!bs.contains(b)) {
                    bs.add(b)
                    cs.add(256 + n)
                    n++
                }
            }
            val arr = Array(256) { ' ' }
            for (i in 0 until bs.size) {
                arr[bs[i]] = cs[i].toChar()
            }
            arr
        }

        fun fromJson(jsonStr: String): VieNeuTokenizer {
            val root = JSONObject(jsonStr)
            val model = root.getJSONObject("model")
            val vocabObj = model.getJSONObject("vocab")
            val vocab = HashMap<String, Int>(vocabObj.length())
            val keys = vocabObj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                vocab[k] = vocabObj.getInt(k)
            }

            // Also load added_tokens if present
            val addedTokens = root.optJSONArray("added_tokens")
            if (addedTokens != null) {
                for (i in 0 until addedTokens.length()) {
                    val tokObj = addedTokens.getJSONObject(i)
                    val content = tokObj.getString("content")
                    val id = tokObj.getInt("id")
                    vocab[content] = id
                }
            }

            val mergesArr = model.getJSONArray("merges")
            val bpeRanks = HashMap<Pair<String, String>, Int>(mergesArr.length())
            for (i in 0 until mergesArr.length()) {
                val item = mergesArr.get(i)
                if (item is String) {
                    val parts = item.split(" ")
                    if (parts.size >= 2) {
                        bpeRanks[Pair(parts[0], parts[1])] = i
                    }
                } else if (item is org.json.JSONArray) {
                    if (item.length() >= 2) {
                        bpeRanks[Pair(item.getString(0), item.getString(1))] = i
                    }
                }
            }

            val unkId = vocab["<|unk|>"] ?: 43
            return VieNeuTokenizer(vocab, bpeRanks, unkId)
        }

        fun fromInputStream(inputStream: InputStream): VieNeuTokenizer {
            val jsonStr = inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            return fromJson(jsonStr)
        }

        fun fromFile(file: File): VieNeuTokenizer {
            return fromJson(file.readText(Charsets.UTF_8))
        }
    }
}
