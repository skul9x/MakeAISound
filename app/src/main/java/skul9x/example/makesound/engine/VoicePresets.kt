package skul9x.example.makesound.engine

import android.content.Context
import org.json.JSONObject
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * Preset voice definitions and loader for VieNeu-TTS.
 */
data class VoicePreset(
    val name: String,
    val description: String = "",
    val gender: String = "unknown",
    val region: String = "Bắc",
    val style: String = "tu_nhien",
    val speakerEmb: FloatArray,
    val codes: Array<IntArray>? = null,
    val featured: Int? = null
) {
    val genderDisplay: String
        get() = when (gender.trim().lowercase()) {
            "female", "nữ", "nu" -> "Nữ"
            "male", "nam" -> "Nam"
            else -> {
                if (description.contains("Nữ", ignoreCase = true)) "Nữ"
                else if (description.contains("Nam", ignoreCase = true) &&
                    !description.contains("Miền Nam", ignoreCase = true) &&
                    !description.contains("· Nam", ignoreCase = true) &&
                    !description.contains("- Nam", ignoreCase = true)) "Nam"
                else if (gender.isNotBlank() && gender != "unknown") gender
                else ""
            }
        }

    val regionDisplay: String
        get() {
            val r = region.trim()
            val effectiveRegion = when {
                r.isNotEmpty() && !r.equals("unknown", ignoreCase = true) -> r
                description.contains("Miền Bắc", ignoreCase = true) || description.contains("Bắc", ignoreCase = true) -> "Bắc"
                description.contains("Miền Nam", ignoreCase = true) || description.contains("Nam", ignoreCase = true) -> "Nam"
                description.contains("Miền Trung", ignoreCase = true) || description.contains("Trung", ignoreCase = true) -> "Trung"
                else -> ""
            }

            return when {
                effectiveRegion.isEmpty() -> ""
                effectiveRegion.startsWith("Giọng ", ignoreCase = true) -> effectiveRegion
                effectiveRegion.startsWith("Miền ", ignoreCase = true) -> "Giọng $effectiveRegion"
                effectiveRegion.equals("Bắc", ignoreCase = true) -> "Giọng Miền Bắc"
                effectiveRegion.equals("Nam", ignoreCase = true) -> "Giọng Miền Nam"
                effectiveRegion.equals("Trung", ignoreCase = true) -> "Giọng Miền Trung"
                else -> "Giọng $effectiveRegion"
            }
        }

    val styleDisplay: String
        get() {
            val descLower = description.lowercase()
            if (descLower.contains("phong cách tin tức") || descLower.contains("tin tức")) {
                return "Phong cách tin tức"
            }
            if (descLower.contains("phong cách kể chuyện") || descLower.contains("kể chuyện")) {
                return "Phong cách kể chuyện"
            }
            if (descLower.contains("phong cách đọc truyện") || descLower.contains("đọc truyện")) {
                return "Phong cách đọc truyện"
            }
            if (descLower.contains("giọng đọc tự nhiên")) {
                return "Giọng đọc tự nhiên"
            }
            if (descLower.contains("phong cách tự nhiên") || descLower.contains("tự nhiên")) {
                return "Phong cách tự nhiên"
            }
            if (descLower.contains("kiếm hiệp")) {
                return "Phong cách kiếm hiệp"
            }

            val s = style.trim().lowercase()
            return when {
                s in listOf("tin_tuc", "tintuc", "news") -> "Phong cách tin tức"
                s in listOf("doc_truyen", "doctruyen", "story", "kể chuyện", "ke_chuyen") -> "Phong cách kể chuyện"
                s in listOf("tu_nhien", "tunhien", "natural") -> "Phong cách tự nhiên"
                s in listOf("kiem_hiep", "kiemhiep") -> "Phong cách kiếm hiệp"
                s.isNotEmpty() && s != "unknown" -> "Phong cách $s"
                else -> "Phong cách tự nhiên"
            }
        }

    fun getFormattedDescription(): String {
        val parts = mutableListOf<String>()
        if (genderDisplay.isNotEmpty()) parts.add(genderDisplay)
        if (regionDisplay.isNotEmpty()) parts.add(regionDisplay)
        if (styleDisplay.isNotEmpty()) parts.add(styleDisplay)
        return parts.joinToString(" - ")
    }

    fun getFormattedDisplayName(): String {
        val desc = getFormattedDescription()
        return if (desc.isNotEmpty()) {
            "$name - $desc"
        } else {
            name
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as VoicePreset
        if (name != other.name) return false
        if (featured != other.featured) return false
        if (!speakerEmb.contentEquals(other.speakerEmb)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + (featured ?: 0)
        result = 31 * result + speakerEmb.contentHashCode()
        return result
    }
}

/**
 * Registry and loader for all 25 Vietnamese preset voices.
 */
object VoicePresets {
    private val presetsMap = ConcurrentHashMap<String, VoicePreset>()
    var defaultVoiceName: String = "Ngọc Huyền"

    val availableVoices: List<String>
        get() = presetsMap.keys.toList()

    fun registerVoice(preset: VoicePreset) {
        presetsMap[preset.name] = preset
    }

    fun getVoice(name: String): VoicePreset? {
        return presetsMap[name] ?: presetsMap[defaultVoiceName]
    }

    fun getVoicePresets(): List<VoicePreset> {
        return presetsMap.values.toList()
    }

    fun getFeaturedVoices(): List<VoicePreset> {
        return presetsMap.values
            .filter { it.featured != null && it.featured > 0 }
            .sortedBy { it.featured }
    }

    fun getVoiceEmbedding(name: String): FloatArray? {
        return getVoice(name)?.speakerEmb
    }

    fun getRefCodes(name: String): Array<IntArray>? {
        return getVoice(name)?.codes
    }

    /**
     * Strips trailing encoder pad frame (Issue #198 upstream fix) where codebook-0 is 455.
     * MOSS audio tokenizer pads clips not aligned to 3840 samples with code 455.
     */
    fun stripEncoderPadFrame(codes: Array<IntArray>?): Array<IntArray>? {
        if (codes == null) return null
        if (codes.size >= 2 && codes.last().isNotEmpty() && codes.last()[0] == 455) {
            return codes.copyOfRange(0, codes.size - 1)
        }
        return codes
    }

    fun loadFromContext(context: Context, assetPath: String = "vieneu/voices_v3_turbo.json"): Boolean {
        return try {
            context.assets.open(assetPath).use { loadFromInputStream(it) }
            true
        } catch (e: Exception) {
            false
        }
    }

    fun loadFromInputStream(inputStream: InputStream) {
        val jsonStr = inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        loadFromJson(jsonStr)
    }

    fun loadFromJson(jsonStr: String) {
        val root = JSONObject(jsonStr)
        if (root.has("default_voice")) {
            defaultVoiceName = root.getString("default_voice")
        }
        val presetsObj = root.optJSONObject("presets") ?: return
        val keys = presetsObj.keys()
        while (keys.hasNext()) {
            val name = keys.next()
            val vObj = presetsObj.getJSONObject(name)
            val desc = vObj.optString("description", "")
            val gender = vObj.optString("gender", "")
            val region = vObj.optString("region", "")
            val style = vObj.optString("style", "tu_nhien")

            val featured = if (vObj.has("featured")) {
                val f = vObj.optInt("featured", 0)
                if (f > 0) f else null
            } else null

            val embArr = vObj.getJSONArray("speaker_emb")
            val emb = FloatArray(embArr.length()) { i -> embArr.getDouble(i).toFloat() }

            var codes: Array<IntArray>? = null
            if (vObj.has("codes")) {
                val codesArr = vObj.getJSONArray("codes")
                val nFrames = codesArr.length()
                if (nFrames > 0) {
                    val rawCodes = Array(nFrames) { f ->
                        val frameArr = codesArr.getJSONArray(f)
                        IntArray(frameArr.length()) { c -> frameArr.getInt(c) }
                    }
                    codes = stripEncoderPadFrame(rawCodes)
                }
            }

            registerVoice(
                VoicePreset(
                    name = name,
                    description = desc,
                    gender = gender,
                    region = region,
                    style = style,
                    speakerEmb = emb,
                    codes = codes,
                    featured = featured
                )
            )
        }
    }
}
