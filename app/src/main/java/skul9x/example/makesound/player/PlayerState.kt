package skul9x.example.makesound.player

/**
 * Lifecycle states of the audio player.
 */
enum class PlaybackStatus {
    IDLE,
    PREPARED,
    PLAYING,
    PAUSED,
    COMPLETED,
    ERROR
}

/**
 * Immutable data state representing current playback status, timings, and progress.
 */
data class PlayerState(
    val status: PlaybackStatus = PlaybackStatus.IDLE,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val audioFilePath: String? = null,
    val errorMessage: String? = null
) {
    val isPlaying: Boolean
        get() = status == PlaybackStatus.PLAYING

    val isPaused: Boolean
        get() = status == PlaybackStatus.PAUSED

    val isPrepared: Boolean
        get() = status == PlaybackStatus.PREPARED ||
                status == PlaybackStatus.PLAYING ||
                status == PlaybackStatus.PAUSED ||
                status == PlaybackStatus.COMPLETED

    /**
     * Normalized progress ratio strictly bounded in [0.0, 1.0].
     */
    val progress: Float
        get() = if (durationMs > 0L) {
            (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }

    val progressFraction: Float
        get() = progress

    val formattedPosition: String
        get() = formatTime(currentPositionMs)

    val formattedCurrentPosition: String
        get() = formattedPosition

    val formattedDuration: String
        get() = formatTime(durationMs)

    /**
     * Standard human-readable readout (e.g., "00:15 / 00:45").
     */
    val formattedTimeDisplay: String
        get() = "$formattedPosition / $formattedDuration"

    companion object {
        val IDLE = PlayerState()

        /**
         * Formats duration in milliseconds into standard "mm:ss" display string.
         */
        fun formatTime(millis: Long): String {
            val totalSeconds = (millis / 1000).coerceAtLeast(0)
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return String.format("%02d:%02d", minutes, seconds)
        }
    }
}
