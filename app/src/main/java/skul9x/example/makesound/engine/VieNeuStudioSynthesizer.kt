package skul9x.example.makesound.engine

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import skul9x.example.makesound.telemetry.ChunkPerformanceMetrics
import skul9x.example.makesound.telemetry.PerformanceMetricsTracker
import skul9x.example.makesound.telemetry.SessionPerformanceMetrics
import skul9x.example.makesound.telemetry.StudioLogger
import kotlin.coroutines.coroutineContext

/**
 * Result of batch speech synthesis by [VieNeuStudioSynthesizer].
 */
data class SynthesizedAudioResult(
    val pcm16Data: ShortArray,
    val sampleRate: Int = VieNeuConfig.SAMPLE_RATE,
    val metrics: SessionPerformanceMetrics,
    val textChunks: List<TextChunk>
) {
    val durationSeconds: Float
        get() = if (sampleRate > 0) pcm16Data.size.toFloat() / sampleRate.toFloat() else 0f

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as SynthesizedAudioResult
        if (!pcm16Data.contentEquals(other.pcm16Data)) return false
        if (sampleRate != other.sampleRate) return false
        if (metrics != other.metrics) return false
        if (textChunks != other.textChunks) return false
        return true
    }

    override fun hashCode(): Int {
        var result = pcm16Data.contentHashCode()
        result = 31 * result + sampleRate
        result = 31 * result + metrics.hashCode()
        result = 31 * result + textChunks.hashCode()
        return result
    }
}

/**
 * Batch generation coordinator for VieNeu-TTS studio audio generation.
 * Handles linguistic segmentation, phonemization, ONNX inference, audio post-processing,
 * micro-fading, telemetry logging, and progress reporting.
 */
class VieNeuStudioSynthesizer(
    private val engine: VieNeuOnnxEngine
) : AutoCloseable {

    private var closed = false
    val isClosed: Boolean
        get() = closed || engine.isClosed

    override fun close() {
        if (closed) return
        closed = true
        try {
            engine.close()
        } catch (_: Exception) {}
    }

    /**
     * Synthesize input text into full 48kHz 16-bit PCM audio with progress callback.
     *
     * @param text Raw story or document text (plain text or HTML).
     * @param voiceName Selected voice preset name (e.g. "Trúc Ly", "Minh Đức").
     * @param maxCharsPerChunk Chunk size boundary (default 180 chars).
     * @param pauseConfig Custom pause configuration.
     * @param temperature Sampling temperature (default 0.8f).
     * @param topK Top-K sampling cutoff (default 25).
     * @param topP Top-P nucleus sampling (default 0.95f).
     * @param repetitionPenalty Repetition penalty factor (default 1.2f).
     * @param onProgress Callback invoked on each chunk: (chunkIndex, totalChunks, percent, chunkMetrics).
     * @return [SynthesizedAudioResult] containing concatenated PCM audio and telemetry.
     */
    suspend fun synthesize(
        text: String,
        voiceName: String = "Trúc Ly",
        maxCharsPerChunk: Int = SmartTextSegmenter.DEFAULT_MAX_CHARS,
        pauseConfig: PauseConfig = PauseConfig(),
        temperature: Float = 0.8f,
        topK: Int = 25,
        topP: Float = 0.95f,
        repetitionPenalty: Float = 1.2f,
        onProgress: ((chunkIndex: Int, totalChunks: Int, percent: Float, metrics: ChunkPerformanceMetrics?) -> Unit)? = null
    ): SynthesizedAudioResult = withContext(Dispatchers.Default) {
        if (isClosed) {
            throw IllegalStateException("VieNeuStudioSynthesizer is closed")
        }
        val sessionId = "Session_${System.currentTimeMillis()}"
        PerformanceMetricsTracker.startSession(sessionId = sessionId, voiceName = voiceName)
        StudioLogger.i(TAG, "🎙️ Starting studio batch synthesis: ${text.take(60)}... [Voice: $voiceName]")

        // 1. Linguistic Segmentation
        val chunks = SmartTextSegmenter.segment(text, maxCharsPerChunk, pauseConfig)
        if (chunks.isEmpty()) {
            val emptySession = PerformanceMetricsTracker.finishSession()
            return@withContext SynthesizedAudioResult(
                pcm16Data = ShortArray(0),
                sampleRate = VieNeuConfig.SAMPLE_RATE,
                metrics = emptySession,
                textChunks = emptyList()
            )
        }

        val totalChunks = chunks.size
        val audioChunkList = ArrayList<ShortArray>(totalChunks)

        onProgress?.invoke(0, totalChunks, 0.0f, null)

        for ((idx, chunk) in chunks.withIndex()) {
            coroutineContext.ensureActive()

            val chunkText = chunk.text
            val charCount = chunkText.length

            // 2. G2P Phonemization
            val g2pStart = System.currentTimeMillis()
            val phonemes = SeaG2P.phonemizeWithEmotions(chunkText)
            val g2pDuration = System.currentTimeMillis() - g2pStart
            val phonemeCount = phonemes.length

            // 3. Neural Acoustic + Codec Inference
            val inferResult = engine.inferWithTiming(
                phonemes = phonemes,
                voiceName = voiceName,
                temperature = temperature,
                topK = topK,
                topP = topP,
                repetitionPenalty = repetitionPenalty
            )

            // 4. Float to Short & 5ms Micro-Fade
            val shortAudio = PcmUtils.floatToShort(inferResult.pcm)
            PcmUtils.applyMicroFade(shortAudio)
            audioChunkList.add(shortAudio)

            // 5. Telemetry Tracking
            val timing = inferResult.timing
            val totalSynthMs = g2pDuration + timing.totalDurationMs
            val audioDurationMs = if (VieNeuConfig.SAMPLE_RATE > 0) {
                ((shortAudio.size.toLong() * 1000L) / VieNeuConfig.SAMPLE_RATE)
            } else {
                0L
            }
            val rtf = if (audioDurationMs > 0) totalSynthMs.toFloat() / audioDurationMs.toFloat() else 0f
            val peakMem = PerformanceMetricsTracker.getMemoryUsageMb()

            val chunkMetrics = ChunkPerformanceMetrics(
                chunkIndex = idx,
                totalChunks = totalChunks,
                textSnippet = chunkText.take(40),
                charCount = charCount,
                phonemeCount = phonemeCount,
                g2pDurationMs = g2pDuration,
                prefillDurationMs = timing.prefillDurationMs,
                decodeDurationMs = timing.decodeDurationMs,
                codecDurationMs = timing.codecDurationMs,
                totalSynthesisDurationMs = totalSynthMs,
                audioDurationMs = audioDurationMs,
                rtf = rtf,
                peakMemoryMb = peakMem
            )
            PerformanceMetricsTracker.recordChunk(chunkMetrics)

            val percent = ((idx + 1).toFloat() / totalChunks.toFloat()) * 100f
            onProgress?.invoke(idx + 1, totalChunks, percent, chunkMetrics)
        }

        // 6. Join Chunks with Dynamic Gap Silences using pausePadSamples
        val padLengths = IntArray(if (totalChunks > 1) totalChunks - 1 else 0)
        var totalSamples = 0
        for (i in 0 until totalChunks) {
            totalSamples += audioChunkList[i].size
            if (i < totalChunks - 1) {
                val pad = PcmUtils.pausePadSamples(
                    prevChunk = audioChunkList[i],
                    nextChunk = audioChunkList[i + 1],
                    pauseMs = chunks[i].pauseDurationMs,
                    sampleRate = VieNeuConfig.SAMPLE_RATE
                )
                padLengths[i] = pad
                totalSamples += pad
            }
        }

        val finalPcm = ShortArray(totalSamples)
        var offset = 0
        for (i in 0 until totalChunks) {
            val audio = audioChunkList[i]
            System.arraycopy(audio, 0, finalPcm, offset, audio.size)
            offset += audio.size

            if (i < totalChunks - 1) {
                val pad = padLengths[i]
                offset += pad // ShortArray is already zero-initialized
            }
        }

        val sessionMetrics = PerformanceMetricsTracker.finishSession()
        StudioLogger.i(TAG, "✅ Studio batch synthesis completed. Audio length: ${finalPcm.size} samples")

        SynthesizedAudioResult(
            pcm16Data = finalPcm,
            sampleRate = VieNeuConfig.SAMPLE_RATE,
            metrics = sessionMetrics,
            textChunks = chunks
        )
    }

    companion object {
        private const val TAG = "VieNeuStudioSynthesizer"
    }
}
