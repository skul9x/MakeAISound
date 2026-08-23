package skul9x.example.makesound.telemetry

import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Performance metrics measured for a single synthesized speech chunk.
 */
data class ChunkPerformanceMetrics(
    val chunkIndex: Int,
    val totalChunks: Int,
    val textSnippet: String,
    val charCount: Int,
    val phonemeCount: Int,
    val g2pDurationMs: Long,
    val prefillDurationMs: Long,
    val decodeDurationMs: Long,
    val codecDurationMs: Long,
    val totalSynthesisDurationMs: Long,
    val audioDurationMs: Long,
    val rtf: Float,
    val peakMemoryMb: Double = 0.0
) {
    fun format(): String {
        return String.format(
            Locale.US,
            "Chunk #%d/%d [%d chars, %d phones] | G2P: %dms, Prefill: %dms, Decode: %dms, Codec: %dms | Total: %dms, Audio: %dms -> RTF: %.3fx",
            chunkIndex + 1, totalChunks, charCount, phonemeCount,
            g2pDurationMs, prefillDurationMs, decodeDurationMs, codecDurationMs,
            totalSynthesisDurationMs, audioDurationMs, rtf
        )
    }
}

/**
 * Aggregated session metrics for a complete audio synthesis task.
 */
data class SessionPerformanceMetrics(
    val sessionId: String = "",
    val voiceName: String = "",
    val totalChars: Int = 0,
    val totalPhonemes: Int = 0,
    val totalSynthesisDurationMs: Long = 0L,
    val totalAudioDurationMs: Long = 0L,
    val averageRtf: Float = 0f,
    val peakMemoryMb: Double = 0.0,
    val chunks: List<ChunkPerformanceMetrics> = emptyList()
) {
    fun formatSummary(): String {
        return String.format(
            Locale.US,
            "📊 Session Summary [%s | Voice: %s]\n" +
                    "  • Total Chunks: %d\n" +
                    "  • Total Characters: %d | Total Phonemes: %d\n" +
                    "  • Synthesis Time: %d ms | Audio Generated: %d ms (%.2f sec)\n" +
                    "  • Average RTF: %.3fx\n" +
                    "  • Peak Memory: %.1f MB",
            sessionId, voiceName, chunks.size, totalChars, totalPhonemes,
            totalSynthesisDurationMs, totalAudioDurationMs,
            totalAudioDurationMs / 1000.0, averageRtf, peakMemoryMb
        )
    }
}

/**
 * Core performance telemetry tracker for VieNeu AI engine.
 * Thread-safe and light-weight (< 1ms overhead per chunk).
 */
object PerformanceMetricsTracker {
    private const val TAG = "PerformanceTelemetry"

    @Volatile
    private var currentSession: SessionPerformanceMetrics = SessionPerformanceMetrics()

    private val chunkList = CopyOnWriteArrayList<ChunkPerformanceMetrics>()
    private var sessionStartTimeMs: Long = 0L

    @Synchronized
    fun startSession(sessionId: String = "", voiceName: String = "") {
        sessionStartTimeMs = System.currentTimeMillis()
        chunkList.clear()
        currentSession = SessionPerformanceMetrics(
            sessionId = sessionId,
            voiceName = voiceName
        )
        StudioLogger.i(TAG, "⏱️ Performance session started: '$sessionId' [Voice: $voiceName]")
    }

    @Synchronized
    fun recordChunk(chunk: ChunkPerformanceMetrics) {
        chunkList.add(chunk)
        StudioLogger.d(TAG, chunk.format(), stage = "Telemetry")
    }

    @Synchronized
    fun finishSession(): SessionPerformanceMetrics {
        var totalChars = 0
        var totalPhones = 0
        var totalSynthesisMs = 0L
        var totalAudioMs = 0L
        var maxPeakMemory = 0.0

        for (c in chunkList) {
            totalChars += c.charCount
            totalPhones += c.phonemeCount
            totalSynthesisMs += c.totalSynthesisDurationMs
            totalAudioMs += c.audioDurationMs
            if (c.peakMemoryMb > maxPeakMemory) {
                maxPeakMemory = c.peakMemoryMb
            }
        }

        val avgRtf = if (totalAudioMs > 0) {
            totalSynthesisMs.toFloat() / totalAudioMs.toFloat()
        } else {
            0f
        }

        currentSession = currentSession.copy(
            totalChars = totalChars,
            totalPhonemes = totalPhones,
            totalSynthesisDurationMs = totalSynthesisMs,
            totalAudioDurationMs = totalAudioMs,
            averageRtf = avgRtf,
            peakMemoryMb = maxPeakMemory,
            chunks = ArrayList(chunkList)
        )

        StudioLogger.i(TAG, currentSession.formatSummary(), stage = "Telemetry")
        return currentSession
    }

    fun getCurrentSession(): SessionPerformanceMetrics = currentSession

    fun getMemoryUsageMb(): Double {
        val runtime = Runtime.getRuntime()
        val usedMem = runtime.totalMemory() - runtime.freeMemory()
        return usedMem.toDouble() / (1024.0 * 1024.0)
    }
}
