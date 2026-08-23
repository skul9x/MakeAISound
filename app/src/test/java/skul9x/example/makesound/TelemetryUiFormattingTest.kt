package skul9x.example.makesound

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import skul9x.example.makesound.telemetry.ChunkPerformanceMetrics
import skul9x.example.makesound.telemetry.LogLevel
import skul9x.example.makesound.telemetry.SessionPerformanceMetrics
import skul9x.example.makesound.telemetry.StudioLogEntry
import skul9x.example.makesound.ui.StudioUiState
import skul9x.example.makesound.ui.components.buildAnnotatedLogEntry
import skul9x.example.makesound.ui.components.formatPlainLogEntry
import java.util.Locale

/**
 * Single test file verifying the Telemetry UI formatting, AnnotatedString rendering,
 * and filter/metrics logic for DiagnosticsLogBottomSheet.
 */
class TelemetryUiFormattingTest {

    @Test
    fun testBuildAnnotatedLogEntry_containsFullMessageAndTag() {
        val entry = StudioLogEntry(
            id = 1L,
            timestamp = 1724407082170L,
            level = LogLevel.INFO,
            tag = "OnnxSessionManager",
            message = "Configured XNNPACK EP with 6 threads for FP32 model"
        )

        val annotatedString = buildAnnotatedLogEntry(entry)
        val text = annotatedString.text

        assertTrue("Should contain log level [INFO]", text.contains("[INFO]"))
        assertTrue("Should contain tag [OnnxSessionManager]", text.contains("[OnnxSessionManager]"))
        assertTrue("Should contain full un-truncated message", text.contains("Configured XNNPACK EP with 6 threads for FP32 model"))
        assertTrue("Should contain style spans for time, level, tag, and text", annotatedString.spanStyles.size >= 4)
    }

    @Test
    fun testFormatPlainLogEntry_generatesCleanClipboardFormat() {
        val entry = StudioLogEntry(
            id = 2L,
            timestamp = 1724407082170L,
            level = LogLevel.ERROR,
            tag = "VieNeuAudioEngine",
            message = "Synthesizer failed to decode audio frame"
        )

        val plain = formatPlainLogEntry(entry)
        assertTrue(plain.startsWith("["))
        assertTrue(plain.contains("[ERROR] [VieNeuAudioEngine] Synthesizer failed to decode audio frame"))
    }

    @Test
    fun testAllLogLevels_formattingWorksCorrectly() {
        LogLevel.values().forEach { level ->
            val entry = StudioLogEntry(
                id = level.ordinal.toLong() + 10,
                timestamp = System.currentTimeMillis(),
                level = level,
                tag = "TestTag",
                message = "Testing level ${level.name}"
            )
            val annotated = buildAnnotatedLogEntry(entry)
            assertTrue("Annotated text must include level name", annotated.text.contains("[${level.name}]"))
        }
    }

    @Test
    fun testTelemetryMetricsCalculations() {
        val chunks = listOf(
            ChunkPerformanceMetrics(
                chunkIndex = 0,
                totalChunks = 2,
                textSnippet = "Xin chào",
                charCount = 8,
                phonemeCount = 12,
                g2pDurationMs = 15L,
                prefillDurationMs = 50L,
                decodeDurationMs = 900L,
                codecDurationMs = 35L,
                totalSynthesisDurationMs = 1000L,
                audioDurationMs = 2000L,
                rtf = 0.5f,
                peakMemoryMb = 120.5
            ),
            ChunkPerformanceMetrics(
                chunkIndex = 1,
                totalChunks = 2,
                textSnippet = "Việt Nam",
                charCount = 8,
                phonemeCount = 12,
                g2pDurationMs = 15L,
                prefillDurationMs = 50L,
                decodeDurationMs = 1100L,
                codecDurationMs = 35L,
                totalSynthesisDurationMs = 1200L,
                audioDurationMs = 2000L,
                rtf = 0.6f,
                peakMemoryMb = 135.0
            )
        )
        val sessionMetrics = SessionPerformanceMetrics(
            sessionId = "Session_1",
            voiceName = "HN_Mai",
            totalChars = 16,
            totalPhonemes = 24,
            totalSynthesisDurationMs = 2200L,
            totalAudioDurationMs = 4000L,
            averageRtf = 0.55f,
            peakMemoryMb = 135.0,
            chunks = chunks
        )

        assertEquals(2, sessionMetrics.chunks.size)
        val formattedRtf = String.format(Locale.US, "%.3fx", sessionMetrics.averageRtf)
        assertEquals("0.550x", formattedRtf)
        val formattedMem = String.format(Locale.US, "%.1f MB", sessionMetrics.peakMemoryMb)
        assertEquals("135.0 MB", formattedMem)
    }

    @Test
    fun testStudioUiState_filteredLogsCategories() {
        val rawLogs = listOf(
            StudioLogEntry(id = 1, timestamp = 1000L, level = LogLevel.INFO, tag = "VieNeuAudioEngine", message = "Synthesis started"),
            StudioLogEntry(id = 2, timestamp = 1010L, level = LogLevel.INFO, tag = "WavStorage", message = "WAV written to storage"),
            StudioLogEntry(id = 3, timestamp = 1020L, level = LogLevel.ERROR, tag = "OnnxSessionManager", message = "Execution failed")
        )

        val stateAll = StudioUiState(logs = rawLogs, logFilter = "ALL")
        assertEquals(3, stateAll.filteredLogs.size)

        val stateSynth = StudioUiState(logs = rawLogs, logFilter = "SYNTHESIS")
        assertEquals(1, stateSynth.filteredLogs.size)
        assertEquals("VieNeuAudioEngine", stateSynth.filteredLogs[0].tag)

        val stateStorage = StudioUiState(logs = rawLogs, logFilter = "STORAGE")
        assertEquals(1, stateStorage.filteredLogs.size)
        assertEquals("WavStorage", stateStorage.filteredLogs[0].tag)

        val stateError = StudioUiState(logs = rawLogs, logFilter = "ERROR")
        assertEquals(1, stateError.filteredLogs.size)
        assertEquals(LogLevel.ERROR, stateError.filteredLogs[0].level)
    }
}
