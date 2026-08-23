package skul9x.example.makesound.ui

import skul9x.example.makesound.engine.SynthesizedAudioResult
import skul9x.example.makesound.engine.VoicePreset
import skul9x.example.makesound.player.PlayerState
import skul9x.example.makesound.telemetry.ChunkPerformanceMetrics
import skul9x.example.makesound.telemetry.SessionPerformanceMetrics
import skul9x.example.makesound.telemetry.StudioLogEntry
import java.io.File

/**
 * Speech synthesis generation lifecycle state.
 */
sealed class GenerationState {
    object Idle : GenerationState()

    data class Generating(
        val chunkIndex: Int = 0,
        val totalChunks: Int = 0,
        val percent: Float = 0f,
        val currentMetrics: ChunkPerformanceMetrics? = null
    ) : GenerationState()

    data class Success(
        val result: SynthesizedAudioResult,
        val audioFile: File
    ) : GenerationState()

    data class Error(
        val message: String
    ) : GenerationState()
}

/**
 * Filter categories for voice selection modal.
 */
enum class VoiceFilter(val displayName: String) {
    ALL("Tất cả"),
    NORTH("Miền Bắc"),
    CENTRAL("Miền Trung"),
    SOUTH("Miền Nam"),
    MALE("Nam"),
    FEMALE("Nữ")
}

/**
 * Complete immutable UI state hierarchy for MakeAiSound Studio.
 */
data class StudioUiState(
    val text: String = "",
    val selectedVoice: VoicePreset? = null,
    val availableVoices: List<VoicePreset> = emptyList(),
    val selectedVoiceFilter: VoiceFilter = VoiceFilter.ALL,
    val generationState: GenerationState = GenerationState.Idle,
    val generatedAudioFile: File? = null,
    val playerState: PlayerState = PlayerState.IDLE,
    val waveformAmplitudes: FloatArray = FloatArray(48),
    val sessionMetrics: SessionPerformanceMetrics? = null,
    val logs: List<StudioLogEntry> = emptyList(),
    val logFilter: String = "ALL",
    val statusMessage: String? = null,
    val showVoicePicker: Boolean = false,
    val showDiagnostics: Boolean = false
) {
    val charCount: Int
        get() = text.length

    val estimatedDurationSeconds: Double
        get() = if (text.isBlank()) 0.0 else (text.length / 18.0).coerceAtLeast(1.0)

    val isGenerating: Boolean
        get() = generationState is GenerationState.Generating

    val canGenerate: Boolean
        get() = text.isNotBlank() && !isGenerating && selectedVoice != null

    val filteredVoices: List<VoicePreset>
        get() = when (selectedVoiceFilter) {
            VoiceFilter.ALL -> availableVoices
            VoiceFilter.NORTH -> availableVoices.filter {
                it.region.equals("Bắc", ignoreCase = true) ||
                        it.region.equals("Miền Bắc", ignoreCase = true) ||
                        it.regionDisplay.contains("Bắc", ignoreCase = true) ||
                        it.description.contains("Miền Bắc", ignoreCase = true) ||
                        it.description.contains("· Bắc", ignoreCase = true)
            }
            VoiceFilter.CENTRAL -> availableVoices.filter {
                it.region.equals("Trung", ignoreCase = true) ||
                        it.region.equals("Miền Trung", ignoreCase = true) ||
                        it.regionDisplay.contains("Trung", ignoreCase = true) ||
                        it.description.contains("Miền Trung", ignoreCase = true) ||
                        it.description.contains("· Trung", ignoreCase = true)
            }
            VoiceFilter.SOUTH -> availableVoices.filter {
                it.region.equals("Nam", ignoreCase = true) ||
                        it.region.equals("Miền Nam", ignoreCase = true) ||
                        it.regionDisplay.contains("Miền Nam", ignoreCase = true) ||
                        it.description.contains("Miền Nam", ignoreCase = true) ||
                        it.description.contains("· Nam", ignoreCase = true)
            }
            VoiceFilter.MALE -> availableVoices.filter {
                it.gender.equals("male", ignoreCase = true) ||
                        it.genderDisplay.equals("Nam", ignoreCase = true)
            }
            VoiceFilter.FEMALE -> availableVoices.filter {
                it.gender.equals("female", ignoreCase = true) ||
                        it.genderDisplay.equals("Nữ", ignoreCase = true)
            }
        }

    val filteredLogs: List<StudioLogEntry>
        get() = when (logFilter.uppercase()) {
            "ALL" -> logs
            "SYNTHESIS" -> logs.filter { it.tag.contains("Synthesizer", ignoreCase = true) || it.tag.contains("Engine", ignoreCase = true) || it.tag.contains("G2P", ignoreCase = true) }
            "STORAGE" -> logs.filter { it.tag.contains("Storage", ignoreCase = true) || it.tag.contains("Wav", ignoreCase = true) || it.tag.contains("Share", ignoreCase = true) }
            "ERROR" -> logs.filter { it.level.name == "ERROR" }
            else -> logs
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as StudioUiState
        if (text != other.text) return false
        if (selectedVoice != other.selectedVoice) return false
        if (availableVoices != other.availableVoices) return false
        if (selectedVoiceFilter != other.selectedVoiceFilter) return false
        if (generationState != other.generationState) return false
        if (generatedAudioFile != other.generatedAudioFile) return false
        if (playerState != other.playerState) return false
        if (!waveformAmplitudes.contentEquals(other.waveformAmplitudes)) return false
        if (sessionMetrics != other.sessionMetrics) return false
        if (logs != other.logs) return false
        if (logFilter != other.logFilter) return false
        if (statusMessage != other.statusMessage) return false
        if (showVoicePicker != other.showVoicePicker) return false
        if (showDiagnostics != other.showDiagnostics) return false
        return true
    }

    override fun hashCode(): Int {
        var result = text.hashCode()
        result = 31 * result + (selectedVoice?.hashCode() ?: 0)
        result = 31 * result + availableVoices.hashCode()
        result = 31 * result + selectedVoiceFilter.hashCode()
        result = 31 * result + generationState.hashCode()
        result = 31 * result + (generatedAudioFile?.hashCode() ?: 0)
        result = 31 * result + playerState.hashCode()
        result = 31 * result + waveformAmplitudes.contentHashCode()
        result = 31 * result + (sessionMetrics?.hashCode() ?: 0)
        result = 31 * result + logs.hashCode()
        result = 31 * result + logFilter.hashCode()
        result = 31 * result + (statusMessage?.hashCode() ?: 0)
        result = 31 * result + showVoicePicker.hashCode()
        result = 31 * result + showDiagnostics.hashCode()
        return result
    }
}
