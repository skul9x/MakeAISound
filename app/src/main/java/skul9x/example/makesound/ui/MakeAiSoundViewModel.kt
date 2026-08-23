package skul9x.example.makesound.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import skul9x.example.makesound.engine.SmartTextSegmenter
import skul9x.example.makesound.engine.SynthesizedAudioResult
import skul9x.example.makesound.engine.VieNeuConfig
import skul9x.example.makesound.engine.VieNeuStudioSynthesizer
import skul9x.example.makesound.engine.VoicePreset
import skul9x.example.makesound.engine.VoicePresets
import skul9x.example.makesound.player.AudioPlayerManager
import skul9x.example.makesound.player.PlaybackStatus
import skul9x.example.makesound.player.WaveformSampler
import skul9x.example.makesound.storage.AudioStorageManager
import skul9x.example.makesound.storage.ShareHelper
import skul9x.example.makesound.telemetry.StudioLogger
import java.io.File

/**
 * ViewModel orchestrating studio UI state, background synthesis, audio playback,
 * MediaStore export, diagnostic telemetry, and clipboard interactions.
 */
open class MakeAiSoundViewModel(
    private val synthesizer: VieNeuStudioSynthesizer? = null,
    val playerManager: AudioPlayerManager = AudioPlayerManager(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    customScope: kotlinx.coroutines.CoroutineScope? = null
) : ViewModel() {

    private val scope: kotlinx.coroutines.CoroutineScope = customScope ?: viewModelScope

    private val _uiState = MutableStateFlow(StudioUiState())
    val uiState: StateFlow<StudioUiState> = _uiState.asStateFlow()

    private var generationJob: Job? = null

    init {
        // Collect reactive logs
        scope.launch {
            StudioLogger.logs.collect { newLogs ->
                _uiState.update { it.copy(logs = newLogs) }
            }
        }

        // Collect player state
        scope.launch {
            playerManager.playerState.collect { newPlayerState ->
                _uiState.update { it.copy(playerState = newPlayerState) }
            }
        }
    }

    /**
     * Initializer for default available voices.
     */
    fun loadVoices(context: Context? = null) {
        if (context != null && VoicePresets.getVoicePresets().isEmpty()) {
            VoicePresets.loadFromContext(context)
        }

        val presets = VoicePresets.getVoicePresets()
        val defaultPreset = VoicePresets.getVoice(VoicePresets.defaultVoiceName) ?: presets.firstOrNull()

        _uiState.update { current ->
            current.copy(
                availableVoices = presets,
                selectedVoice = current.selectedVoice ?: defaultPreset
            )
        }
    }

    fun setAvailableVoices(voices: List<VoicePreset>) {
        _uiState.update { current ->
            current.copy(
                availableVoices = voices,
                selectedVoice = current.selectedVoice ?: voices.firstOrNull()
            )
        }
    }

    fun onTextChanged(newText: String) {
        val trimmed = if (newText.length > 5000) newText.take(5000) else newText
        _uiState.update { it.copy(text = trimmed) }
    }

    fun onPasteFromClipboard(pastedText: String) {
        val current = _uiState.value.text
        val combined = if (current.isEmpty()) pastedText else "$current $pastedText"
        onTextChanged(combined)
    }

    fun readClipboardAndPaste(context: Context) {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clip = clipboard?.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val pasteText = clip.getItemAt(0).text?.toString() ?: ""
                if (pasteText.isNotBlank()) {
                    onPasteFromClipboard(pasteText)
                    setStatusMessage("Đã dán từ bộ nhớ tạm")
                }
            }
        } catch (e: Exception) {
            StudioLogger.e(TAG, "Failed to read clipboard: ${e.message}")
        }
    }

    fun copyTextToClipboard(context: Context) {
        try {
            val text = _uiState.value.text
            if (text.isBlank()) return
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clip = ClipData.newPlainText("MakeAiSound Text", text)
            clipboard?.setPrimaryClip(clip)
            setStatusMessage("Đã sao chép văn bản vào bộ nhớ tạm")
        } catch (e: Exception) {
            StudioLogger.e(TAG, "Failed to copy text: ${e.message}")
        }
    }

    fun onClearText() {
        _uiState.update { it.copy(text = "") }
    }

    fun insertEmotionTag(tag: String) {
        val current = _uiState.value.text
        val formatted = if (tag.startsWith("[") && tag.endsWith("]")) tag else "[$tag]"
        val newText = if (current.isEmpty()) {
            "$formatted "
        } else if (current.endsWith(" ")) {
            "$current$formatted "
        } else {
            "$current $formatted "
        }
        onTextChanged(newText)
    }

    fun onVoiceSelected(preset: VoicePreset) {
        _uiState.update {
            it.copy(
                selectedVoice = preset,
                showVoicePicker = false
            )
        }
        StudioLogger.i(TAG, "Selected voice: ${preset.name}")
    }

    fun setVoiceFilter(filter: VoiceFilter) {
        _uiState.update { it.copy(selectedVoiceFilter = filter) }
    }

    fun showVoicePicker(show: Boolean) {
        _uiState.update { it.copy(showVoicePicker = show) }
    }

    fun showDiagnostics(show: Boolean) {
        _uiState.update { it.copy(showDiagnostics = show) }
    }

    fun setLogFilter(filter: String) {
        _uiState.update { it.copy(logFilter = filter) }
    }

    fun clearLogs() {
        StudioLogger.clear()
        _uiState.update { it.copy(logs = emptyList()) }
        setStatusMessage("Đã xóa nhật ký hoạt động")
    }

    fun copyLogsToClipboard(context: Context) {
        try {
            val logsString = StudioLogger.exportLogsToString()
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clip = ClipData.newPlainText("MakeAiSound Logs", logsString)
            clipboard?.setPrimaryClip(clip)
            setStatusMessage("Đã sao chép toàn bộ logs vào bộ nhớ tạm")
        } catch (e: Exception) {
            StudioLogger.e(TAG, "Failed to copy logs: ${e.message}")
        }
    }

    fun setStatusMessage(msg: String?) {
        _uiState.update { it.copy(statusMessage = msg) }
    }

    fun clearStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    /**
     * Triggers batch synthesis and saves the preview WAV file.
     */
    fun generateAudio(context: Context, maxCharsPerChunk: Int = SmartTextSegmenter.DEFAULT_MAX_CHARS) {
        val currentState = _uiState.value
        val text = currentState.text.trim()
        val voice = currentState.selectedVoice ?: return

        if (text.isEmpty() || currentState.isGenerating) return

        generationJob?.cancel()
        playerManager.stop()

        _uiState.update {
            it.copy(
                generationState = GenerationState.Generating(chunkIndex = 0, totalChunks = 0, percent = 0f)
            )
        }

        generationJob = viewModelScope.launch {
            try {
                val synth = synthesizer
                val result: SynthesizedAudioResult = if (synth != null) {
                    synth.synthesize(
                        text = text,
                        voiceName = voice.name,
                        maxCharsPerChunk = maxCharsPerChunk,
                        onProgress = { chunkIndex, totalChunks, percent, chunkMetrics ->
                            _uiState.update {
                                it.copy(
                                    generationState = GenerationState.Generating(
                                        chunkIndex = chunkIndex,
                                        totalChunks = totalChunks,
                                        percent = percent,
                                        currentMetrics = chunkMetrics
                                    )
                                )
                            }
                        }
                    )
                } else {
                    // Fallback / Stub generation when synthesizer is not injected (e.g. preview/mock)
                    delayMockGeneration(text, voice)
                }

                // Save to preview cache
                val audioFile = AudioStorageManager.saveToCache(
                    context = context,
                    pcm16 = result.pcm16Data,
                    sampleRate = result.sampleRate,
                    prefix = "studio_${voice.name}"
                )

                // Compute waveform amplitudes
                val amplitudes = WaveformSampler.samplePcm16(result.pcm16Data, barCount = 48)

                // Load to player manager
                playerManager.load(audioFile)

                _uiState.update {
                    it.copy(
                        generationState = GenerationState.Success(result, audioFile),
                        generatedAudioFile = audioFile,
                        waveformAmplitudes = amplitudes,
                        sessionMetrics = result.metrics,
                        statusMessage = "Tạo âm thanh thành công! (${String.format("%.2f", result.durationSeconds)}s)"
                    )
                }
            } catch (e: CancellationException) {
                StudioLogger.w(TAG, "Speech generation cancelled by user")
                _uiState.update {
                    it.copy(
                        generationState = GenerationState.Idle,
                        statusMessage = "Đã hủy quá trình tạo âm thanh"
                    )
                }
            } catch (e: Throwable) {
                StudioLogger.e(TAG, "Speech generation failed: ${e.message}", e)
                _uiState.update {
                    it.copy(
                        generationState = GenerationState.Error(e.message ?: "Lỗi tạo âm thanh"),
                        statusMessage = "Lỗi: ${e.message}"
                    )
                }
            }
        }
    }

    fun cancelGeneration() {
        if (_uiState.value.isGenerating) {
            generationJob?.cancel()
            generationJob = null
            _uiState.update {
                it.copy(
                    generationState = GenerationState.Idle,
                    statusMessage = "Đã dừng tạo âm thanh"
                )
            }
        }
    }

    private suspend fun delayMockGeneration(text: String, voice: VoicePreset): SynthesizedAudioResult = withContext(ioDispatcher) {
        val sampleRate = VieNeuConfig.SAMPLE_RATE
        val durationSamples = (sampleRate * 2).coerceAtLeast(sampleRate)
        val pcm = ShortArray(durationSamples) { (Math.sin(it.toDouble() * 0.05) * 8000).toInt().toShort() }
        val sessionMetrics = skul9x.example.makesound.telemetry.SessionPerformanceMetrics(
            sessionId = "Mock_${System.currentTimeMillis()}",
            voiceName = voice.name,
            totalChars = text.length,
            totalPhonemes = text.length * 2,
            totalSynthesisDurationMs = 150L,
            totalAudioDurationMs = 2000L,
            averageRtf = 0.075f,
            peakMemoryMb = 128.0
        )
        SynthesizedAudioResult(
            pcm16Data = pcm,
            sampleRate = sampleRate,
            metrics = sessionMetrics,
            textChunks = emptyList()
        )
    }

    // Playback Controls
    fun playAudio() {
        playerManager.play()
    }

    fun pauseAudio() {
        playerManager.pause()
    }

    fun replayAudio() {
        playerManager.replay()
    }

    fun seekTo(positionMs: Long) {
        playerManager.seekTo(positionMs)
    }

    fun seekToFraction(fraction: Float) {
        playerManager.seekToFraction(fraction)
    }

    // Export & Sharing
    fun exportToStorage(context: Context) {
        val audioFile = _uiState.value.generatedAudioFile
        val speaker = _uiState.value.selectedVoice?.name ?: "Voice"
        if (audioFile == null || !audioFile.exists()) {
            setStatusMessage("Chưa có file âm thanh để lưu!")
            return
        }

        scope.launch {
            try {
                val uri = AudioStorageManager.exportToMediaStore(
                    context = context,
                    wavFile = audioFile,
                    speakerName = speaker
                )
                if (uri != null) {
                    StudioLogger.i(TAG, "Exported audio to MediaStore: $uri")
                    setStatusMessage("💾 Đã lưu vào Thư viện Nhạc (Music/MakeAiSound)")
                } else {
                    setStatusMessage("Không thể xuất file vào MediaStore")
                }
            } catch (e: Exception) {
                StudioLogger.e(TAG, "Failed to export audio: ${e.message}", e)
                setStatusMessage("Lỗi khi lưu file: ${e.message}")
            }
        }
    }

    fun shareAudio(context: Context) {
        val audioFile = _uiState.value.generatedAudioFile
        if (audioFile == null || !audioFile.exists()) {
            setStatusMessage("Chưa có file âm thanh để chia sẻ!")
            return
        }
        try {
            ShareHelper.shareAudio(context, audioFile, chooserTitle = "Chia sẻ âm thanh AI")
        } catch (e: Exception) {
            StudioLogger.e(TAG, "Failed to share audio: ${e.message}", e)
            setStatusMessage("Lỗi khi chia sẻ: ${e.message}")
        }
    }

    override fun onCleared() {
        super.onCleared()
        generationJob?.cancel()
        playerManager.release()
        try {
            (synthesizer as? AutoCloseable)?.close()
        } catch (_: Exception) {}
    }

    companion object {
        private const val TAG = "MakeAiSoundViewModel"
    }
}
