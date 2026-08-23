package skul9x.example.makesound.engine

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import android.content.Context
import skul9x.example.makesound.telemetry.StudioLogger
import java.io.File
import java.nio.FloatBuffer
import java.nio.IntBuffer
import java.nio.LongBuffer
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * On-Device Neural Acoustic Text-to-Speech Engine for VieNeu-TTS v3 Turbo.
 * Operates in FP32 Full Precision mode.
 */
class VieNeuOnnxEngine(
    val sessionManager: OnnxSessionManager,
    val config: VieNeuConfig,
    val tokenizer: VieNeuTokenizer,
    val textEmbeddings: Array<FloatArray>,           // (textVocabSize, hiddenSize)
    val audioEmbeddings: Array<Array<FloatArray>>,    // (nVq, audioVocabSize, hiddenSize)
    val xvecW: Array<FloatArray>?,                   // (hiddenSize, speakerEmbeddingDim)
    val xvecB: FloatArray?,                          // (hiddenSize)
    val xvecLnW: FloatArray?,                        // (hiddenSize)
    val xvecLnB: FloatArray?,                        // (hiddenSize)
    val xvecLnEps: Float = 1e-5f
) : AutoCloseable {

    val quantizationType: OnnxSessionManager.QuantizationType
        get() = sessionManager.quantizationType

    val executionProvider: String
        get() = sessionManager.executionProvider

    private val lock = ReentrantLock()
    private val env: OrtEnvironment get() = sessionManager.env

    private var closed = false
    val isClosed: Boolean
        get() = closed || sessionManager.isClosed

    // Pre-allocated buffers for topKSample to eliminate per-call allocations
    private var workingLogitsBuffer = FloatArray(1024)
    private var topKIndicesBuffer = IntArray(256)
    private var topKLogitsBuffer = FloatArray(256)
    private var probsBuffer = DoubleArray(256)

    // Pre-allocated buffers for decode loop and acoustic frame generation
    private val decodeInputsMap = HashMap<String, OnnxTensor>(2 + 2 * config.numHiddenLayers)
    private val acousticFeedMap = HashMap<String, OnnxTensor>(4)
    private val decodeSlotRowBuffer = IntArray(config.nVq + 1)
    private val decodeSlotEmbedsBuffer = FloatArray(config.hiddenSize)
    private val decodePosBuffer = LongArray(1)
    private val acousticTokBuffer = FloatArray(2 * config.hiddenSize)
    private val acousticPos0Buffer = longArrayOf(0L, 1L)
    private val acousticPosBuffer = LongArray(1)
    private val acousticHiddenFlatBuffer = FloatArray(2 * config.hiddenSize)
    private val acousticSlot0Buffer = FloatArray(config.hiddenSize)
    private val acousticVecBuffer = FloatArray(config.hiddenSize)

    // Contiguous flattened audio embeddings per channel for cache locality
    val flatAudioEmbeddings: Array<FloatArray> = Array(audioEmbeddings.size) { ch ->
        val vSize = audioEmbeddings[ch].size
        val hSize = config.hiddenSize
        val flat = FloatArray(vSize * hSize)
        for (v in 0 until vSize) {
            val emb = audioEmbeddings[ch][v]
            System.arraycopy(emb, 0, flat, v * hSize, hSize)
        }
        flat
    }
    // Pre-allocated channel logits buffer
    private val channelLogitsBuffer: Array<FloatArray> = Array(audioEmbeddings.size) { ch ->
        FloatArray(audioEmbeddings[ch].size)
    }

    private var cachedEmptyPastK: OnnxTensor? = null
    private var cachedEmptyPastV: OnnxTensor? = null

    @Synchronized
    fun getOrCreateEmptyPastK(): OnnxTensor {
        val tensor = cachedEmptyPastK
        if (tensor != null) return tensor
        val nHLoc = config.localNumAttentionHeads.toLong()
        val hdLoc = config.localHeadDim.toLong()
        val newTensor = OnnxTensor.createTensor(
            env,
            FloatBuffer.wrap(FloatArray(0)),
            longArrayOf(1, nHLoc, 0, hdLoc)
        )
        cachedEmptyPastK = newTensor
        return newTensor
    }

    @Synchronized
    fun getOrCreateEmptyPastV(): OnnxTensor {
        val tensor = cachedEmptyPastV
        if (tensor != null) return tensor
        val nHLoc = config.localNumAttentionHeads.toLong()
        val hdLoc = config.localHeadDim.toLong()
        val newTensor = OnnxTensor.createTensor(
            env,
            FloatBuffer.wrap(FloatArray(0)),
            longArrayOf(1, nHLoc, 0, hdLoc)
        )
        cachedEmptyPastV = newTensor
        return newTensor
    }

    data class InferenceTiming(
        val prefillDurationMs: Long = 0L,
        val decodeDurationMs: Long = 0L,
        val backboneDurationMs: Long = 0L,
        val codecDurationMs: Long = 0L
    ) {
        val totalDurationMs: Long get() = backboneDurationMs + codecDurationMs
    }

    data class EngineInferenceResult(
        val pcm: FloatArray,
        val timing: InferenceTiming,
        val frameCount: Int = 0
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as EngineInferenceResult
            if (!pcm.contentEquals(other.pcm)) return false
            if (timing != other.timing) return false
            if (frameCount != other.frameCount) return false
            return true
        }

        override fun hashCode(): Int {
            var result = pcm.contentHashCode()
            result = 31 * result + timing.hashCode()
            result = 31 * result + frameCount
            return result
        }
    }

    fun infer(
        phonemes: String,
        voiceName: String = "Trúc Ly",
        temperature: Float = 0.8f,
        topK: Int = 25,
        topP: Float = 0.95f,
        maxNewFrames: Int = 600,
        repetitionPenalty: Float = 1.2f
    ): FloatArray {
        return inferWithTiming(
            phonemes = phonemes,
            voiceName = voiceName,
            temperature = temperature,
            topK = topK,
            topP = topP,
            maxNewFrames = maxNewFrames,
            repetitionPenalty = repetitionPenalty
        ).pcm
    }

    fun infer(
        phonemes: String,
        speakerEmb: FloatArray?,
        refCodes: Array<IntArray>?,
        temperature: Float = 0.8f,
        topK: Int = 25,
        topP: Float = 0.95f,
        maxNewFrames: Int = 600,
        repetitionPenalty: Float = 1.2f
    ): FloatArray {
        return inferWithTiming(
            phonemes = phonemes,
            speakerEmb = speakerEmb,
            refCodes = refCodes,
            temperature = temperature,
            topK = topK,
            topP = topP,
            maxNewFrames = maxNewFrames,
            repetitionPenalty = repetitionPenalty
        ).pcm
    }

    fun inferWithTiming(
        phonemes: String,
        voiceName: String = "Trúc Ly",
        temperature: Float = 0.8f,
        topK: Int = 25,
        topP: Float = 0.95f,
        maxNewFrames: Int = 600,
        repetitionPenalty: Float = 1.2f
    ): EngineInferenceResult {
        val preset = VoicePresets.getVoice(voiceName)
        val spkEmb = preset?.speakerEmb
        val refCodes = preset?.codes
        return inferWithTiming(
            phonemes = phonemes,
            speakerEmb = spkEmb,
            refCodes = refCodes,
            temperature = temperature,
            topK = topK,
            topP = topP,
            maxNewFrames = maxNewFrames,
            repetitionPenalty = repetitionPenalty
        )
    }

    fun inferWithTiming(
        phonemes: String,
        speakerEmb: FloatArray?,
        refCodes: Array<IntArray>?,
        temperature: Float = 0.8f,
        topK: Int = 25,
        topP: Float = 0.95f,
        maxNewFrames: Int = 600,
        repetitionPenalty: Float = 1.2f
    ): EngineInferenceResult = lock.withLock {
        if (isClosed) {
            throw IllegalStateException("VieNeuOnnxEngine is closed")
        }
        val backboneStart = System.currentTimeMillis()
        val anchor = speakerAnchor(speakerEmb)
        val styleId = config.defaultStyleTokenId
        val rows = buildRows(phonemes, refCodes, styleId)
        val promptEmbeds = embedRows(rows, anchor) // shape (1, T, H)
        val tPrompt = rows.size
        val H = config.hiddenSize
        val L = config.numHiddenLayers // 12

        // 1. Prefill Step
        val prefillStart = System.currentTimeMillis()
        var h: FloatArray // size H (last hidden vector)
        var pastK: List<CacheTensor>
        var pastV: List<CacheTensor>

        val prefillInputs = HashMap<String, OnnxTensor>()
        val promptTensor = OnnxTensor.createTensor(
            env,
            FloatBuffer.wrap(promptEmbeds),
            longArrayOf(1, tPrompt.toLong(), H.toLong())
        )
        prefillInputs["inputs_embeds"] = promptTensor

        sessionManager.prefillSession.run(prefillInputs).use { preResult ->
            promptTensor.close()

            val hiddenTensor = preResult.get(0) as OnnxTensor
            val hiddenFb = hiddenTensor.floatBuffer
            val hiddenFlat = FloatArray(hiddenFb.remaining())
            hiddenFb.get(hiddenFlat)

            // Extract last hidden row: hidden[0, tPrompt-1, :]
            h = FloatArray(H)
            System.arraycopy(hiddenFlat, (tPrompt - 1) * H, h, 0, H)

            val kList = ArrayList<CacheTensor>(L)
            val vList = ArrayList<CacheTensor>(L)
            for (i in 0 until L) {
                val kt = preResult.get(1 + i) as OnnxTensor
                val vt = preResult.get(1 + L + i) as OnnxTensor
                kList.add(CacheTensor.fromOnnxTensor(kt))
                vList.add(CacheTensor.fromOnnxTensor(vt))
            }
            pastK = kList
            pastV = vList
        }
        val prefillDuration = System.currentTimeMillis() - prefillStart

        // 2. Autoregressive Loop with Sliding Window Repetition History (window = 64)
        val decodeStart = System.currentTimeMillis()
        val effectiveMaxFrames = min(maxNewFrames, maxExpectedFrames(phonemes))
        val frames = ArrayList<IntArray>()
        val repHistory = if (kotlin.math.abs(repetitionPenalty - 1.0f) > 1e-4f) {
            RepetitionHistory(nChannels = config.nVq, window = RepetitionHistory.DEFAULT_REP_WINDOW)
        } else {
            null
        }

        var currentKList = ArrayList<CacheTensor>(L)
        var currentVList = ArrayList<CacheTensor>(L)
        var nextKList = ArrayList<CacheTensor>(L)
        var nextVList = ArrayList<CacheTensor>(L)

        currentKList.addAll(pastK)
        currentVList.addAll(pastV)

        val slotRow = decodeSlotRowBuffer
        slotRow[0] = config.speechGenerationStartTokenId
        val slotEmbeds = decodeSlotEmbedsBuffer
        val posBuffer = decodePosBuffer
        val decodeInputs = decodeInputsMap
        val createdCacheTensors = ArrayList<OnnxTensor>(2 * L)

        for (t in 0 until effectiveMaxFrames) {
            val (codes, eos) = acousticFrame(
                h = h,
                temperature = temperature,
                topK = topK,
                topP = topP,
                repetitionPenalty = repetitionPenalty,
                repHistory = repHistory
            )
            frames.add(codes)
            if (eos) {
                break
            }

            // Build decode step slot: [sgs, codes[0], ..., codes[15]]
            System.arraycopy(codes, 0, slotRow, 1, config.nVq)
            embedSlot(slotRow, anchor, slotEmbeds)

            posBuffer[0] = (tPrompt + t).toLong()
            decodeInputs.clear()
            createdCacheTensors.clear()

            val seTensor = OnnxTensor.createTensor(
                env,
                FloatBuffer.wrap(slotEmbeds),
                longArrayOf(1, 1, H.toLong())
            )
            val posTensor = OnnxTensor.createTensor(
                env,
                LongBuffer.wrap(posBuffer),
                longArrayOf(1, 1)
            )
            decodeInputs["inputs_embeds"] = seTensor
            decodeInputs["position_ids"] = posTensor

            for (i in 0 until L) {
                val kt = currentKList[i].toOnnxTensor(env)
                val vt = currentVList[i].toOnnxTensor(env)
                createdCacheTensors.add(kt)
                createdCacheTensors.add(vt)
                decodeInputs["past_k_$i"] = kt
                decodeInputs["past_v_$i"] = vt
            }

            sessionManager.decodeSession.run(decodeInputs).use { decResult ->
                seTensor.close()
                posTensor.close()
                for (ct in createdCacheTensors) ct.close()

                val hiddenTensor = decResult.get(0) as OnnxTensor
                val hiddenFb = hiddenTensor.floatBuffer
                hiddenFb.get(h, 0, H)

                nextKList.clear()
                nextVList.clear()
                for (i in 0 until L) {
                    val kt = decResult.get(1 + i) as OnnxTensor
                    val vt = decResult.get(1 + L + i) as OnnxTensor
                    nextKList.add(CacheTensor.fromOnnxTensor(kt))
                    nextVList.add(CacheTensor.fromOnnxTensor(vt))
                }

                // Recycle old CacheTensors
                for (i in 0 until L) {
                    currentKList[i].recycle()
                    currentVList[i].recycle()
                }

                currentKList.clear()
                currentVList.clear()
                val tempK = currentKList
                val tempV = currentVList
                currentKList = nextKList
                currentVList = nextVList
                nextKList = tempK
                nextVList = tempV
            }
        }

        // Clean up last pastK/pastV after loop
        for (ct in currentKList) ct.recycle()
        for (ct in currentVList) ct.recycle()
        currentKList.clear()
        currentVList.clear()
        decodeInputs.clear()
        createdCacheTensors.clear()

        val decodeDuration = System.currentTimeMillis() - decodeStart
        val backboneDuration = System.currentTimeMillis() - backboneStart

        if (frames.isEmpty()) {
            return EngineInferenceResult(
                pcm = FloatArray(0),
                timing = InferenceTiming(
                    prefillDurationMs = prefillDuration,
                    decodeDurationMs = decodeDuration,
                    backboneDurationMs = backboneDuration,
                    codecDurationMs = 0L
                ),
                frameCount = 0
            )
        }

        // 3. MOSS Codec Decode
        val codecStart = System.currentTimeMillis()
        val pcm = decodeCodes(frames)
        val codecDuration = System.currentTimeMillis() - codecStart

        return EngineInferenceResult(
            pcm = pcm,
            timing = InferenceTiming(
                prefillDurationMs = prefillDuration,
                decodeDurationMs = decodeDuration,
                backboneDurationMs = backboneDuration,
                codecDurationMs = codecDuration
            ),
            frameCount = frames.size
        )
    }

    private fun acousticFrame(
        h: FloatArray,
        temperature: Float,
        topK: Int,
        topP: Float,
        repetitionPenalty: Float,
        repHistory: RepetitionHistory?
    ): Pair<IntArray, Boolean> {
        val H = config.hiddenSize
        val sgs = config.speechGenerationStartTokenId
        val txt = textEmbeddings[sgs] // size H

        val tok = acousticTokBuffer
        System.arraycopy(h, 0, tok, 0, H)
        System.arraycopy(txt, 0, tok, H, H)

        val feed = acousticFeedMap
        feed.clear()
        val tokTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(tok), longArrayOf(1, 2, H.toLong()))
        val posTensor0 = OnnxTensor.createTensor(env, LongBuffer.wrap(acousticPos0Buffer), longArrayOf(1, 2))
        val emptyPastK = getOrCreateEmptyPastK()
        val emptyPastV = getOrCreateEmptyPastV()

        feed["token_emb"] = tokTensor
        feed["position_ids"] = posTensor0
        feed["past_k_0"] = emptyPastK
        feed["past_v_0"] = emptyPastV

        val slot0 = acousticSlot0Buffer
        val vec = acousticVecBuffer
        var pk: CacheTensor
        var pv: CacheTensor
        val codes = IntArray(config.nVq)

        sessionManager.acousticSession.run(feed).use { out0 ->
            tokTensor.close()
            posTensor0.close()

            val hiddenTensor = out0.get(0) as OnnxTensor
            val hiddenFb = hiddenTensor.floatBuffer
            val hiddenFlat = acousticHiddenFlatBuffer
            hiddenFb.get(hiddenFlat, 0, 2 * H)

            System.arraycopy(hiddenFlat, 0, slot0, 0, H)
            System.arraycopy(hiddenFlat, H, vec, 0, H)

            val kt = out0.get(1) as OnnxTensor
            val vt = out0.get(2) as OnnxTensor
            pk = CacheTensor.fromOnnxTensor(kt)
            pv = CacheTensor.fromOnnxTensor(vt)

            // Sample code for channel 0 with sliding window history
            codes[0] = sampleCode(0, vec, temperature, topK, topP, repetitionPenalty, repHistory?.get(0)?.getCodes())
            repHistory?.get(0)?.add(codes[0])
        }

        val posBuffer = acousticPosBuffer
        // Subsequent channels 1..15
        for (ch in 1 until config.nVq) {
            val prevCode = codes[ch - 1]
            val emb = audioEmbeddings[ch - 1][prevCode] // size H

            posBuffer[0] = (ch + 1).toLong()
            feed.clear()
            val embTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(emb), longArrayOf(1, 1, H.toLong()))
            val posTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(posBuffer), longArrayOf(1, 1))
            val pastKTensor = pk.toOnnxTensor(env)
            val pastVTensor = pv.toOnnxTensor(env)

            feed["token_emb"] = embTensor
            feed["position_ids"] = posTensor
            feed["past_k_0"] = pastKTensor
            feed["past_v_0"] = pastVTensor

            sessionManager.acousticSession.run(feed).use { out ->
                embTensor.close()
                posTensor.close()
                pastKTensor.close()
                pastVTensor.close()

                val hiddenTensor = out.get(0) as OnnxTensor
                val hiddenFb = hiddenTensor.floatBuffer
                hiddenFb.get(vec, 0, H)

                val kt = out.get(1) as OnnxTensor
                val vt = out.get(2) as OnnxTensor

                pk.recycle()
                pv.recycle()

                pk = CacheTensor.fromOnnxTensor(kt)
                pv = CacheTensor.fromOnnxTensor(vt)

                codes[ch] = sampleCode(ch, vec, temperature, topK, topP, repetitionPenalty, repHistory?.get(ch)?.getCodes())
                repHistory?.get(ch)?.add(codes[ch])
            }
        }

        pk.recycle()
        pv.recycle()
        feed.clear()

        // Text logits for EOS check: slot0 @ text_emb.T
        val eosTokenId = config.speechGenerationEndTokenId
        val eosEmb = textEmbeddings[eosTokenId]
        var eosDot = 0f
        for (h in 0 until H) {
            eosDot += slot0[h] * eosEmb[h]
        }

        var isEos = true
        val textVocabSize = config.textVocabSize
        for (v in 0 until textVocabSize) {
            if (v == eosTokenId) continue
            val emb = textEmbeddings[v]
            var dot = 0f
            for (h in 0 until H) {
                dot += slot0[h] * emb[h]
            }
            if (dot >= eosDot) {
                isEos = false
                break
            }
        }
        val eos = isEos

        return Pair(codes, eos)
    }

    private fun sampleCode(
        ch: Int,
        vec: FloatArray,
        temperature: Float,
        topK: Int,
        topP: Float,
        repetitionPenalty: Float,
        prev: Set<Int>?
    ): Int {
        val audioVocabSize = audioEmbeddings[ch].size // 1024
        val logits = channelLogitsBuffer[ch]
        val H = config.hiddenSize
        val flatEmb = flatAudioEmbeddings[ch]

        for (v in 0 until audioVocabSize) {
            val offset = v * H
            var dot = 0f
            for (h in 0 until H) {
                dot += vec[h] * flatEmb[offset + h]
            }
            logits[v] = dot
        }

        return topKSample(
            logits = logits,
            k = topK,
            temperature = temperature,
            topP = topP,
            repetitionPenalty = repetitionPenalty,
            prevTokens = prev
        )
    }

    fun topKSample(
        logits: FloatArray,
        k: Int = 25,
        temperature: Float = 0.8f,
        topP: Float = 0.95f,
        repetitionPenalty: Float = 1.2f,
        prevTokens: Set<Int>? = null
    ): Int {
        val V = logits.size
        if (workingLogitsBuffer.size < V) {
            workingLogitsBuffer = FloatArray(V)
        }
        val workingLogits = workingLogitsBuffer
        System.arraycopy(logits, 0, workingLogits, 0, V)

        if (kotlin.math.abs(repetitionPenalty - 1.0f) > 1e-4f && prevTokens != null && prevTokens.isNotEmpty()) {
            for (idx in prevTokens) {
                if (idx in 0 until V) {
                    val sel = workingLogits[idx]
                    workingLogits[idx] = if (sel < 0) sel * repetitionPenalty else sel / repetitionPenalty
                }
            }
        }

        if (temperature <= 0f) {
            var bestIdx = 0
            var bestVal = workingLogits[0]
            for (i in 1 until V) {
                if (workingLogits[i] > bestVal) {
                    bestVal = workingLogits[i]
                    bestIdx = i
                }
            }
            return bestIdx
        }

        val invTemp = 1.0f / temperature
        for (i in 0 until V) {
            workingLogits[i] *= invTemp
        }

        val effK = if (k in 1 until V) k else V
        if (topKIndicesBuffer.size < effK) {
            topKIndicesBuffer = IntArray(effK)
            topKLogitsBuffer = FloatArray(effK)
            probsBuffer = DoubleArray(effK)
        }
        val topIndices = topKIndicesBuffer
        val topLogits = topKLogitsBuffer
        val probs = probsBuffer

        // Partial selection: min-heap of size effK
        var heapSize = 0
        for (i in 0 until V) {
            val logit = workingLogits[i]
            if (heapSize < effK) {
                var child = heapSize
                topIndices[child] = i
                heapSize++
                while (child > 0) {
                    val parent = (child - 1) shr 1
                    if (workingLogits[topIndices[child]] < workingLogits[topIndices[parent]]) {
                        val temp = topIndices[child]
                        topIndices[child] = topIndices[parent]
                        topIndices[parent] = temp
                        child = parent
                    } else {
                        break
                    }
                }
            } else if (logit > workingLogits[topIndices[0]]) {
                topIndices[0] = i
                var root = 0
                while (true) {
                    var smallest = root
                    val left = (root shl 1) + 1
                    val right = left + 1
                    if (left < effK && workingLogits[topIndices[left]] < workingLogits[topIndices[smallest]]) {
                        smallest = left
                    }
                    if (right < effK && workingLogits[topIndices[right]] < workingLogits[topIndices[smallest]]) {
                        smallest = right
                    }
                    if (smallest != root) {
                        val temp = topIndices[root]
                        topIndices[root] = topIndices[smallest]
                        topIndices[smallest] = temp
                        root = smallest
                    } else {
                        break
                    }
                }
            }
        }

        // Extract elements in descending order
        var curSize = effK
        for (pos in (effK - 1) downTo 0) {
            val rootIdx = topIndices[0]
            topLogits[pos] = workingLogits[rootIdx]

            curSize--
            if (curSize > 0) {
                topIndices[0] = topIndices[curSize]
                var root = 0
                while (true) {
                    var smallest = root
                    val left = (root shl 1) + 1
                    val right = left + 1
                    if (left < curSize && workingLogits[topIndices[left]] < workingLogits[topIndices[smallest]]) {
                        smallest = left
                    }
                    if (right < curSize && workingLogits[topIndices[right]] < workingLogits[topIndices[smallest]]) {
                        smallest = right
                    }
                    if (smallest != root) {
                        val temp = topIndices[root]
                        topIndices[root] = topIndices[smallest]
                        topIndices[smallest] = temp
                        root = smallest
                    } else {
                        break
                    }
                }
            }
            topIndices[pos] = rootIdx
        }

        // Softmax over top-k
        val maxLogit = topLogits[0]
        var sumExp = 0.0
        for (i in 0 until effK) {
            val p = exp((topLogits[i] - maxLogit).toDouble())
            probs[i] = p
            sumExp += p
        }
        val invSumExp = 1.0 / sumExp
        for (i in 0 until effK) {
            probs[i] *= invSumExp
        }

        // Top-P Nucleus filtering
        if (topP in 0.0f..0.9999f) {
            var cumsum = 0.0
            var pSum = 0.0
            for (i in 0 until effK) {
                val p = probs[i]
                if (cumsum >= topP) {
                    probs[i] = 0.0
                } else {
                    cumsum += p
                    pSum += p
                }
            }
            if (pSum > 0.0) {
                val invPSum = 1.0 / pSum
                for (i in 0 until effK) {
                    probs[i] *= invPSum
                }
            }
        }

        // Random weighted sampling
        val r = Random.nextDouble()
        var accum = 0.0
        for (i in 0 until effK) {
            accum += probs[i]
            if (r <= accum || i == effK - 1) {
                return topIndices[i]
            }
        }

        return topIndices[0]
    }

    private fun decodeCodes(frames: List<IntArray>): FloatArray {
        val tGen = frames.size
        val nVq = config.nVq // 16
        val flatCodes = IntArray(tGen * nVq)
        for (t in 0 until tGen) {
            val frame = frames[t]
            System.arraycopy(frame, 0, flatCodes, t * nVq, nVq)
        }

        val feed = HashMap<String, OnnxTensor>()
        val codesTensor = OnnxTensor.createTensor(
            env,
            IntBuffer.wrap(flatCodes),
            longArrayOf(1, tGen.toLong(), nVq.toLong())
        )
        val lensTensor = OnnxTensor.createTensor(
            env,
            IntBuffer.wrap(intArrayOf(tGen)),
            longArrayOf(1)
        )

        feed["audio_codes"] = codesTensor
        feed["audio_code_lengths"] = lensTensor

        sessionManager.codecSession.run(feed).use { result ->
            codesTensor.close()
            lensTensor.close()

            val audioTensor = result.get(0) as OnnxTensor
            val shape = audioTensor.info.shape
            val fb = audioTensor.floatBuffer
            val totalElements = fb.remaining()

            if (shape.size == 3 && shape[1] == 2L) {
                val numSamples = shape[2].toInt()
                val ch0 = FloatArray(numSamples)
                val ch1 = FloatArray(numSamples)
                fb.get(ch0)
                fb.get(ch1)
                val mono = FloatArray(numSamples)
                for (i in 0 until numSamples) {
                    mono[i] = (ch0[i] + ch1[i]) / 2.0f
                }
                return mono
            } else {
                val out = FloatArray(totalElements)
                fb.get(out)
                return out
            }
        }
    }

    fun speakerAnchor(speakerEmb: FloatArray?): FloatArray? {
        if (!config.useSpeakerEmbedding) return null
        if (speakerEmb == null) {
            throw IllegalArgumentException("This model needs a speaker anchor: pass `speakerEmb`")
        }
        val w = xvecW ?: throw IllegalStateException("heads.npz has no xvec_proj weights")
        val b = xvecB ?: throw IllegalStateException("heads.npz has no xvec_b")
        val lnW = xvecLnW ?: throw IllegalStateException("heads.npz has no xvec_ln_w")
        val lnB = xvecLnB ?: throw IllegalStateException("heads.npz has no xvec_ln_b")
        val H = config.hiddenSize
        val spkDim = config.speakerEmbeddingDim

        val v = FloatArray(H)
        var sum = 0.0
        for (i in 0 until H) {
            var dot = 0.0
            val row = w[i]
            for (j in 0 until spkDim) {
                dot += speakerEmb[j].toDouble() * row[j].toDouble()
            }
            val vi = (dot + b[i].toDouble()).toFloat()
            v[i] = vi
            sum += vi
        }

        val mean = (sum / H).toFloat()
        var varSum = 0.0
        for (i in 0 until H) {
            val diff = (v[i] - mean).toDouble()
            varSum += diff * diff
        }
        val variance = (varSum / H).toFloat()
        val std = sqrt(variance + xvecLnEps)

        val anchor = FloatArray(H)
        for (i in 0 until H) {
            val norm = (v[i] - mean) / std
            anchor[i] = norm * lnW[i] + lnB[i]
        }
        return anchor
    }

    fun buildRows(
        phonemes: String,
        refCodes: Array<IntArray>?,
        styleId: Int = config.defaultStyleTokenId
    ): Array<IntArray> {
        val phoneIds = tokenizer.encode(phonemes)
        val textIds = ArrayList<Int>()
        textIds.add(styleId)
        textIds.add(config.textPromptStartTokenId)
        for (id in phoneIds) textIds.add(id)
        textIds.add(config.textPromptEndTokenId)

        val textLen = textIds.size
        val refLen = refCodes?.size ?: 0
        val totalRows = textLen + refLen
        val nVq = config.nVq

        val rows = Array(totalRows) { IntArray(nVq + 1) { config.audioPadTokenId } }
        for (t in 0 until textLen) {
            rows[t][0] = textIds[t]
        }
        if (refCodes != null) {
            for (r in 0 until refLen) {
                val rowIdx = textLen + r
                rows[rowIdx][0] = config.audioRefSlotTokenId
                for (ch in 0 until nVq) {
                    rows[rowIdx][ch + 1] = refCodes[r][ch]
                }
            }
        }
        return rows
    }

    fun embedRows(rows: Array<IntArray>, anchor: FloatArray?): FloatArray {
        val T = rows.size
        val H = config.hiddenSize
        val nVq = config.nVq
        val result = FloatArray(T * H)

        for (t in 0 until T) {
            val textId = rows[t][0]
            val textVec = textEmbeddings[textId]
            val offset = t * H
            for (h in 0 until H) {
                result[offset + h] = textVec[h]
            }

            for (ch in 0 until nVq) {
                val audioId = rows[t][ch + 1]
                if (audioId != config.audioPadTokenId) {
                    val audioVec = audioEmbeddings[ch][audioId]
                    for (h in 0 until H) {
                        result[offset + h] += audioVec[h]
                    }
                }
            }
        }

        if (anchor != null) {
            for (t in 0 until T) {
                val offset = t * H
                for (h in 0 until H) {
                    result[offset + h] += anchor[h]
                }
            }
        }

        return result
    }

    fun embedSlot(slotRow: IntArray, anchor: FloatArray?, out: FloatArray) {
        val H = config.hiddenSize
        val nVq = config.nVq
        val textId = slotRow[0]
        val textVec = textEmbeddings[textId]
        System.arraycopy(textVec, 0, out, 0, H)

        for (ch in 0 until nVq) {
            val audioId = slotRow[ch + 1]
            if (audioId != config.audioPadTokenId) {
                val audioVec = audioEmbeddings[ch][audioId]
                for (h in 0 until H) {
                    out[h] += audioVec[h]
                }
            }
        }

        if (anchor != null) {
            for (h in 0 until H) {
                out[h] += anchor[h]
            }
        }
    }

    override fun close() {
        lock.withLock {
            if (closed) return
            closed = true
            try {
                cachedEmptyPastK?.close()
                cachedEmptyPastK = null
            } catch (_: Exception) {}
            try {
                cachedEmptyPastV?.close()
                cachedEmptyPastV = null
            } catch (_: Exception) {}
            try {
                sessionManager.close()
            } catch (_: Exception) {}
            CacheTensor.Pool.clear()
            decodeInputsMap.clear()
            acousticFeedMap.clear()
            workingLogitsBuffer = FloatArray(0)
            topKIndicesBuffer = IntArray(0)
            topKLogitsBuffer = FloatArray(0)
            probsBuffer = DoubleArray(0)
        }
    }

    /**
     * Cache tensor holder for past key/value states.
     */
    data class CacheTensor(
        var shape: LongArray,
        var data: FloatArray,
        var size: Int = data.size
    ) {
        fun toOnnxTensor(env: OrtEnvironment): OnnxTensor {
            return OnnxTensor.createTensor(env, FloatBuffer.wrap(data, 0, size), shape)
        }

        fun recycle() {
            Pool.release(this)
        }

        object Pool {
            private const val MAX_POOL_SIZE = 64
            private val pool = ArrayDeque<CacheTensor>(MAX_POOL_SIZE)

            @Synchronized
            fun obtain(shape: LongArray, data: FloatArray): CacheTensor {
                val cached = if (pool.isNotEmpty()) pool.removeLast() else null
                return if (cached != null) {
                    cached.shape = shape
                    cached.data = data
                    cached.size = data.size
                    cached
                } else {
                    CacheTensor(shape, data, data.size)
                }
            }

            @Synchronized
            fun obtain(shape: LongArray, requiredSize: Int): CacheTensor {
                val cached = if (pool.isNotEmpty()) pool.removeLast() else null
                return if (cached != null) {
                    cached.shape = shape
                    if (cached.data.size < requiredSize) {
                        cached.data = FloatArray(requiredSize)
                    }
                    cached.size = requiredSize
                    cached
                } else {
                    CacheTensor(shape, FloatArray(requiredSize), requiredSize)
                }
            }

            @Synchronized
            fun release(tensor: CacheTensor) {
                if (pool.size < MAX_POOL_SIZE) {
                    tensor.shape = LongArray(0)
                    tensor.size = 0
                    pool.addLast(tensor)
                }
            }

            @Synchronized
            fun clear() {
                pool.clear()
            }

            val pooledCount: Int
                @Synchronized get() = pool.size
        }

        companion object {
            fun fromOnnxTensor(tensor: OnnxTensor): CacheTensor {
                val shape = tensor.info.shape.clone()
                val fb = tensor.floatBuffer
                val requiredSize = fb.remaining()
                val cached = Pool.obtain(shape, requiredSize)
                fb.get(cached.data, 0, requiredSize)
                return cached
            }
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as CacheTensor
            if (!shape.contentEquals(other.shape)) return false
            if (size != other.size) return false
            for (i in 0 until size) {
                if (data[i] != other.data[i]) return false
            }
            return true
        }

        override fun hashCode(): Int {
            var result = shape.contentHashCode()
            result = 31 * result + size
            for (i in 0 until size) {
                result = 31 * result + data[i].toBits()
            }
            return result
        }
    }

    companion object {
        private const val TAG = "VieNeuOnnxEngine"

        const val MAX_FRAMES_PER_PHONE: Double = 2.0
        const val FRAME_CAP_SLACK: Int = 24
        const val SINGLE_WORD_MAX_FRAMES: Int = 13
        const val SINGLE_WORD_MAX_PHONES: Int = 24
        private val FRAME_MARKUP_RE = Regex("""<\|emotion_\d+\|>|</?en>""")

        fun maxExpectedFrames(phonemes: String): Int {
            val stripped = FRAME_MARKUP_RE.replace(phonemes, "")
            val effLen = stripped.length
            var cap = FRAME_CAP_SLACK + ceil(MAX_FRAMES_PER_PHONE * effLen).toInt()
            val words = stripped.trim().split(Regex("""\s+""")).filter { it.isNotEmpty() }
            if (words.size <= 1 && effLen <= SINGLE_WORD_MAX_PHONES && !phonemes.contains("<|emotion_")) {
                cap = min(cap, SINGLE_WORD_MAX_FRAMES)
            }
            return cap
        }

        fun create(
            context: Context,
            threads: Int = 0,
            precision: String = "FP32"
        ): VieNeuOnnxEngine {
            val sessionManager = OnnxSessionManager.create(context, threads, precision)

            fun openBackboneAsset(filename: String): java.io.InputStream {
                return context.assets.open("${VieNeuConfig.BACKBONE_DIR}/$filename")
            }

            val configStr = openBackboneAsset("config.json")
                .bufferedReader(Charsets.UTF_8).use { it.readText() }
            val config = VieNeuConfig.fromJson(configStr)

            val tokenizer = openBackboneAsset("tokenizer.json").use {
                VieNeuTokenizer.fromInputStream(it)
            }

            val headsReader = openBackboneAsset("vieneu_v3_heads.npz").use {
                NpzReader.read(it)
            }

            val textEmb = headsReader.get2D("text_emb")
                ?: throw IllegalStateException("text_emb missing from vieneu_v3_heads.npz")
            val audioEmb = headsReader.get3D("audio_emb")
                ?: throw IllegalStateException("audio_emb missing from vieneu_v3_heads.npz")
            val xvecW = headsReader.get2D("xvec_w")
            val xvecB = headsReader.getFloatArray("xvec_b")
            val xvecLnW = headsReader.getFloatArray("xvec_ln_w")
            val xvecLnB = headsReader.getFloatArray("xvec_ln_b")
            val xvecLnEps = headsReader.getScalarFloat("xvec_ln_eps") ?: 1e-5f

            VoicePresets.loadFromContext(context)

            return VieNeuOnnxEngine(
                sessionManager = sessionManager,
                config = config,
                tokenizer = tokenizer,
                textEmbeddings = textEmb,
                audioEmbeddings = audioEmb,
                xvecW = xvecW,
                xvecB = xvecB,
                xvecLnW = xvecLnW,
                xvecLnB = xvecLnB,
                xvecLnEps = xvecLnEps
            )
        }

        fun createFromFiles(
            backboneDir: File,
            codecDir: File,
            threads: Int = 0
        ): VieNeuOnnxEngine {
            val sessionManager = OnnxSessionManager.createFromFiles(backboneDir, codecDir, threads)

            val configFile = File(backboneDir, "config.json")
            val config = VieNeuConfig.fromJson(configFile.readText(Charsets.UTF_8))

            val tokenizerFile = File(backboneDir, "tokenizer.json")
            val tokenizer = VieNeuTokenizer.fromFile(tokenizerFile)

            val headsFile = File(backboneDir, "vieneu_v3_heads.npz")
            val headsReader = NpzReader.read(headsFile)

            val textEmb = headsReader.get2D("text_emb")
                ?: throw IllegalStateException("text_emb missing from vieneu_v3_heads.npz")
            val audioEmb = headsReader.get3D("audio_emb")
                ?: throw IllegalStateException("audio_emb missing from vieneu_v3_heads.npz")
            val xvecW = headsReader.get2D("xvec_w")
            val xvecB = headsReader.getFloatArray("xvec_b")
            val xvecLnW = headsReader.getFloatArray("xvec_ln_w")
            val xvecLnB = headsReader.getFloatArray("xvec_ln_b")
            val xvecLnEps = headsReader.getScalarFloat("xvec_ln_eps") ?: 1e-5f

            return VieNeuOnnxEngine(
                sessionManager = sessionManager,
                config = config,
                tokenizer = tokenizer,
                textEmbeddings = textEmb,
                audioEmbeddings = audioEmb,
                xvecW = xvecW,
                xvecB = xvecB,
                xvecLnW = xvecLnW,
                xvecLnB = xvecLnB,
                xvecLnEps = xvecLnEps
            )
        }
    }
}
