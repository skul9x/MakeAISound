package skul9x.example.makesound

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import skul9x.example.makesound.engine.VieNeuConfig
import skul9x.example.makesound.engine.VoicePresets
import skul9x.example.makesound.engine.VoiceSampleManager
import skul9x.example.makesound.storage.WavWriter
import java.io.File
import kotlin.math.abs

/**
 * Comprehensive Verification Test for Phase 01: Voice Sample Assets & Repository Manager.
 *
 * Verifies:
 * 1. Bundled Asset Resolution for all 25 preset voices defined in `voices_v3_turbo.json`.
 * 2. Exact and Prefix/Parenthetical matching in `findBundledAssetPath`.
 * 3. Multi-tier resolution, instantaneous memory cache hit, and disk cache persistence.
 * 4. Fallback harmonic tone generator, RIFF WAV header integrity, and PCM waveform characteristics.
 */
class Phase1VoiceSampleManagerTest {

    @Test
    fun verifyVoiceSampleAssetsAndManagerCoreFunctionality() {
        val manager = VoiceSampleManager()

        // =========================================================================
        // 1. Preset Catalog & Bundled Asset Resolution for All 25 Voices
        // =========================================================================
        val jsonFile = File("src/main/assets/vieneu/voices_v3_turbo.json").takeIf { it.exists() }
            ?: File("app/src/main/assets/vieneu/voices_v3_turbo.json")
        assertTrue("voices_v3_turbo.json must exist in assets", jsonFile.exists())

        VoicePresets.loadFromInputStream(jsonFile.inputStream())
        val allVoices = VoicePresets.getVoicePresets()
        assertEquals("Must load all 25 voices", 25, allVoices.size)

        for (voice in allVoices) {
            val assetPath = manager.findBundledAssetPath(voice.name)
            assertNotNull("Asset path for preset '${voice.name}' must not be null", assetPath)
            assertTrue(
                "Asset path for '${voice.name}' must be under vieneu/samples: $assetPath",
                assetPath!!.startsWith("vieneu/samples/") && assetPath.endsWith(".wav", ignoreCase = true)
            )

            val bytes = manager.getBundledSampleBytes(voice.name)
            assertNotNull("Bundled audio bytes for '${voice.name}' must be loadable", bytes)
            assertTrue("Bundled WAV file for '${voice.name}' must have header (>44 bytes)", bytes!!.size > 44)

            assertTrue("Voice '${voice.name}' must report hasImmediateSample == true", manager.hasImmediateSample(voice.name))
        }

        // =========================================================================
        // 2. Exact and Prefix/Parenthetical Asset Matching
        // =========================================================================
        // Exact match
        val exactPath = manager.findBundledAssetPath("Ngọc Huyền")
        assertNotNull(exactPath)
        assertTrue(exactPath!!.contains("Ngọc Huyền.wav"))

        // Case-insensitive & trimmed with .wav extension
        val caseInsensitivePath = manager.findBundledAssetPath("  ngọc huyền.wav  ")
        assertNotNull(caseInsensitivePath)
        assertTrue(caseInsensitivePath!!.contains("Ngọc Huyền.wav"))

        // Prefix / Parenthetical base name matching
        val doanPath = manager.findBundledAssetPath("Đoan")
        assertNotNull("Base name 'Đoan' must match bundled asset", doanPath)
        assertTrue(doanPath!!.contains("Đoan"))

        val binhPath = manager.findBundledAssetPath("Bình")
        assertNotNull("Base name 'Bình' must match bundled asset", binhPath)
        assertTrue(binhPath!!.contains("Bình"))

        val lyPath = manager.findBundledAssetPath("Ly")
        assertNotNull("Base name 'Ly' must match bundled asset", lyPath)
        assertTrue(lyPath!!.contains("Ly"))

        val vinhPath = manager.findBundledAssetPath("Vĩnh")
        assertNotNull("Base name 'Vĩnh' must match bundled asset", vinhPath)
        assertTrue(vinhPath!!.contains("Vĩnh"))

        // Completely absent voice returns null for bundled asset
        val absentName = "AbsentVoice_XYZ_999"
        assertNull("Non-existent voice must return null asset path", manager.findBundledAssetPath(absentName))
        assertNull("Non-existent voice must return null bytes directly from assets", manager.getBundledSampleBytes(absentName))

        // =========================================================================
        // 3. Multi-Tier Resolution & Memory Caching Efficiency
        // =========================================================================
        manager.clearCache()
        assertEquals("Memory cache must be empty after clearCache", 0, manager.getMemoryCacheSize())

        val testVoice = "Ngọc Huyền"
        assertFalse("Voice should not be memory-cached initially", manager.isMemoryCached(testVoice))

        val startNs = System.nanoTime()
        val firstSample = manager.getOrResolveSample(testVoice)
        val firstDurationMs = (System.nanoTime() - startNs) / 1_000_000.0

        assertTrue("First resolve must succeed", firstSample.isNotEmpty())
        assertTrue("Voice should now be in memory cache", manager.isMemoryCached(testVoice))
        assertEquals(1, manager.getMemoryCacheSize())

        // Instantaneous Memory Cache Retrieval (< 5ms)
        val cacheStartNs = System.nanoTime()
        val secondSample = manager.getOrResolveSample(testVoice)
        val cacheDurationMs = (System.nanoTime() - cacheStartNs) / 1_000_000.0

        assertSame("Subsequent resolution must return identical memory-cached instance", firstSample, secondSample)
        assertTrue("Memory cache hit must be instantaneous (< 5ms), was ${cacheDurationMs}ms", cacheDurationMs < 5.0)

        // =========================================================================
        // 4. Fallback Harmonic Tone Synthesis & WAV Header Validation
        // =========================================================================
        val fallbackVoiceName = "FallbackSyntheticVoice"
        assertFalse(manager.hasImmediateSample(fallbackVoiceName))

        // Trigger Tier 4 Fallback resolution
        val fallbackWavBytes = manager.getOrResolveSample(fallbackVoiceName)
        assertNotNull(fallbackWavBytes)
        assertTrue(manager.isMemoryCached(fallbackVoiceName))
        assertTrue(manager.isDiskCached(fallbackVoiceName))

        // Validate canonical 44-byte WAV header
        assertEquals('R'.code.toByte(), fallbackWavBytes[0])
        assertEquals('I'.code.toByte(), fallbackWavBytes[1])
        assertEquals('F'.code.toByte(), fallbackWavBytes[2])
        assertEquals('F'.code.toByte(), fallbackWavBytes[3])

        assertEquals('W'.code.toByte(), fallbackWavBytes[8])
        assertEquals('A'.code.toByte(), fallbackWavBytes[9])
        assertEquals('V'.code.toByte(), fallbackWavBytes[10])
        assertEquals('E'.code.toByte(), fallbackWavBytes[11])

        // Parse with WavWriter to verify sample parameters & payload
        val parsedWav = WavWriter.readWav(fallbackWavBytes)
        assertEquals(VieNeuConfig.SAMPLE_RATE, parsedWav.sampleRate)
        assertEquals(1, parsedWav.numChannels)
        assertEquals(16, parsedWav.bitsPerSample)

        val expectedSamples = (VieNeuConfig.SAMPLE_RATE * VoiceSampleManager.DEFAULT_FALLBACK_DURATION_MS) / 1000
        assertEquals(expectedSamples, parsedWav.pcm16.size)
        assertEquals(expectedSamples * 2, parsedWav.dataSize)
        assertEquals(44 + expectedSamples * 2, fallbackWavBytes.size)

        // Verify harmonic tone acoustic properties (smooth envelope, non-silent audio)
        val pcm = parsedWav.pcm16
        var maxAmp = 0
        for (sample in pcm) {
            val amp = abs(sample.toInt())
            if (amp > maxAmp) maxAmp = amp
        }
        assertTrue("Harmonic tone must produce audible amplitude (> 5000)", maxAmp > 5000)
        assertTrue("Harmonic tone start sample should be softly attenuated by fade envelope", abs(pcm[0].toInt()) < 200)

        // Custom duration tone generation verification
        val customTone = manager.generateHarmonicTone(durationMs = 250, sampleRate = 24000)
        assertEquals(24000 * 250 / 1000, customTone.size)

        // =========================================================================
        // 5. Cleanup & Cache Teardown
        // =========================================================================
        val diskFile = manager.getDiskCachedFile(fallbackVoiceName)
        assertTrue("Disk cache file must exist before teardown", diskFile.exists())

        val deletedCount = manager.clearCache()
        assertTrue("clearCache must delete at least 1 file", deletedCount >= 1)
        assertEquals(0, manager.getMemoryCacheSize())
        assertFalse(diskFile.exists())
    }
}
