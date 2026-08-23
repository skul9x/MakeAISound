package skul9x.example.makesound.engine

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.providers.NNAPIFlags
import android.content.Context
import skul9x.example.makesound.telemetry.StudioLogger
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.EnumSet

/**
 * Manages ONNX Runtime sessions for the 4 VieNeu-TTS models:
 * 1. Prefill Session (`vieneu_prefill.onnx`)
 * 2. Autoregressive Decode Step Session (`vieneu_decode_step.onnx`)
 * 3. Acoustic Frame Generator Session (`vieneu_acoustic_cached.onnx`)
 * 4. MOSS Audio Tokenizer Codec Session (`moss_audio_tokenizer_decode_full.onnx`)
 *
 * Configured by default for FP32 Full Precision mode and multi-threaded XNNPACK execution provider.
 */
class OnnxSessionManager(
    val prefillSession: OrtSession,
    val decodeSession: OrtSession,
    val acousticSession: OrtSession,
    val codecSession: OrtSession,
    val env: OrtEnvironment,
    val quantizationType: QuantizationType = QuantizationType.FP32,
    val executionProvider: String = "XNNPACK",
    val sessionOptions: OrtSession.SessionOptions? = null
) : AutoCloseable {

    enum class QuantizationType {
        FP32,
        INT8,
        FP16
    }

    private var closed = false
    val isClosed: Boolean
        get() = closed

    private var internalPrefillSession: OrtSession? = prefillSession
    private var internalDecodeSession: OrtSession? = decodeSession
    private var internalAcousticSession: OrtSession? = acousticSession
    private var internalCodecSession: OrtSession? = codecSession
    private var internalSessionOptions: OrtSession.SessionOptions? = sessionOptions

    override fun close() {
        if (closed) return
        closed = true
        try { internalPrefillSession?.close() } catch (_: Exception) {}
        try { internalDecodeSession?.close() } catch (_: Exception) {}
        try { internalAcousticSession?.close() } catch (_: Exception) {}
        try { internalCodecSession?.close() } catch (_: Exception) {}
        try { internalSessionOptions?.close() } catch (_: Exception) {}
        internalPrefillSession = null
        internalDecodeSession = null
        internalAcousticSession = null
        internalCodecSession = null
        internalSessionOptions = null
    }

    companion object {
        private const val TAG = "OnnxSessionManager"

        /**
         * Detect quantization type of an ONNX model file.
         */
        fun detectQuantizationType(modelPath: String): QuantizationType {
            try {
                val file = File(modelPath)
                if (file.exists() && file.length() > 0) {
                    val scanSize = minOf(file.length(), 2 * 1024 * 1024L).toInt()
                    val buffer = ByteArray(scanSize)
                    file.inputStream().use { input ->
                        input.read(buffer)
                    }
                    val detected = extractQuantizationFromBytes(buffer)
                    if (detected != null) {
                        return detected
                    }
                }
            } catch (e: Throwable) {
                StudioLogger.w(TAG, "Failed scanning ONNX metadata in $modelPath: ${e.message}")
            }

            val lowerPath = modelPath.lowercase()
            return when {
                lowerPath.contains("int8") -> QuantizationType.INT8
                lowerPath.contains("fp16") -> QuantizationType.FP16
                else -> QuantizationType.FP32
            }
        }

        private fun extractQuantizationFromBytes(bytes: ByteArray): QuantizationType? {
            val searchKey = "quantization".toByteArray(Charsets.UTF_8)
            val maxScan = bytes.size - searchKey.size
            for (i in 0 until maxScan) {
                var matched = true
                for (j in searchKey.indices) {
                    if (bytes[i + j] != searchKey[j]) {
                        matched = false
                        break
                    }
                }
                if (matched) {
                    val endIdx = minOf(bytes.size, i + searchKey.size + 64)
                    val slice = String(bytes, i + searchKey.size, endIdx - (i + searchKey.size), Charsets.UTF_8).lowercase()
                    if (slice.contains("int8")) return QuantizationType.INT8
                    if (slice.contains("fp16")) return QuantizationType.FP16
                    if (slice.contains("fp32")) return QuantizationType.FP32
                }
            }
            return null
        }

        /**
         * Configure session options with multi-threaded XNNPACK EP.
         */
        fun configureSessionOptions(
            quantizationType: QuantizationType,
            threads: Int = 0
        ): Pair<OrtSession.SessionOptions, String> {
            val sessionOptions = OrtSession.SessionOptions().apply {
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                setExecutionMode(OrtSession.SessionOptions.ExecutionMode.SEQUENTIAL)
            }

            val cpuCores = Runtime.getRuntime().availableProcessors().coerceIn(2, 6)
            val effectiveThreads = if (threads > 0) threads else cpuCores
            var epUsed = "CPU"

            when (quantizationType) {
                QuantizationType.FP16 -> {
                    var nnapiSuccess = false
                    try {
                        sessionOptions.addNnapi(EnumSet.of(NNAPIFlags.USE_FP16))
                        epUsed = "NNAPI"
                        nnapiSuccess = true
                        StudioLogger.i(TAG, "Configured NNAPI EP (USE_FP16) for FP16 model")
                    } catch (e: Throwable) {
                        StudioLogger.w(TAG, "NNAPI EP failed for FP16: ${e.message}. Falling back to XNNPACK.")
                    }

                    if (!nnapiSuccess) {
                        sessionOptions.setIntraOpNumThreads(1)
                        sessionOptions.setInterOpNumThreads(1)
                        sessionOptions.addConfigEntry("session.intra_op.allow_spinning", "0")
                        try {
                            sessionOptions.addXnnpack(mapOf("intra_op_num_threads" to effectiveThreads.toString()))
                            epUsed = "XNNPACK"
                        } catch (_: Throwable) {
                            sessionOptions.setIntraOpNumThreads(effectiveThreads)
                            epUsed = "CPU"
                        }
                    }
                }
                QuantizationType.FP32, QuantizationType.INT8 -> {
                    sessionOptions.setIntraOpNumThreads(1)
                    sessionOptions.setInterOpNumThreads(1)
                    sessionOptions.addConfigEntry("session.intra_op.allow_spinning", "0")
                    try {
                        sessionOptions.addXnnpack(mapOf("intra_op_num_threads" to effectiveThreads.toString()))
                        epUsed = "XNNPACK"
                        StudioLogger.i(TAG, "Configured XNNPACK EP with $effectiveThreads threads for $quantizationType model")
                    } catch (e: Throwable) {
                        StudioLogger.w(TAG, "Failed to add XNNPACK EP: ${e.message}. Falling back to CPU thread pool.")
                        sessionOptions.setIntraOpNumThreads(effectiveThreads)
                        epUsed = "CPU"
                    }
                }
            }

            return Pair(sessionOptions, epUsed)
        }

        fun create(
            context: Context,
            threads: Int = 0,
            precision: String = "FP32"
        ): OnnxSessionManager {
            val env = OrtEnvironment.getEnvironment()
            val baseDir = ensureAssetsExtracted(context, precision)
            val backboneDir = File(baseDir, "backbone")
            val codecDir = File(baseDir, "codec")

            val prefillPath = File(backboneDir, "vieneu_prefill.onnx").absolutePath
            val decodePath = File(backboneDir, "vieneu_decode_step.onnx").absolutePath
            val acousticPath = File(backboneDir, "vieneu_acoustic_cached.onnx").absolutePath
            val codecPath = File(codecDir, "moss_audio_tokenizer_decode_full.onnx").absolutePath

            val detectedQuant = detectQuantizationType(prefillPath)
            val (sessionOptions, epUsed) = configureSessionOptions(detectedQuant, threads)

            StudioLogger.i(TAG, "Creating ONNX sessions [Quantization: $detectedQuant, EP: $epUsed, Precision: $precision]...")
            val prefillSession = env.createSession(prefillPath, sessionOptions)
            val decodeSession = env.createSession(decodePath, sessionOptions)
            val acousticSession = env.createSession(acousticPath, sessionOptions)
            val codecSession = env.createSession(codecPath, sessionOptions)

            StudioLogger.i(TAG, "All 4 ONNX sessions initialized successfully [EP: $epUsed]")
            return OnnxSessionManager(
                prefillSession = prefillSession,
                decodeSession = decodeSession,
                acousticSession = acousticSession,
                codecSession = codecSession,
                env = env,
                quantizationType = detectedQuant,
                executionProvider = epUsed,
                sessionOptions = sessionOptions
            )
        }

        fun createFromFiles(
            backboneDir: File,
            codecDir: File,
            threads: Int = 0,
            quantizationTypeOverride: QuantizationType? = null
        ): OnnxSessionManager {
            val env = OrtEnvironment.getEnvironment()
            val prefillPath = File(backboneDir, "vieneu_prefill.onnx").absolutePath
            val decodePath = File(backboneDir, "vieneu_decode_step.onnx").absolutePath
            val acousticPath = File(backboneDir, "vieneu_acoustic_cached.onnx").absolutePath
            val codecPath = File(codecDir, "moss_audio_tokenizer_decode_full.onnx").absolutePath

            val detectedQuant = quantizationTypeOverride ?: detectQuantizationType(prefillPath)
            val (sessionOptions, epUsed) = configureSessionOptions(detectedQuant, threads)

            val prefillSession = env.createSession(prefillPath, sessionOptions)
            val decodeSession = env.createSession(decodePath, sessionOptions)
            val acousticSession = env.createSession(acousticPath, sessionOptions)
            val codecSession = env.createSession(codecPath, sessionOptions)

            return OnnxSessionManager(
                prefillSession = prefillSession,
                decodeSession = decodeSession,
                acousticSession = acousticSession,
                codecSession = codecSession,
                env = env,
                quantizationType = detectedQuant,
                executionProvider = epUsed,
                sessionOptions = sessionOptions
            )
        }

        fun ensureAssetsExtracted(context: Context, precision: String = "FP32"): File {
            val targetBase = File(context.filesDir, "vieneu")
            val backboneFolder = "vieneu/backbone"
            val targetSubdir = "backbone"

            copyAssetFolder(context, backboneFolder, File(targetBase, targetSubdir))

            val children = context.assets.list("vieneu") ?: emptyArray()
            for (child in children) {
                if (child != "backbone") {
                    val childPath = "vieneu/$child"
                    val subTarget = File(targetBase, child)
                    val subChildren = context.assets.list(childPath)
                    if (subChildren != null && subChildren.isNotEmpty()) {
                        copyAssetFolder(context, childPath, subTarget)
                    } else {
                        copyAssetFile(context, childPath, subTarget)
                    }
                }
            }

            return targetBase
        }

        private fun copyAssetFolder(context: Context, assetPath: String, targetDir: File) {
            val children = context.assets.list(assetPath) ?: return

            if (children.isEmpty()) {
                copyAssetFile(context, assetPath, targetDir)
            } else {
                if (!targetDir.exists()) {
                    targetDir.mkdirs()
                }
                for (child in children) {
                    val subAssetPath = if (assetPath.isEmpty()) child else "$assetPath/$child"
                    val subTarget = File(targetDir, child)
                    val subChildren = context.assets.list(subAssetPath)
                    if (subChildren != null && subChildren.isNotEmpty()) {
                        copyAssetFolder(context, subAssetPath, subTarget)
                    } else {
                        copyAssetFile(context, subAssetPath, subTarget)
                    }
                }
            }
        }

        private fun copyAssetFile(context: Context, assetPath: String, targetFile: File) {
            if (targetFile.exists() && targetFile.length() > 0) {
                return
            }
            targetFile.parentFile?.mkdirs()
            context.assets.open(assetPath).use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }
        }
    }
}
