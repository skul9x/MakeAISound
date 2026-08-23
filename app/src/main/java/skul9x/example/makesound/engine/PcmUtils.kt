package skul9x.example.makesound.engine

/**
 * Utility functions for PCM audio data conversion, gap silence, and micro-fading.
 */
object PcmUtils {

    val V3_GAP_SILENCE_MS = mapOf(
        "para" to 350,
        "paragraph" to 350,
        "sentence" to 180,
        "minor" to 40,
        "clause" to 40
    )

    /**
     * Convert float32 [-1.0, 1.0] PCM samples to int16 [-32768, 32767] PCM samples.
     */
    fun floatToShort(floatPcm: FloatArray): ShortArray {
        val result = ShortArray(floatPcm.size)
        for (i in floatPcm.indices) {
            result[i] = (floatPcm[i].coerceIn(-1f, 1f) * 32767f).toInt().toShort()
        }
        return result
    }

    /**
     * Convert int16 [-32768, 32767] PCM samples to float32 [-1.0, 1.0] PCM samples.
     */
    fun shortToFloat(shortPcm: ShortArray): FloatArray {
        val result = FloatArray(shortPcm.size)
        val invScale = 1f / 32767f
        for (i in shortPcm.indices) {
            result[i] = shortPcm[i].toFloat() * invScale
        }
        return result
    }

    /**
     * Split text into sentence chunks using [SmartTextSegmenter].
     */
    fun splitIntoSentences(text: String, maxCharsPerChunk: Int = SmartTextSegmenter.DEFAULT_MAX_CHARS): List<String> {
        return SmartTextSegmenter.segment(text, maxCharsPerChunk).map { it.text }
    }

    /**
     * Split text into structured [TextChunk] list with pause metadata.
     */
    fun splitIntoChunks(text: String, maxCharsPerChunk: Int = SmartTextSegmenter.DEFAULT_MAX_CHARS): List<TextChunk> {
        return SmartTextSegmenter.segment(text, maxCharsPerChunk)
    }

    /**
     * Generate acoustic silence buffer (16-bit PCM zeroes) for a given pause duration in milliseconds.
     */
    fun generateSilence(pauseMs: Int, sampleRate: Int = VieNeuConfig.SAMPLE_RATE): ShortArray {
        if (pauseMs <= 0) return ShortArray(0)
        val sampleCount = ((sampleRate.toLong() * pauseMs) / 1000L).toInt()
        return ShortArray(sampleCount)
    }

    /**
     * Apply micro-fading (fade-in at start, fade-out at end) in-place to eliminate acoustic pop/click discontinuities.
     * Uses linear ramp over fadeDurationMs (default 5ms).
     */
    fun applyMicroFade(
        pcm16: ShortArray,
        fadeDurationMs: Float = 5.0f,
        sampleRate: Int = VieNeuConfig.SAMPLE_RATE
    ): ShortArray {
        if (pcm16.isEmpty()) return pcm16
        val fadeSamples = ((sampleRate * fadeDurationMs) / 1000f).toInt().coerceAtMost(pcm16.size / 2)
        if (fadeSamples <= 0) return pcm16

        val invFade = 1.0f / fadeSamples.toFloat()
        // Fade in: [0..fadeSamples-1] ramp 0.0 -> 1.0
        for (i in 0 until fadeSamples) {
            val factor = i.toFloat() * invFade
            pcm16[i] = (pcm16[i] * factor).toInt().toShort()
        }

        // Fade out: [size - fadeSamples..size - 1] ramp 1.0 -> 0.0
        val n = pcm16.size
        for (i in 0 until fadeSamples) {
            val factor = (fadeSamples - 1 - i).toFloat() * invFade
            val idx = n - fadeSamples + i
            pcm16[idx] = (pcm16[idx] * factor).toInt().toShort()
        }

        return pcm16
    }

    /**
     * Join multiple audio chunks with calibrated gap silences.
     */
    fun joinAudioChunks(
        chunks: List<ShortArray>,
        gaps: List<String>,
        sampleRate: Int = VieNeuConfig.SAMPLE_RATE
    ): ShortArray {
        if (chunks.isEmpty()) return ShortArray(0)
        if (chunks.size == 1) return chunks[0]

        var totalLen = 0
        for (c in chunks) {
            totalLen += c.size
        }
        for (g in gaps) {
            val pauseMs = V3_GAP_SILENCE_MS[g] ?: V3_GAP_SILENCE_MS["sentence"] ?: 180
            totalLen += ((sampleRate.toLong() * pauseMs) / 1000L).toInt()
        }

        val result = ShortArray(totalLen)
        var offset = 0

        for (i in chunks.indices) {
            val chunk = chunks[i]
            System.arraycopy(chunk, 0, result, offset, chunk.size)
            offset += chunk.size

            if (i < gaps.size) {
                val gapType = gaps[i]
                val pauseMs = V3_GAP_SILENCE_MS[gapType] ?: V3_GAP_SILENCE_MS["sentence"] ?: 180
                val silenceSamples = ((sampleRate.toLong() * pauseMs) / 1000L).toInt()
                // Zeroes already in result array
                offset += silenceSamples
            }
        }

        return if (offset == totalLen) result else result.copyOf(offset)
    }
}
