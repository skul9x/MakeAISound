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

    val V3_CALIBRATED_GAP_SILENCE_MS = mapOf(
        "para" to 700,
        "paragraph" to 700,
        "sentence" to 500,
        "minor" to 300,
        "clause" to 300
    )

    const val EDGE_THRESH_DB = -45.0f
    const val EDGE_WIN_MS = 10.0f

    /**
     * Measure leading and trailing silence durations (in sample counts) of a 16-bit PCM waveform.
     * Uses a 10ms window RMS envelope threshold (-45 dB).
     * If the audio is completely silent or shorter than one window, returns (pcm16.size, 0).
     *
     * @param pcm16 16-bit PCM audio samples.
     * @param sampleRate Sampling rate in Hz (default 48000).
     * @param threshDb RMS threshold in decibels (default -45.0 dB).
     * @param winMs Window duration in milliseconds (default 10.0 ms).
     * @return Pair of (leadingSilenceSamples, trailingSilenceSamples).
     */
    fun edgeSilence(
        pcm16: ShortArray,
        sampleRate: Int = VieNeuConfig.SAMPLE_RATE,
        threshDb: Float = EDGE_THRESH_DB,
        winMs: Float = EDGE_WIN_MS
    ): Pair<Int, Int> {
        val nSamp = pcm16.size
        if (nSamp == 0) return Pair(0, 0)

        val win = maxOf(1, ((winMs * sampleRate) / 1000f).toInt())
        val nWin = nSamp / win
        if (nWin == 0) {
            return Pair(nSamp, 0)
        }

        val threshLinear = Math.pow(10.0, threshDb.toDouble() / 20.0)

        // Find first active window from start
        var firstActiveWin = -1
        for (w in 0 until nWin) {
            val startIdx = w * win
            var sumSq = 0.0
            for (i in 0 until win) {
                val s = pcm16[startIdx + i].toDouble() / 32767.0
                sumSq += s * s
            }
            val rms = Math.sqrt(sumSq / win)
            if (rms > threshLinear) {
                firstActiveWin = w
                break
            }
        }

        // Entire waveform is silence
        if (firstActiveWin == -1) {
            return Pair(nSamp, 0)
        }

        // Find last active window from end
        var lastActiveWin = firstActiveWin
        for (w in nWin - 1 downTo firstActiveWin) {
            val startIdx = w * win
            var sumSq = 0.0
            for (i in 0 until win) {
                val s = pcm16[startIdx + i].toDouble() / 32767.0
                sumSq += s * s
            }
            val rms = Math.sqrt(sumSq / win)
            if (rms > threshLinear) {
                lastActiveWin = w
                break
            }
        }

        val lead = firstActiveWin * win
        val tail = nSamp - (lastActiveWin + 1) * win
        return Pair(lead, tail)
    }

    /**
     * FloatArray overload of [edgeSilence].
     */
    fun edgeSilence(
        pcmFloat: FloatArray,
        sampleRate: Int = VieNeuConfig.SAMPLE_RATE,
        threshDb: Float = EDGE_THRESH_DB,
        winMs: Float = EDGE_WIN_MS
    ): Pair<Int, Int> {
        val nSamp = pcmFloat.size
        if (nSamp == 0) return Pair(0, 0)

        val win = maxOf(1, ((winMs * sampleRate) / 1000f).toInt())
        val nWin = nSamp / win
        if (nWin == 0) {
            return Pair(nSamp, 0)
        }

        val threshLinear = Math.pow(10.0, threshDb.toDouble() / 20.0)

        var firstActiveWin = -1
        for (w in 0 until nWin) {
            val startIdx = w * win
            var sumSq = 0.0
            for (i in 0 until win) {
                val s = pcmFloat[startIdx + i].toDouble()
                sumSq += s * s
            }
            val rms = Math.sqrt(sumSq / win)
            if (rms > threshLinear) {
                firstActiveWin = w
                break
            }
        }

        if (firstActiveWin == -1) {
            return Pair(nSamp, 0)
        }

        var lastActiveWin = firstActiveWin
        for (w in nWin - 1 downTo firstActiveWin) {
            val startIdx = w * win
            var sumSq = 0.0
            for (i in 0 until win) {
                val s = pcmFloat[startIdx + i].toDouble()
                sumSq += s * s
            }
            val rms = Math.sqrt(sumSq / win)
            if (rms > threshLinear) {
                lastActiveWin = w
                break
            }
        }

        val lead = firstActiveWin * win
        val tail = nSamp - (lastActiveWin + 1) * win
        return Pair(lead, tail)
    }

    /**
     * Compute remaining zero samples needed between [prevChunk] and [nextChunk] such that:
     * (tailSilence + zeros + leadSilence) == targetPause.
     * If (tailSilence + leadSilence) >= targetPause, returns 0.
     *
     * If [prevChunk] is all-silence, its entire duration counts as trailing silence.
     * If [nextChunk] is all-silence, its entire duration counts as leading silence.
     *
     * @param prevChunk Preceding 16-bit PCM chunk.
     * @param nextChunk Following 16-bit PCM chunk.
     * @param pauseMs Target pause duration in milliseconds.
     * @param sampleRate Sampling rate in Hz (default 48000).
     * @return Number of zero samples to pad between chunks.
     */
    fun pausePadSamples(
        prevChunk: ShortArray,
        nextChunk: ShortArray,
        pauseMs: Int,
        sampleRate: Int = VieNeuConfig.SAMPLE_RATE
    ): Int {
        if (pauseMs <= 0) return 0
        val targetSamples = ((pauseMs.toLong() * sampleRate) / 1000L).toInt()

        val tail = if (prevChunk.isNotEmpty()) {
            val (leadPrev, tailPrev) = edgeSilence(prevChunk, sampleRate)
            if (leadPrev == prevChunk.size) prevChunk.size else tailPrev
        } else {
            0
        }

        val lead = if (nextChunk.isNotEmpty()) {
            val (leadNext, _) = edgeSilence(nextChunk, sampleRate)
            leadNext
        } else {
            0
        }

        return maxOf(0, targetSamples - tail - lead)
    }

    /**
     * Float seconds overload of [pausePadSamples].
     */
    fun pausePadSamples(
        prevChunk: ShortArray,
        nextChunk: ShortArray,
        pauseSeconds: Float,
        sampleRate: Int = VieNeuConfig.SAMPLE_RATE
    ): Int = pausePadSamples(
        prevChunk,
        nextChunk,
        (pauseSeconds * 1000f).toInt(),
        sampleRate
    )

    /**
     * Join multiple audio chunks with calibrated gap silences using dynamic padding [pausePadSamples].
     */
    fun joinAudioChunks(
        chunks: List<ShortArray>,
        gaps: List<String>,
        sampleRate: Int = VieNeuConfig.SAMPLE_RATE
    ): ShortArray {
        if (chunks.isEmpty()) return ShortArray(0)
        if (chunks.size == 1) return chunks[0]

        val pads = IntArray(chunks.size - 1)
        var totalLen = 0
        for (c in chunks) {
            totalLen += c.size
        }
        for (i in 0 until chunks.size - 1) {
            val gapType = if (i < gaps.size) gaps[i] else "sentence"
            val pauseMs = V3_CALIBRATED_GAP_SILENCE_MS[gapType]
                ?: V3_GAP_SILENCE_MS[gapType]
                ?: 500
            val pad = pausePadSamples(chunks[i], chunks[i + 1], pauseMs, sampleRate)
            pads[i] = pad
            totalLen += pad
        }

        val result = ShortArray(totalLen)
        var offset = 0
        for (i in chunks.indices) {
            val chunk = chunks[i]
            System.arraycopy(chunk, 0, result, offset, chunk.size)
            offset += chunk.size

            if (i < chunks.size - 1) {
                offset += pads[i]
            }
        }

        return result
    }

    /**
     * Join multiple audio chunks with explicit list of pause durations in milliseconds.
     */
    fun joinAudioChunksWithPauses(
        chunks: List<ShortArray>,
        pausesMs: List<Int>,
        sampleRate: Int = VieNeuConfig.SAMPLE_RATE
    ): ShortArray {
        if (chunks.isEmpty()) return ShortArray(0)
        if (chunks.size == 1) return chunks[0]

        val pads = IntArray(chunks.size - 1)
        var totalLen = 0
        for (c in chunks) {
            totalLen += c.size
        }
        for (i in 0 until chunks.size - 1) {
            val pauseMs = if (i < pausesMs.size) pausesMs[i] else 500
            val pad = pausePadSamples(chunks[i], chunks[i + 1], pauseMs, sampleRate)
            pads[i] = pad
            totalLen += pad
        }

        val result = ShortArray(totalLen)
        var offset = 0
        for (i in chunks.indices) {
            val chunk = chunks[i]
            System.arraycopy(chunk, 0, result, offset, chunk.size)
            offset += chunk.size

            if (i < chunks.size - 1) {
                offset += pads[i]
            }
        }

        return result
    }
}
