package skul9x.example.makesound.ui

import androidx.compose.ui.text.input.TextFieldValue
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
 * Multi-dimensional voice filter enums.
 */
enum class RegionFilter(val displayName: String) {
    ALL("Tất cả"),
    NORTH("Bắc"),
    CENTRAL("Trung"),
    SOUTH("Nam")
}

enum class GenderFilter(val displayName: String) {
    ALL("Tất cả"),
    FEMALE("Nữ"),
    MALE("Nam")
}

enum class StyleFilter(val displayName: String) {
    ALL("Tất cả"),
    NATURAL("Tự nhiên"),
    STORY("Kể chuyện / Đọc truyện"),
    NEWS("Tin tức")
}

/**
 * Filter categories for voice selection modal (legacy single-dimension filter).
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
    val textFieldValue: TextFieldValue = TextFieldValue(""),
    val selectedVoice: VoicePreset? = null,
    val availableVoices: List<VoicePreset> = emptyList(),
    val selectedVoiceFilter: VoiceFilter = VoiceFilter.ALL,
    val selectedRegionFilter: RegionFilter = RegionFilter.ALL,
    val selectedGenderFilter: GenderFilter = GenderFilter.ALL,
    val selectedStyleFilter: StyleFilter = StyleFilter.ALL,
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
    val text: String
        get() = textFieldValue.text

    val charCount: Int
        get() = text.length

    val estimatedDurationSeconds: Double
        get() = if (text.isBlank()) 0.0 else (text.length / 18.0).coerceAtLeast(1.0)

    val isGenerating: Boolean
        get() = generationState is GenerationState.Generating

    val canGenerate: Boolean
        get() = text.isNotBlank() && !isGenerating && selectedVoice != null

    val isVoiceFilterActive: Boolean
        get() = selectedRegionFilter != RegionFilter.ALL ||
                selectedGenderFilter != GenderFilter.ALL ||
                selectedStyleFilter != StyleFilter.ALL ||
                selectedVoiceFilter != VoiceFilter.ALL

    val activeVoiceFilterCount: Int
        get() = (if (selectedRegionFilter != RegionFilter.ALL) 1 else 0) +
                (if (selectedGenderFilter != GenderFilter.ALL) 1 else 0) +
                (if (selectedStyleFilter != StyleFilter.ALL) 1 else 0) +
                (if (selectedVoiceFilter != VoiceFilter.ALL && selectedRegionFilter == RegionFilter.ALL && selectedGenderFilter == GenderFilter.ALL && selectedStyleFilter == StyleFilter.ALL) 1 else 0)

    val filteredVoices: List<VoicePreset>
        get() = availableVoices.filter { preset ->
            val regionMatch = when (selectedRegionFilter) {
                RegionFilter.ALL -> true
                RegionFilter.NORTH -> preset.region.equals("Bắc", ignoreCase = true) ||
                        preset.region.equals("Miền Bắc", ignoreCase = true) ||
                        preset.regionDisplay.contains("Bắc", ignoreCase = true) ||
                        preset.description.contains("· Bắc", ignoreCase = true) ||
                        preset.description.contains("Miền Bắc", ignoreCase = true)
                RegionFilter.CENTRAL -> preset.region.equals("Trung", ignoreCase = true) ||
                        preset.region.equals("Miền Trung", ignoreCase = true) ||
                        preset.regionDisplay.contains("Trung", ignoreCase = true) ||
                        preset.description.contains("· Trung", ignoreCase = true) ||
                        preset.description.contains("Miền Trung", ignoreCase = true)
                RegionFilter.SOUTH -> preset.region.equals("Nam", ignoreCase = true) ||
                        preset.region.equals("Miền Nam", ignoreCase = true) ||
                        preset.regionDisplay.contains("Miền Nam", ignoreCase = true) ||
                        preset.description.contains("· Nam", ignoreCase = true) ||
                        preset.description.contains("Miền Nam", ignoreCase = true)
            }

            val genderMatch = when (selectedGenderFilter) {
                GenderFilter.ALL -> true
                GenderFilter.FEMALE -> preset.gender.equals("female", ignoreCase = true) ||
                        preset.genderDisplay.equals("Nữ", ignoreCase = true) ||
                        preset.description.startsWith("Nữ", ignoreCase = true)
                GenderFilter.MALE -> preset.gender.equals("male", ignoreCase = true) ||
                        preset.genderDisplay.equals("Nam", ignoreCase = true) ||
                        (preset.description.startsWith("Nam", ignoreCase = true) && !preset.description.startsWith("Nữ", ignoreCase = true))
            }

            val styleMatch = when (selectedStyleFilter) {
                StyleFilter.ALL -> true
                StyleFilter.NATURAL -> preset.style in listOf("tu_nhien", "tunhien", "natural") ||
                        preset.styleDisplay.contains("tự nhiên", ignoreCase = true) ||
                        preset.description.contains("tự nhiên", ignoreCase = true)
                StyleFilter.STORY -> preset.style in listOf("doc_truyen", "doctruyen", "story", "kể chuyện", "ke_chuyen") ||
                        preset.styleDisplay.contains("kể chuyện", ignoreCase = true) ||
                        preset.styleDisplay.contains("đọc truyện", ignoreCase = true) ||
                        preset.description.contains("kể chuyện", ignoreCase = true) ||
                        preset.description.contains("đọc truyện", ignoreCase = true)
                StyleFilter.NEWS -> preset.style in listOf("tin_tuc", "tintuc", "news") ||
                        preset.styleDisplay.contains("tin tức", ignoreCase = true) ||
                        preset.description.contains("tin tức", ignoreCase = true)
            }

            val legacyMatch = when (selectedVoiceFilter) {
                VoiceFilter.ALL -> true
                VoiceFilter.NORTH -> preset.region.equals("Bắc", ignoreCase = true) ||
                        preset.region.equals("Miền Bắc", ignoreCase = true) ||
                        preset.regionDisplay.contains("Bắc", ignoreCase = true) ||
                        preset.description.contains("Miền Bắc", ignoreCase = true) ||
                        preset.description.contains("· Bắc", ignoreCase = true)
                VoiceFilter.CENTRAL -> preset.region.equals("Trung", ignoreCase = true) ||
                        preset.region.equals("Miền Trung", ignoreCase = true) ||
                        preset.regionDisplay.contains("Trung", ignoreCase = true) ||
                        preset.description.contains("Miền Trung", ignoreCase = true) ||
                        preset.description.contains("· Trung", ignoreCase = true)
                VoiceFilter.SOUTH -> preset.region.equals("Nam", ignoreCase = true) ||
                        preset.region.equals("Miền Nam", ignoreCase = true) ||
                        preset.regionDisplay.contains("Miền Nam", ignoreCase = true) ||
                        preset.description.contains("Miền Nam", ignoreCase = true) ||
                        preset.description.contains("· Nam", ignoreCase = true)
                VoiceFilter.MALE -> preset.gender.equals("male", ignoreCase = true) ||
                        preset.genderDisplay.equals("Nam", ignoreCase = true)
                VoiceFilter.FEMALE -> preset.gender.equals("female", ignoreCase = true) ||
                        preset.genderDisplay.equals("Nữ", ignoreCase = true)
            }

            regionMatch && genderMatch && styleMatch && legacyMatch
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
        if (textFieldValue != other.textFieldValue) return false
        if (selectedVoice != other.selectedVoice) return false
        if (availableVoices != other.availableVoices) return false
        if (selectedVoiceFilter != other.selectedVoiceFilter) return false
        if (selectedRegionFilter != other.selectedRegionFilter) return false
        if (selectedGenderFilter != other.selectedGenderFilter) return false
        if (selectedStyleFilter != other.selectedStyleFilter) return false
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
        var result = textFieldValue.hashCode()
        result = 31 * result + (selectedVoice?.hashCode() ?: 0)
        result = 31 * result + availableVoices.hashCode()
        result = 31 * result + selectedVoiceFilter.hashCode()
        result = 31 * result + selectedRegionFilter.hashCode()
        result = 31 * result + selectedGenderFilter.hashCode()
        result = 31 * result + selectedStyleFilter.hashCode()
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
