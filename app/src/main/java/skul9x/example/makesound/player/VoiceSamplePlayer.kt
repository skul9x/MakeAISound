package skul9x.example.makesound.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileOutputStream

/**
 * Immutable data state representing voice sample preview playback state.
 */
data class VoicePlayerState(
    val currentVoiceName: String? = null,
    val isPlaying: Boolean = false,
    val error: String? = null
) {
    val isIdle: Boolean
        get() = currentVoiceName == null && !isPlaying && error == null

    companion object {
        val IDLE = VoicePlayerState()
    }
}

/**
 * Lightweight, independent audio player subsystem dedicated to instantaneous preview playback
 * inside the Voice Picker Studio.
 *
 * Guarantees:
 * 1. Single-active sample constraint: automatically halts active playback when a new voice is auditioned.
 * 2. Independent state management: maintains dedicated [VoicePlayerState] without interfering with
 *    the main studio [AudioPlayerManager].
 * 3. Safe byte buffer & disk playback: generates and cleans up temporary preview files with zero leaks.
 * 4. Deterministic testability: delegates underlying playback to [MediaPlayerAdapter].
 */
class VoiceSamplePlayer(
    private val playerAdapter: MediaPlayerAdapter = AndroidMediaPlayerAdapter()
) {
    private val _playerState = MutableStateFlow(VoicePlayerState.IDLE)
    val playerState: StateFlow<VoicePlayerState> = _playerState.asStateFlow()

    val currentVoiceName: String?
        get() = _playerState.value.currentVoiceName

    val isPlaying: Boolean
        get() = _playerState.value.isPlaying

    private var currentTempFile: File? = null

    init {
        setupListeners()
    }

    private fun setupListeners() {
        playerAdapter.setOnCompletionListener {
            _playerState.value = VoicePlayerState(
                currentVoiceName = null,
                isPlaying = false,
                error = null
            )
        }

        playerAdapter.setOnErrorListener { what, extra ->
            _playerState.value = VoicePlayerState(
                currentVoiceName = null,
                isPlaying = false,
                error = "Playback error (what: $what, extra: $extra)"
            )
            true
        }
    }

    /**
     * Plays an audio sample from a local file path or asset descriptor path.
     * Automatically stops any currently playing sample (single-active mechanism).
     *
     * @param voiceName Identifying name of the voice preset being previewed.
     * @param audioPath Absolute file path or accessible audio source path.
     * @return true if playback initiated successfully, false otherwise.
     */
    fun playVoiceSample(voiceName: String, audioPath: String): Boolean {
        if (audioPath.isBlank()) {
            _playerState.value = VoicePlayerState(
                currentVoiceName = null,
                isPlaying = false,
                error = "Invalid audio path for voice: $voiceName"
            )
            return false
        }

        return try {
            stopVoiceSample()

            if (currentTempFile != null && currentTempFile?.absolutePath != audioPath) {
                cleanupTempFile()
            }

            playerAdapter.reset()
            setupListeners()
            playerAdapter.setDataSource(audioPath)
            playerAdapter.prepare()
            playerAdapter.start()

            _playerState.value = VoicePlayerState(
                currentVoiceName = voiceName,
                isPlaying = true,
                error = null
            )
            true
        } catch (e: Throwable) {
            _playerState.value = VoicePlayerState(
                currentVoiceName = null,
                isPlaying = false,
                error = e.message ?: "Failed to play voice sample: $voiceName"
            )
            false
        }
    }

    /**
     * Overload for playing directly from a [File] handle.
     */
    fun playVoiceSample(voiceName: String, file: File): Boolean = playVoiceSample(voiceName, file.absolutePath)

    /**
     * Writes in-memory audio bytes to a cache directory and plays the resulting audio clip.
     * Ensures preceding temporary preview files are safely removed.
     *
     * @param voiceName Identifying name of the voice preset.
     * @param wavBytes PCM/WAV encoded audio byte buffer.
     * @param cacheDir Target directory for transient preview playback files.
     * @return true if byte caching and playback start cleanly, false otherwise.
     */
    fun playVoiceBytes(voiceName: String, wavBytes: ByteArray, cacheDir: File): Boolean {
        if (wavBytes.isEmpty()) {
            _playerState.value = VoicePlayerState(
                currentVoiceName = null,
                isPlaying = false,
                error = "Cannot play empty audio bytes for voice: $voiceName"
            )
            return false
        }

        return try {
            cleanupTempFile()
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }
            val safeName = voiceName.replace("[^a-zA-Z0-9_\\-]".toRegex(), "_")
            val tempFile = File(cacheDir, "preview_sample_${safeName}_${System.nanoTime()}.wav")
            FileOutputStream(tempFile).use { fos ->
                fos.write(wavBytes)
                fos.flush()
            }
            currentTempFile = tempFile
            playVoiceSample(voiceName, tempFile.absolutePath)
        } catch (e: Throwable) {
            _playerState.value = VoicePlayerState(
                currentVoiceName = null,
                isPlaying = false,
                error = e.message ?: "Failed to write voice sample bytes for: $voiceName"
            )
            false
        }
    }

    /**
     * Pauses the active voice sample playback while retaining the current voice name.
     */
    fun pauseVoiceSample(): Boolean {
        return try {
            if (playerAdapter.isPlaying()) {
                playerAdapter.pause()
            }
            _playerState.value = _playerState.value.copy(isPlaying = false)
            true
        } catch (e: Throwable) {
            _playerState.value = _playerState.value.copy(
                isPlaying = false,
                error = e.message ?: "Failed to pause voice sample"
            )
            false
        }
    }

    /**
     * Resumes the paused voice sample preview.
     */
    fun resumeVoiceSample(): Boolean {
        val current = _playerState.value
        if (current.currentVoiceName != null && !current.isPlaying) {
            return try {
                playerAdapter.start()
                _playerState.value = current.copy(isPlaying = true, error = null)
                true
            } catch (e: Throwable) {
                _playerState.value = current.copy(isPlaying = false, error = e.message)
                false
            }
        }
        return false
    }

    /**
     * Stops current preview playback and clears active voice name.
     */
    fun stopVoiceSample() {
        try {
            if (playerAdapter.isPlaying()) {
                playerAdapter.stop()
            }
        } catch (_: Throwable) {}

        _playerState.value = VoicePlayerState(
            currentVoiceName = null,
            isPlaying = false,
            error = null
        )
    }

    /**
     * Standard alias methods for convenient API consumption.
     */
    fun pause(): Boolean = pauseVoiceSample()
    fun resume(): Boolean = resumeVoiceSample()
    fun stop() = stopVoiceSample()

    /**
     * Completely releases player adapter resources and cleans up any transient files.
     */
    fun release() {
        stopVoiceSample()
        try {
            playerAdapter.release()
        } catch (_: Throwable) {}
        cleanupTempFile()
        _playerState.value = VoicePlayerState.IDLE
    }

    /**
     * Package-private / test inspection helper for active temporary file.
     */
    internal fun getActiveTempFile(): File? = currentTempFile

    private fun cleanupTempFile() {
        try {
            currentTempFile?.let { file ->
                if (file.exists()) {
                    file.delete()
                }
            }
        } catch (_: Throwable) {}
        currentTempFile = null
    }
}
