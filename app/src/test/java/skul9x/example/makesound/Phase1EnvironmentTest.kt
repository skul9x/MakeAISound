package skul9x.example.makesound

import ai.onnxruntime.OrtEnvironment
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Phase 1 Comprehensive Environment & Setup Verification Test.
 *
 * Verifies:
 * 1. Microsoft ONNX Runtime JVM environment initialization and capabilities.
 * 2. All AI model weights, voice presets, tokenizers, codecs, and phonemizer data assets.
 * 3. Configuration parity between copied assets and VieNeu-TTS v3 Turbo architecture specifications.
 * 4. Native library `.so` binaries for all supported ABIs.
 */
class Phase1EnvironmentTest {

    private fun resolveProjectDir(relativePath: String): File {
        val direct = File(relativePath)
        if (direct.exists()) return direct
        val nested = File("app", relativePath)
        if (nested.exists()) return nested
        val parent = File("..", relativePath)
        if (parent.exists()) return parent
        return direct
    }

    @Test
    fun verifyPhase1CoreFunctionality() {
        // 1. Verify ONNX Runtime environment initialization
        val ortEnv = OrtEnvironment.getEnvironment()
        assertNotNull("OrtEnvironment should not be null", ortEnv)
        val sessionOpts = ai.onnxruntime.OrtSession.SessionOptions()
        assertNotNull("OrtSession.SessionOptions should instantiate", sessionOpts)
        sessionOpts.close()

        // 2. Resolve Assets and Native JNI directories
        val assetsDir = resolveProjectDir("src/main/assets/vieneu")
        val jniLibsDir = resolveProjectDir("src/main/jniLibs")

        assertTrue("Vieneu assets directory must exist at: ${assetsDir.absolutePath}", assetsDir.exists() && assetsDir.isDirectory)
        assertTrue("Native jniLibs directory must exist at: ${jniLibsDir.absolutePath}", jniLibsDir.exists() && jniLibsDir.isDirectory)

        // 3. Verify Backbone model assets
        val backboneDir = File(assetsDir, "backbone")
        assertTrue("Backbone dir exists", backboneDir.exists())

        val requiredBackboneFiles = listOf(
            "vieneu_prefill.onnx",
            "vieneu_decode_step.onnx",
            "vieneu_acoustic_cached.onnx",
            "vieneu_backbone_shared.data",
            "vieneu_v3_heads.npz",
            "config.json",
            "tokenizer.json"
        )
        for (fileName in requiredBackboneFiles) {
            val file = File(backboneDir, fileName)
            assertTrue("Backbone asset $fileName must exist and be non-empty", file.exists() && file.length() > 0)
        }

        // 4. Verify Model Configuration matches VieNeu-TTS v3 Turbo architecture
        val configFile = File(backboneDir, "config.json")
        val configJson = JSONObject(configFile.readText(Charsets.UTF_8))
        assertEquals("Model type must be vieneu_v3", "vieneu_v3", configJson.getString("model_type"))
        assertEquals("Number of VQ codebooks must be 16", 16, configJson.getInt("n_vq"))
        assertEquals("Hidden size must be 768", 768, configJson.getInt("hidden_size"))
        assertEquals("Sample rate must be 48000", 48000, configJson.getInt("audio_sample_rate"))

        // 5. Verify Codec and Denoiser model assets
        val codecDir = File(assetsDir, "codec")
        assertTrue("Codec dir exists", codecDir.exists())
        val requiredCodecFiles = listOf(
            "moss_audio_tokenizer_decode_full.onnx",
            "moss_audio_tokenizer_decode_shared.data",
            "moss_audio_tokenizer_decode_step.onnx",
            "codec_browser_onnx_meta.json"
        )
        for (fileName in requiredCodecFiles) {
            val file = File(codecDir, fileName)
            assertTrue("Codec asset $fileName must exist and be non-empty", file.exists() && file.length() > 0)
        }

        val denoiserDir = File(assetsDir, "denoiser")
        assertTrue("Denoiser dir exists", denoiserDir.exists())
        val denoiserFile = File(denoiserDir, "denoiser.onnx")
        assertTrue("Denoiser model file must exist", denoiserFile.exists() && denoiserFile.length() > 10_000_000)

        // 6. Verify G2P pronunciation dictionary
        val g2pFile = File(assetsDir, "sea_g2p.bin")
        assertTrue("Phonemizer dictionary sea_g2p.bin must exist (>50MB)", g2pFile.exists() && g2pFile.length() > 50_000_000)

        // 7. Verify Voice Presets JSON and Speaker Embedding dimensions
        val voicesFile = File(assetsDir, "voices_v3_turbo.json")
        assertTrue("Voices config file voices_v3_turbo.json must exist", voicesFile.exists() && voicesFile.length() > 0)

        val voicesJson = JSONObject(voicesFile.readText(Charsets.UTF_8))
        val presets = voicesJson.getJSONObject("presets")
        assertTrue("Presets must have multiple voices", presets.length() >= 5)

        // Verify standard Vietnamese voices
        val expectedSampleVoices = listOf("Minh Đức", "Thái Sơn", "Xuân Vĩnh", "Trúc Ly", "Ngọc Linh")
        for (voiceName in expectedSampleVoices) {
            assertTrue("Voice '$voiceName' must exist in presets", presets.has(voiceName))
            val voiceObj = presets.getJSONObject(voiceName)
            assertTrue("Voice '$voiceName' must have speaker_emb", voiceObj.has("speaker_emb"))
            val emb = voiceObj.getJSONArray("speaker_emb")
            assertEquals("Speaker embedding dimension must be 192", 192, emb.length())
            assertTrue("Voice '$voiceName' must have acoustic codes", voiceObj.has("codes"))
            val codes = voiceObj.getJSONArray("codes")
            assertTrue("Acoustic codes frames count must be > 0", codes.length() > 0)
            val firstFrame = codes.getJSONArray(0)
            assertEquals("Acoustic code frame must have 16 codebook tokens", 16, firstFrame.length())
        }

        // 8. Verify Native .so libraries for all target architectures
        val expectedAbis = listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        for (abi in expectedAbis) {
            val abiDir = File(jniLibsDir, abi)
            assertTrue("Native ABI directory $abi must exist", abiDir.exists() && abiDir.isDirectory)
            val soFile = File(abiDir, "libsea_g2p_android.so")
            assertTrue("Native library $abi/libsea_g2p_android.so must exist and be non-empty", soFile.exists() && soFile.length() > 1_000_000)
        }
    }
}
