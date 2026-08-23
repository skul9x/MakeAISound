package skul9x.example.makesound.engine

import android.content.Context

/**
 * Grapheme-to-Phoneme facade forwarding to JNI-bound `com.skul9x.doctruyen.tts.vieneu.SeaG2P`.
 */
object SeaG2P {
    fun isWarmedUp(): Boolean = com.skul9x.doctruyen.tts.vieneu.SeaG2P.isWarmedUp()

    fun clearCache() = com.skul9x.doctruyen.tts.vieneu.SeaG2P.clearCache()

    fun getCacheSize(): Int = com.skul9x.doctruyen.tts.vieneu.SeaG2P.getCacheSize()

    fun init(context: Context? = null) = com.skul9x.doctruyen.tts.vieneu.SeaG2P.init(context)

    fun warmUp(context: Context? = null) = com.skul9x.doctruyen.tts.vieneu.SeaG2P.warmUp(context)

    fun phonemize(text: String): String = com.skul9x.doctruyen.tts.vieneu.SeaG2P.phonemize(text)
}
