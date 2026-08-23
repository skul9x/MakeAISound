package skul9x.example.makesound.player

/**
 * Synchronizes visualizer active bar highlighting and scrubber seek positions with player's elapsed time.
 */
object PlaybackProgressTracker {

    /**
     * Calculates the active bar index (0 until barCount) corresponding to current playback position.
     */
    fun calculateActiveBarIndex(currentPositionMs: Long, durationMs: Long, barCount: Int): Int {
        if (durationMs <= 0L || barCount <= 0) return 0
        val progress = (currentPositionMs.toDouble() / durationMs.toDouble()).coerceIn(0.0, 1.0)
        val index = (progress * barCount).toInt()
        return index.coerceIn(0, barCount - 1)
    }

    /**
     * Calculates playback progress ratio strictly within [0.0f, 1.0f].
     */
    fun calculateProgress(currentPositionMs: Long, durationMs: Long): Float {
        if (durationMs <= 0L) return 0f
        return (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    }

    /**
     * Computes target seek timestamp in milliseconds given a normalized scrub fraction [0.0f, 1.0f].
     */
    fun calculateSeekPosition(fraction: Float, durationMs: Long): Long {
        if (durationMs <= 0L) return 0L
        return (fraction.coerceIn(0f, 1f) * durationMs).toLong().coerceIn(0L, durationMs)
    }
}
