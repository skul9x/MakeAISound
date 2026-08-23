package skul9x.example.makesound

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import skul9x.example.makesound.engine.PcmUtils
import skul9x.example.makesound.engine.VieNeuConfig
import skul9x.example.makesound.storage.AudioStorageManager
import skul9x.example.makesound.storage.WavWriter
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

/**
 * Single verification test for Phase 03: WAV Exporter & Storage Layer.
 *
 * Core functionality verified:
 * 1. 44-byte RIFF header structure (magic `RIFF`, format `WAVE`, subchunk1 `fmt `,
 *    audio format 1, channels 1, sample rate 48000, byte rate 96000, block align 2,
 *    bitsPerSample 16, subchunk2 `data`).
 * 2. Calculation of chunk sizes and data lengths for arbitrary PCM sample arrays (0 samples, 100 samples, 72000 samples).
 * 3. Byte-level integrity of serialized WAV output, float-to-short pipeline, and file roundtrip reconstruction.
 * 4. AudioStorageManager file naming sanitization and formatting.
 */
class Phase3WavExporterTest {

    @Test
    fun verifyPhase3CoreFunctionality() {
        // =========================================================================
        // 1. Verify 44-byte RIFF Header Structure & Magic Byte Markers
        // =========================================================================
        val sampleRate = 48000
        val numChannels = 1
        val bitsPerSample = 16
        val sampleCount = 48000 // 1 second of audio
        val dataSize = sampleCount * numChannels * (bitsPerSample / 8) // 96000 bytes

        val header = WavWriter.createHeader(
            dataSize = dataSize,
            sampleRate = sampleRate,
            numChannels = numChannels,
            bitsPerSample = bitsPerSample
        )

        assertEquals("Header size must be exactly 44 bytes", 44, header.size)

        val headerBuf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)

        // Magic "RIFF" (0..3)
        val riffBytes = ByteArray(4)
        headerBuf.get(riffBytes)
        assertEquals("RIFF", String(riffBytes, Charsets.US_ASCII))

        // Total chunk size (4..7) = 36 + dataSize
        val chunkSize = headerBuf.int
        assertEquals(36 + dataSize, chunkSize)

        // Magic "WAVE" (8..11)
        val waveBytes = ByteArray(4)
        headerBuf.get(waveBytes)
        assertEquals("WAVE", String(waveBytes, Charsets.US_ASCII))

        // Subchunk1 ID "fmt " (12..15)
        val fmtBytes = ByteArray(4)
        headerBuf.get(fmtBytes)
        assertEquals("fmt ", String(fmtBytes, Charsets.US_ASCII))

        // Subchunk1 Size (16..19) = 16 for standard PCM
        val subchunk1Size = headerBuf.int
        assertEquals(16, subchunk1Size)

        // Audio format (20..21) = 1 (Linear PCM)
        val audioFormat = headerBuf.short
        assertEquals(1.toShort(), audioFormat)

        // Number of channels (22..23) = 1 (Mono)
        val channels = headerBuf.short
        assertEquals(1.toShort(), channels)

        // Sample rate (24..27) = 48000 Hz
        val sr = headerBuf.int
        assertEquals(48000, sr)

        // Byte rate (28..31) = 48000 * 1 * 16 / 8 = 96000 B/s
        val byteRate = headerBuf.int
        assertEquals(96000, byteRate)

        // Block align (32..33) = 1 * 16 / 8 = 2 bytes
        val blockAlign = headerBuf.short
        assertEquals(2.toShort(), blockAlign)

        // Bits per sample (34..35) = 16
        val bps = headerBuf.short
        assertEquals(16.toShort(), bps)

        // Subchunk2 ID "data" (36..39)
        val dataMarker = ByteArray(4)
        headerBuf.get(dataMarker)
        assertEquals("data", String(dataMarker, Charsets.US_ASCII))

        // Subchunk2 Size (40..43) = dataSize
        val subchunk2Size = headerBuf.int
        assertEquals(dataSize, subchunk2Size)

        // =========================================================================
        // 2. Verify Calculation of Chunk Sizes for Arbitrary PCM Arrays
        // =========================================================================
        // Test case A: Empty PCM buffer (0 samples)
        val emptyPcm = ShortArray(0)
        val emptyWav = WavWriter.pcm16ToWav(emptyPcm, 48000, 1)
        assertEquals(44, emptyWav.size)
        val emptyParsed = WavWriter.readWav(emptyWav)
        assertEquals(0, emptyParsed.pcm16.size)
        assertEquals(0, emptyParsed.dataSize)
        assertEquals(0f, emptyParsed.durationSeconds, 0.0001f)

        // Test case B: 100 samples (200 bytes)
        val smallPcm = ShortArray(100) { it.toShort() }
        val smallWav = WavWriter.pcm16ToWav(smallPcm, 48000, 1)
        assertEquals(44 + 200, smallWav.size)
        val smallParsed = WavWriter.readWav(smallWav)
        assertEquals(100, smallParsed.pcm16.size)
        assertEquals(200, smallParsed.dataSize)
        assertArrayEquals(smallPcm, smallParsed.pcm16)

        // Test case C: 1.5 seconds at 48kHz = 72,000 samples (144,000 bytes)
        val samplesCount = 72000
        val arbitraryPcm = ShortArray(samplesCount)
        for (i in 0 until samplesCount) {
            // Synthesize 440 Hz standard A tone
            val t = i.toDouble() / 48000.0
            val s = (sin(2.0 * PI * 440.0 * t) * 32000.0).toInt().toShort()
            arbitraryPcm[i] = s
        }

        val wavBytes = WavWriter.pcm16ToWav(arbitraryPcm, 48000, 1)
        assertEquals(44 + 144000, wavBytes.size)

        // =========================================================================
        // 3. Verify Byte-Level Integrity, Stream & Disk File Reconstruction
        // =========================================================================
        // Test stream parsing
        val inputStream = ByteArrayInputStream(wavBytes)
        val streamParsed = WavWriter.readWav(inputStream)
        assertEquals(48000, streamParsed.sampleRate)
        assertEquals(1, streamParsed.numChannels)
        assertEquals(16, streamParsed.bitsPerSample)
        assertEquals(144000, streamParsed.dataSize)
        assertEquals(1.5f, streamParsed.durationSeconds, 0.0001f)
        assertArrayEquals(arbitraryPcm, streamParsed.pcm16)

        // Test disk file write & read
        val tempDir = File(System.getProperty("java.io.tmpdir"), "makesound_test_${System.currentTimeMillis()}")
        tempDir.mkdirs()
        try {
            val testFile = File(tempDir, "test_tone_48k.wav")
            WavWriter.writeWav(testFile, arbitraryPcm, 48000, 1)

            assertTrue("File must exist on disk", testFile.exists())
            assertEquals((44 + 144000).toLong(), testFile.length())

            val fileParsed = WavWriter.readWav(testFile)
            assertEquals(48000, fileParsed.sampleRate)
            assertEquals(1, fileParsed.numChannels)
            assertEquals(16, fileParsed.bitsPerSample)
            assertEquals(144000, fileParsed.dataSize)
            assertArrayEquals("Disk written PCM must match original byte-for-byte", arbitraryPcm, fileParsed.pcm16)

            // Test Float PCM -> WAV serialization
            val floatPcm = FloatArray(1000) { (it % 100 - 50) / 50f }
            val floatWavFile = File(tempDir, "test_float.wav")
            WavWriter.writeWav(floatWavFile, floatPcm, 48000, 1)
            val floatParsed = WavWriter.readWav(floatWavFile)
            val expectedShortPcm = PcmUtils.floatToShort(floatPcm)
            assertArrayEquals("Float to WAV conversion must match PcmUtils.floatToShort", expectedShortPcm, floatParsed.pcm16)
        } finally {
            tempDir.deleteRecursively()
        }

        // =========================================================================
        // 4. Verify AudioStorageManager File Name Generation & Sanitization
        // =========================================================================
        val timestamp = 1717000000000L // Fixed timestamp
        val fileName1 = AudioStorageManager.generateFileName("Trúc Lý", timestamp)
        assertTrue("Filename must start with MakeAiSound_", fileName1.startsWith("MakeAiSound_"))
        assertTrue("Filename must end with .wav", fileName1.endsWith(".wav"))

        val fileName2 = AudioStorageManager.generateFileName("Nam Minh @ 123!", timestamp)
        assertTrue("Special characters must be sanitized", !fileName2.contains("@") && !fileName2.contains("!"))
        assertTrue("Whitespace should be replaced with underscore", !fileName2.contains(" "))
    }
}
