package skul9x.example.makesound.player

import android.media.MediaPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/**
 * Abstraction layer over Android MediaPlayer allowing injection for deterministic testing.
 */
interface MediaPlayerAdapter {
    fun setDataSource(path: String)
    fun prepare()
    fun start()
    fun pause()
    fun stop()
    fun seekTo(msec: Int)
    fun isPlaying(): Boolean
    fun getCurrentPosition(): Int
    fun getDuration(): Int
    fun reset()
    fun release()
    fun setOnCompletionListener(listener: (() -> Unit)?)
    fun setOnErrorListener(listener: ((what: Int, extra: Int) -> Boolean)?)
}

/**
 * Standard implementation wrapping [android.media.MediaPlayer].
 */
class AndroidMediaPlayerAdapter : MediaPlayerAdapter {
    private var mediaPlayer: MediaPlayer? = null

    private fun getOrCreatePlayer(): MediaPlayer {
        return mediaPlayer ?: MediaPlayer().also { mediaPlayer = it }
    }

    override fun setDataSource(path: String) {
        getOrCreatePlayer().setDataSource(path)
    }

    override fun prepare() {
        getOrCreatePlayer().prepare()
    }

    override fun start() {
        try {
            mediaPlayer?.start()
        } catch (_: Throwable) {}
    }

    override fun pause() {
        try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.pause()
            }
        } catch (_: Throwable) {}
    }

    override fun stop() {
        try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.stop()
            }
        } catch (_: Throwable) {}
    }

    override fun seekTo(msec: Int) {
        try {
            mediaPlayer?.seekTo(msec)
        } catch (_: Throwable) {}
    }

    override fun isPlaying(): Boolean {
        return mediaPlayer?.isPlaying ?: false
    }

    override fun getCurrentPosition(): Int {
        return mediaPlayer?.currentPosition ?: 0
    }

    override fun getDuration(): Int {
        return mediaPlayer?.duration ?: 0
    }

    override fun reset() {
        mediaPlayer?.reset()
    }

    override fun release() {
        mediaPlayer?.release()
        mediaPlayer = null
    }

    override fun setOnCompletionListener(listener: (() -> Unit)?) {
        getOrCreatePlayer().setOnCompletionListener {
            listener?.invoke()
        }
    }

    override fun setOnErrorListener(listener: ((what: Int, extra: Int) -> Boolean)?) {
        getOrCreatePlayer().setOnErrorListener { _, what, extra ->
            listener?.invoke(what, extra) ?: false
        }
    }
}

/**
 * Audio playback controller managing MediaPlayer lifecycle, responsive playback controls,
 * state machine transitions, and smooth coroutine progress polling (~20fps / 50ms).
 */
class AudioPlayerManager(
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob()),
    private val playerAdapter: MediaPlayerAdapter = AndroidMediaPlayerAdapter()
) {
    private val _playerState = MutableStateFlow(PlayerState.IDLE)
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    private var progressJob: Job? = null

    init {
        setupListeners()
    }

    private fun setupListeners() {
        playerAdapter.setOnCompletionListener {
            stopProgressPolling()
            _playerState.value = _playerState.value.copy(
                status = PlaybackStatus.COMPLETED,
                currentPositionMs = _playerState.value.durationMs
            )
        }

        playerAdapter.setOnErrorListener { what, extra ->
            stopProgressPolling()
            _playerState.value = _playerState.value.copy(
                status = PlaybackStatus.ERROR,
                errorMessage = "Playback error (what: $what, extra: $extra)"
            )
            true
        }
    }

    /**
     * Loads a local audio file and prepares it for playback.
     */
    fun load(filePath: String): Boolean {
        return try {
            stopProgressPolling()
            playerAdapter.reset()
            setupListeners()
            playerAdapter.setDataSource(filePath)
            playerAdapter.prepare()

            val duration = playerAdapter.getDuration().toLong().coerceAtLeast(0L)
            _playerState.value = PlayerState(
                status = PlaybackStatus.PREPARED,
                currentPositionMs = 0L,
                durationMs = duration,
                audioFilePath = filePath,
                errorMessage = null
            )
            true
        } catch (e: Throwable) {
            _playerState.value = PlayerState(
                status = PlaybackStatus.ERROR,
                audioFilePath = filePath,
                errorMessage = e.message ?: "Failed to load audio file"
            )
            false
        }
    }

    fun load(file: File): Boolean = load(file.absolutePath)

    /**
     * Starts or resumes playback.
     */
    fun play(): Boolean {
        val current = _playerState.value
        return when (current.status) {
            PlaybackStatus.PREPARED, PlaybackStatus.PAUSED -> {
                try {
                    playerAdapter.start()
                    _playerState.value = current.copy(status = PlaybackStatus.PLAYING)
                    startProgressPolling()
                    true
                } catch (e: Throwable) {
                    _playerState.value = current.copy(
                        status = PlaybackStatus.ERROR,
                        errorMessage = e.message
                    )
                    false
                }
            }
            PlaybackStatus.COMPLETED -> {
                try {
                    seekTo(0L)
                    playerAdapter.start()
                    _playerState.value = current.copy(
                        status = PlaybackStatus.PLAYING,
                        currentPositionMs = 0L
                    )
                    startProgressPolling()
                    true
                } catch (e: Throwable) {
                    _playerState.value = current.copy(
                        status = PlaybackStatus.ERROR,
                        errorMessage = e.message
                    )
                    false
                }
            }
            PlaybackStatus.PLAYING -> true
            else -> false
        }
    }

    /**
     * Pauses current playback.
     */
    fun pause(): Boolean {
        val current = _playerState.value
        if (current.status == PlaybackStatus.PLAYING) {
            return try {
                playerAdapter.pause()
                stopProgressPolling()
                val pos = playerAdapter.getCurrentPosition().toLong()
                _playerState.value = current.copy(
                    status = PlaybackStatus.PAUSED,
                    currentPositionMs = pos
                )
                true
            } catch (e: Throwable) {
                _playerState.value = current.copy(
                    status = PlaybackStatus.ERROR,
                    errorMessage = e.message
                )
                false
            }
        }
        return false
    }

    /**
     * Alias for [play] when paused.
     */
    fun resume(): Boolean = play()

    /**
     * Replays audio from the beginning.
     */
    fun replay(): Boolean {
        seekTo(0L)
        return play()
    }

    /**
     * Precision seeking to target position in milliseconds.
     */
    fun seekTo(positionMs: Long) {
        val current = _playerState.value
        if (current.isPrepared) {
            val clamped = positionMs.coerceIn(0L, current.durationMs)
            try {
                playerAdapter.seekTo(clamped.toInt())
                _playerState.value = current.copy(currentPositionMs = clamped)
            } catch (_: Throwable) {
                // Ignore seek exceptions during rapid scrubbing
            }
        }
    }

    /**
     * Seeks to a normalized fraction [0.0f, 1.0f] of total duration.
     */
    fun seekToFraction(fraction: Float) {
        val duration = _playerState.value.durationMs
        val targetMs = PlaybackProgressTracker.calculateSeekPosition(fraction, duration)
        seekTo(targetMs)
    }

    /**
     * Stops playback and returns position to start.
     */
    fun stop() {
        stopProgressPolling()
        try {
            playerAdapter.stop()
        } catch (_: Throwable) {}
        _playerState.value = _playerState.value.copy(
            status = PlaybackStatus.PREPARED,
            currentPositionMs = 0L
        )
    }

    /**
     * Releases MediaPlayer and cancels coroutine scope.
     */
    fun release() {
        stopProgressPolling()
        try {
            playerAdapter.release()
        } catch (_: Throwable) {}
        _playerState.value = PlayerState.IDLE
    }

    private fun startProgressPolling() {
        stopProgressPolling()
        progressJob = coroutineScope.launch {
            while (isActive && _playerState.value.status == PlaybackStatus.PLAYING) {
                val currentPos = playerAdapter.getCurrentPosition().toLong()
                _playerState.value = _playerState.value.copy(currentPositionMs = currentPos)
                delay(50L) // ~20fps responsive & energy-efficient updates
            }
        }
    }

    private fun stopProgressPolling() {
        progressJob?.cancel()
        progressJob = null
    }
}
