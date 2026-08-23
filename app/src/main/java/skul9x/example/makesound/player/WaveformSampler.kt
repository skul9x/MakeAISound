package skul9x.example.makesound.player

import skul9x.example.makesound.storage.WavWriter
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Downsampler generating normalized amplitude arrays ([0.0f, 1.0f]) from raw PCM audio buffers
 * to drive waveform visualizer bars in Compose UI.
 */
object WaveformSampler {

    private const val MAX_PCM16_VALUE = 32767.0

    /**
     * Downsamples a 16-bit PCM [ShortArray] into [barCount] normalized amplitude bars.
     *
     * @param pcm Raw 16-bit linear PCM audio samples.
     * @param barCount Number of output amplitude bars to produce.
     * @param useRms If true, calculates Root-Mean-Square amplitude per window. If false, calculates peak amplitude.
     * @param normalizeToPeak If true, scales output values relative to the maximum observed peak in the audio.
     * @return FloatArray of size [barCount], with each element strictly in [0.0f, 1.0f].
     */
    fun samplePcm16(
        pcm: ShortArray,
        barCount: Int,
        useRms: Boolean = true,
        normalizeToPeak: Boolean = true
    ): FloatArray {
        if (barCount <= 0) return FloatArray(0)
        if (pcm.isEmpty()) return FloatArray(barCount) { 0f }

        val amplitudes = FloatArray(barCount)
        val samplesPerBar = pcm.size.toDouble() / barCount.toDouble()

        for (i in 0 until barCount) {
            val startIndex = (i * samplesPerBar).toInt().coerceIn(0, pcm.size - 1)
            val endIndex = ((i + 1) * samplesPerBar).toInt().coerceIn(startIndex + 1, pcm.size)
            val count = endIndex - startIndex

            if (count <= 0) {
                amplitudes[i] = 0f
                continue
            }

            if (useRms) {
                var sumSquares = 0.0
                for (j in startIndex until endIndex) {
                    val normalized = abs(pcm[j].toDouble()) / MAX_PCM16_VALUE
                    sumSquares += normalized * normalized
                }
                amplitudes[i] = sqrt(sumSquares / count.toDouble()).toFloat().coerceIn(0f, 1f)
            } else {
                var peak = 0.0
                for (j in startIndex until endIndex) {
                    val normalized = abs(pcm[j].toDouble()) / MAX_PCM16_VALUE
                    if (normalized > peak) {
                        peak = normalized
                    }
                }
                amplitudes[i] = peak.toFloat().coerceIn(0f, 1f)
            }
        }

        return applyPeakNormalization(amplitudes, normalizeToPeak)
    }

    /**
     * Downsamples normalized [-1.0f, 1.0f] [FloatArray] PCM into [barCount] normalized amplitude bars.
     */
    fun sampleFloatPcm(
        pcm: FloatArray,
        barCount: Int,
        useRms: Boolean = true,
        normalizeToPeak: Boolean = true
    ): FloatArray {
        if (barCount <= 0) return FloatArray(0)
        if (pcm.isEmpty()) return FloatArray(barCount) { 0f }

        val amplitudes = FloatArray(barCount)
        val samplesPerBar = pcm.size.toDouble() / barCount.toDouble()

        for (i in 0 until barCount) {
            val startIndex = (i * samplesPerBar).toInt().coerceIn(0, pcm.size - 1)
            val endIndex = ((i + 1) * samplesPerBar).toInt().coerceIn(startIndex + 1, pcm.size)
            val count = endIndex - startIndex

            if (count <= 0) {
                amplitudes[i] = 0f
                continue
            }

            if (useRms) {
                var sumSquares = 0.0
                for (j in startIndex until endIndex) {
                    val normalized = abs(pcm[j].toDouble()).coerceIn(0.0, 1.0)
                    sumSquares += normalized * normalized
                }
                amplitudes[i] = sqrt(sumSquares / count.toDouble()).toFloat().coerceIn(0f, 1f)
            } else {
                var peak = 0.0
                for (j in startIndex until endIndex) {
                    val normalized = abs(pcm[j].toDouble()).coerceIn(0.0, 1.0)
                    if (normalized > peak) {
                        peak = normalized
                    }
                }
                amplitudes[i] = peak.toFloat().coerceIn(0f, 1f)
            }
        }

        return applyPeakNormalization(amplitudes, normalizeToPeak)
    }

    /**
     * Extracts PCM samples from encoded WAV bytes and downsamples them into [barCount] amplitude bars.
     */
    fun sampleWavBytes(
        wavBytes: ByteArray,
        barCount: Int,
        useRms: Boolean = true,
        normalizeToPeak: Boolean = true
    ): FloatArray {
        if (wavBytes.isEmpty() || barCount <= 0) return FloatArray(barCount.coerceAtLeast(0)) { 0f }
        return try {
            val parsedWav = WavWriter.readWav(wavBytes)
            samplePcm16(parsedWav.pcm16, barCount, useRms, normalizeToPeak)
        } catch (_: Throwable) {
            FloatArray(barCount) { 0f }
        }
    }

    /**
     * Reads a WAV file from disk and computes its visualizer amplitude bars.
     */
    fun sampleWavFile(
        file: File,
        barCount: Int,
        useRms: Boolean = true,
        normalizeToPeak: Boolean = true
    ): FloatArray {
        if (!file.exists() || !file.canRead() || barCount <= 0) return FloatArray(barCount.coerceAtLeast(0)) { 0f }
        return try {
            val parsedWav = WavWriter.readWav(file)
            samplePcm16(parsedWav.pcm16, barCount, useRms, normalizeToPeak)
        } catch (_: Throwable) {
            FloatArray(barCount) { 0f }
        }
    }

    private fun applyPeakNormalization(amplitudes: FloatArray, normalizeToPeak: Boolean): FloatArray {
        if (!normalizeToPeak || amplitudes.isEmpty()) {
            return amplitudes
        }

        var maxVal = 0f
        for (v in amplitudes) {
            if (v > maxVal) maxVal = v
        }

        if (maxVal > 1e-5f) {
            val scale = 1f / maxVal
            for (i in amplitudes.indices) {
                amplitudes[i] = (amplitudes[i] * scale).coerceIn(0f, 1f)
            }
        }

        return amplitudes
    }
}
