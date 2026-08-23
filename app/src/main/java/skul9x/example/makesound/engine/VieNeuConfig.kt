package skul9x.example.makesound.engine

import org.json.JSONObject

/**
 * Configuration and hyperparameters for VieNeu-TTS v3 Turbo on-device engine.
 */
data class VieNeuConfig(
    val nVq: Int = 16,
    val hiddenSize: Int = 768,
    val numHiddenLayers: Int = 12,
    val audioPadTokenId: Int = 1024,
    val textPromptStartTokenId: Int = 3,
    val textPromptEndTokenId: Int = 4,
    val speechGenerationStartTokenId: Int = 5,
    val speechGenerationEndTokenId: Int = 6,
    val audioRefSlotTokenId: Int = 7,
    val textVocabSize: Int = 419,
    val defaultStyleTokenId: Int = 16,
    val localNumHiddenLayers: Int = 1,
    val localNumAttentionHeads: Int = 8,
    val useSpeakerEmbedding: Boolean = true,
    val speakerEmbeddingDim: Int = 192,
    val audioSampleRate: Int = 48000
) {
    val headDim: Int
        get() = hiddenSize / 12

    val localHeadDim: Int
        get() = hiddenSize / localNumAttentionHeads

    companion object {
        const val ASSET_DIR = "vieneu"
        const val BACKBONE_DIR = "$ASSET_DIR/backbone"
        const val CODEC_DIR = "$ASSET_DIR/codec"
        const val DENOISER_DIR = "$ASSET_DIR/denoiser"
        const val SAMPLE_RATE = 48000

        fun fromJson(jsonStr: String): VieNeuConfig {
            val json = JSONObject(jsonStr)
            return VieNeuConfig(
                nVq = json.optInt("n_vq", 16),
                hiddenSize = json.optInt("hidden_size", 768),
                numHiddenLayers = json.optInt("num_hidden_layers", 12),
                audioPadTokenId = json.optInt("audio_pad_token_id", 1024),
                textPromptStartTokenId = json.optInt("text_prompt_start_token_id", 3),
                textPromptEndTokenId = json.optInt("text_prompt_end_token_id", 4),
                speechGenerationStartTokenId = json.optInt("speech_generation_start_token_id", 5),
                speechGenerationEndTokenId = json.optInt("speech_generation_end_token_id", 6),
                audioRefSlotTokenId = json.optInt("audio_ref_slot_token_id", 7),
                textVocabSize = json.optInt("text_vocab_size", 419),
                defaultStyleTokenId = json.optInt("default_style_token_id", 16),
                localNumHiddenLayers = json.optInt("local_num_hidden_layers", 1),
                localNumAttentionHeads = json.optInt("local_num_attention_heads", 8),
                useSpeakerEmbedding = json.optBoolean("use_speaker_embedding", true),
                speakerEmbeddingDim = json.optInt("speaker_embedding_dim", 192),
                audioSampleRate = json.optInt("audio_sample_rate", 48000)
            )
        }
    }
}
