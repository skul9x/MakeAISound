package skul9x.example.makesound.engine

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import skul9x.example.makesound.storage.WavWriter
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.sin

/**
 * Instant Voice Sample Repository & Cache Manager.
 *
 * Provides immediate (<5ms) auditory previews of each voice preset before speech synthesis:
 * 1. Resolves pre-bundled WAV sample clips in `assets/vieneu/samples/` with exact and prefix matching.
 * 2. Multi-tier resolution strategy: Memory Cache -> Pre-bundled Assets -> Persistent Disk Cache -> Harmonic Acoustic Fallback.
 * 3. Fallback tone generator producing pleasant 440Hz/880Hz envelope-shaped acoustic PCM audio.
 * 4. Thread-safe memory and disk caching preventing redundant I/O and latency.
 */
class VoiceSampleManager(
    private val context: Context? = null
) {
    private val memoryCache = ConcurrentHashMap<String, ByteArray>()
    val sampleAssetDir = "${VieNeuConfig.ASSET_DIR}/samples" // "vieneu/samples"

    companion object {
        const val DEFAULT_FALLBACK_DURATION_MS = 500
        const val HARMONIC_FREQ_1 = 440.0 // A4 fundamental
        const val HARMONIC_FREQ_2 = 880.0 // A5 overtone
        const val ENVELOPE_FADE_SAMPLES = 480 // 10ms at 48kHz
        const val PEAK_AMPLITUDE = 16000.0 // 16-bit safe headroom
    }

    /**
     * Checks whether an audio sample is immediately ready (in memory, on disk, or bundled in assets).
     */
    fun hasImmediateSample(voiceName: String): Boolean {
        if (memoryCache.containsKey(voiceName)) return true
        val diskFile = getDiskCachedFile(voiceName)
        if (diskFile.exists() && diskFile.length() > 0) return true
        return findBundledAssetPath(voiceName) != null
    }

    /**
     * Checks whether a voice sample is currently present in the in-memory cache.
     */
    fun isMemoryCached(voiceName: String): Boolean = memoryCache.containsKey(voiceName)

    /**
     * Checks whether a voice sample is currently saved in persistent disk cache.
     */
    fun isDiskCached(voiceName: String): Boolean = getDiskCachedFile(voiceName).let { it.exists() && it.length() > 0 }

    /**
     * Number of active samples cached in memory.
     */
    fun getMemoryCacheSize(): Int = memoryCache.size

    /**
     * Internal disk cache directory for voice preview files: `cacheDir/voice_samples/`.
     */
    fun getDiskCacheDir(): File {
        val baseDir = context?.cacheDir ?: File(System.getProperty("java.io.tmpdir") ?: "/tmp")
        val dir = File(baseDir, "voice_samples")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * File handle for persistent disk-cached WAV sample.
     */
    fun getDiskCachedFile(voiceName: String): File {
        val safeName = voiceName.replace("[^a-zA-Z0-9_\\-]".toRegex(), "_")
        return File(getDiskCacheDir(), "sample_preview_$safeName.wav")
    }

    /**
     * Scans for bundled sample file in `assets/vieneu/samples/`.
     *
     * Resolution order:
     * 1. Exact match (case-insensitive, ignoring .wav extension).
     * 2. Matching base name before parenthetical tags (e.g., `Đoan (nữ miền Nam).wav` -> `Đoan`).
     * 3. Prefix match: query starts with base name, or base name starts with query.
     * 4. Word token match: query contains the base name as a distinct word.
     */
    fun findBundledAssetPath(voiceName: String): String? {
        val filesList = getBundledFilesList()
        if (filesList.isEmpty()) return null

        val targetClean = voiceName.trim()
            .removeSuffix(".wav").removeSuffix(".WAV")
            .lowercase()

        val wavFiles = filesList.filter { it.endsWith(".wav", ignoreCase = true) }

        // 1. Exact match (e.g. "Ngọc Huyền.wav" for "Ngọc Huyền")
        for (f in wavFiles) {
            val baseName = f.removeSuffix(".wav").removeSuffix(".WAV").trim().lowercase()
            if (baseName == targetClean) {
                return "$sampleAssetDir/$f"
            }
        }

        // 2. Base name before parenthetical region/gender tags
        for (f in wavFiles) {
            val baseName = f.removeSuffix(".wav").removeSuffix(".WAV").trim().lowercase()
            val baseBeforeParen = baseName.substringBefore('(').trim()
            if (baseBeforeParen.isNotEmpty() && baseBeforeParen == targetClean) {
                return "$sampleAssetDir/$f"
            }
        }

        // 3. Prefix match (query starts with base name, or base name starts with query)
        for (f in wavFiles) {
            val baseName = f.removeSuffix(".wav").removeSuffix(".WAV").trim().lowercase()
            val baseBeforeParen = baseName.substringBefore('(').trim()
            if (baseBeforeParen.isNotEmpty()) {
                if (targetClean.startsWith(baseBeforeParen) || baseBeforeParen.startsWith(targetClean)) {
                    return "$sampleAssetDir/$f"
                }
            }
            if (baseName.startsWith(targetClean) || targetClean.startsWith(baseName)) {
                return "$sampleAssetDir/$f"
            }
        }

        // 4. Token containment match
        val targetTokens = targetClean.split("\\s+".toRegex())
        for (f in wavFiles) {
            val baseName = f.removeSuffix(".wav").removeSuffix(".WAV").trim().lowercase()
            val baseBeforeParen = baseName.substringBefore('(').trim()
            if (baseBeforeParen.isNotEmpty() && targetTokens.contains(baseBeforeParen)) {
                return "$sampleAssetDir/$f"
            }
        }

        return null
    }

    /**
     * Retrieves pre-bundled audio sample clip bytes from assets if present.
     */
    fun getBundledSampleBytes(voiceName: String): ByteArray? {
        val assetPath = findBundledAssetPath(voiceName) ?: return null

        // Try Android AssetManager first if context is available
        try {
            context?.assets?.open(assetPath)?.use { input ->
                val bytes = input.readBytes()
                if (bytes.isNotEmpty()) return bytes
            }
        } catch (_: Exception) {}

        // Fallback for JVM unit tests running without Android AssetManager
        val candidateDirs = listOf(
            "src/main/assets",
            "app/src/main/assets",
            "../app/src/main/assets"
        )
        for (dir in candidateDirs) {
            val file = File(dir, assetPath)
            if (file.exists() && file.length() > 0) {
                try {
                    return file.readBytes()
                } catch (_: Exception) {}
            }
        }

        return null
    }

    /**
     * Multi-tier audio resolution:
     * 1. Memory Cache (`ConcurrentHashMap<String, ByteArray>`) -> 0ms retrieval.
     * 2. Pre-bundled Asset in `assets/vieneu/samples/` -> <5ms retrieval.
     * 3. Persistent Disk Cache (`cacheDir/voice_samples/`).
     * 4. Harmonic Acoustic Tone Fallback (`generateHarmonicPreviewPcm`).
     */
    fun getOrResolveSample(voiceName: String): ByteArray {
        // Tier 1: In-Memory Cache
        memoryCache[voiceName]?.let { return it }

        // Tier 2: Pre-bundled Assets
        val bundled = getBundledSampleBytes(voiceName)
        if (bundled != null && bundled.isNotEmpty()) {
            memoryCache[voiceName] = bundled
            return bundled
        }

        // Tier 3: Persistent Disk Cache
        val diskFile = getDiskCachedFile(voiceName)
        if (diskFile.exists() && diskFile.length() > 0) {
            try {
                val bytes = FileInputStream(diskFile).use { it.readBytes() }
                if (bytes.isNotEmpty()) {
                    memoryCache[voiceName] = bytes
                    return bytes
                }
            } catch (_: Exception) {}
        }

        // Tier 4: Fallback Tone Generator (440Hz/880Hz envelope-shaped harmonic tone)
        val fallbackPcm = generateHarmonicPreviewPcm(DEFAULT_FALLBACK_DURATION_MS, VieNeuConfig.SAMPLE_RATE)
        val fallbackWav = WavWriter.pcm16ToWav(fallbackPcm, VieNeuConfig.SAMPLE_RATE)
        saveSampleToCache(voiceName, fallbackWav)
        return fallbackWav
    }

    /**
     * Suspendable variant of [getOrResolveSample] executing safely on [Dispatchers.IO].
     */
    suspend fun getOrResolveSampleAsync(voiceName: String): ByteArray = withContext(Dispatchers.IO) {
        getOrResolveSample(voiceName)
    }

    /**
     * Generates a pleasant harmonic acoustic benchmark tone (440Hz fundamental + 880Hz overtone)
     * with smooth envelope fade-in/fade-out for artifact-free preview fallback.
     *
     * @param durationMs Duration of generated PCM in milliseconds (default: 500ms).
     * @param sampleRate Sampling rate in Hz (default: 48,000Hz).
     * @return 16-bit signed PCM ShortArray.
     */
    fun generateHarmonicTone(
        durationMs: Int = DEFAULT_FALLBACK_DURATION_MS,
        sampleRate: Int = VieNeuConfig.SAMPLE_RATE
    ): ShortArray {
        val nSamples = ((sampleRate.toLong() * durationMs) / 1000).toInt().coerceAtLeast(1)
        val pcm = ShortArray(nSamples)
        val twoPi = 2.0 * Math.PI
        val fadeSamples = minOf(ENVELOPE_FADE_SAMPLES, nSamples / 4).coerceAtLeast(1)

        for (i in 0 until nSamples) {
            val t = i.toDouble() / sampleRate
            // Gentle linear envelope fade-in / fade-out to prevent clicks
            val env = when {
                i < fadeSamples -> i.toDouble() / fadeSamples.toDouble()
                i > nSamples - fadeSamples -> (nSamples - i).toDouble() / fadeSamples.toDouble()
                else -> 1.0
            }
            val sample = (0.65 * sin(twoPi * HARMONIC_FREQ_1 * t) + 0.35 * sin(twoPi * HARMONIC_FREQ_2 * t)) * env
            pcm[i] = (sample * PEAK_AMPLITUDE).toInt().coerceIn(-32768, 32767).toShort()
        }
        return pcm
    }

    /**
     * Alias for [generateHarmonicTone] producing 440Hz/880Hz envelope-shaped acoustic PCM audio.
     */
    fun generateHarmonicPreviewPcm(
        durationMs: Int = DEFAULT_FALLBACK_DURATION_MS,
        sampleRate: Int = VieNeuConfig.SAMPLE_RATE
    ): ShortArray = generateHarmonicTone(durationMs, sampleRate)

    /**
     * Saves audio WAV bytes to both memory and persistent disk caches.
     */
    fun saveSampleToCache(voiceName: String, wavBytes: ByteArray): File {
        memoryCache[voiceName] = wavBytes
        val file = getDiskCachedFile(voiceName)
        file.parentFile?.mkdirs()
        FileOutputStream(file).use { fos ->
            fos.write(wavBytes)
            fos.flush()
        }
        return file
    }

    /**
     * Clears in-memory cache entries only.
     */
    fun clearMemoryCache() {
        memoryCache.clear()
    }

    /**
     * Clears both in-memory cache and persistent disk cache directory.
     *
     * @return Number of deleted disk cache files.
     */
    fun clearCache(): Int {
        memoryCache.clear()
        val files = getDiskCacheDir().listFiles { _, name -> name.startsWith("sample_preview_") } ?: return 0
        var count = 0
        for (f in files) {
            if (f.isFile && f.delete()) {
                count++
            }
        }
        return count
    }

    private fun getBundledFilesList(): List<String> {
        val filesList = mutableListOf<String>()

        // 1. Try Android assets listing
        try {
            val list = context?.assets?.list(sampleAssetDir)
            if (list != null && list.isNotEmpty()) {
                filesList.addAll(list)
            }
        } catch (_: Exception) {}

        // 2. Fallback to candidate local directories
        if (filesList.isEmpty()) {
            val candidateDirs = listOf(
                "src/main/assets/$sampleAssetDir",
                "app/src/main/assets/$sampleAssetDir",
                "../app/src/main/assets/$sampleAssetDir"
            )
            for (dir in candidateDirs) {
                val dirFile = File(dir)
                if (dirFile.exists() && dirFile.isDirectory) {
                    val list = dirFile.list()
                    if (list != null && list.isNotEmpty()) {
                        filesList.addAll(list)
                        break
                    }
                }
            }
        }

        return filesList
    }
}
