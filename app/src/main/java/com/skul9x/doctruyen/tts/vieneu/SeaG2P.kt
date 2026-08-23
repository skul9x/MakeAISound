package com.skul9x.doctruyen.tts.vieneu

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.util.Collections
import java.util.LinkedHashMap

/**
 * Native JNI bridge for sea-g2p library.
 * Note: Package name MUST match `com.skul9x.doctruyen.tts.vieneu` for JNI symbol resolution
 * in `libsea_g2p_android.so`.
 */
object SeaG2P {
    private const val MAX_CACHE_SIZE = 512

    private val phonemeCache: MutableMap<String, String> = Collections.synchronizedMap(
        object : LinkedHashMap<String, String>(MAX_CACHE_SIZE, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean {
                return size > MAX_CACHE_SIZE
            }
        }
    )

    @Volatile
    private var warmedUp = false

    init {
        try {
            System.loadLibrary("sea_g2p_android")
        } catch (e: UnsatisfiedLinkError) {
            val fallbackPaths = listOf(
                "src/main/jniLibs/x86_64/libsea_g2p_android.so",
                "app/src/main/jniLibs/x86_64/libsea_g2p_android.so",
                "../app/src/main/jniLibs/x86_64/libsea_g2p_android.so",
                "src/main/jniLibs/arm64-v8a/libsea_g2p_android.so",
                "app/src/main/jniLibs/arm64-v8a/libsea_g2p_android.so"
            )
            for (path in fallbackPaths) {
                val file = File(path)
                if (file.exists()) {
                    try {
                        System.load(file.absolutePath)
                        break
                    } catch (_: Throwable) {}
                }
            }
        }
    }

    private external fun nativeInit(dictPath: String): Boolean
    private external fun nativePhonemize(text: String): String

    fun isWarmedUp(): Boolean = warmedUp

    fun clearCache() {
        phonemeCache.clear()
    }

    fun getCacheSize(): Int = phonemeCache.size

    fun init(context: Context? = null) {
        warmUp(context)
    }

    @Synchronized
    fun warmUp(context: Context? = null) {
        if (warmedUp) return

        var dictPath: String? = null

        if (context != null) {
            try {
                val targetDir = File(context.filesDir, "vieneu")
                val dictFile = File(targetDir, "sea_g2p.bin")
                if (!dictFile.exists() || dictFile.length() == 0L) {
                    targetDir.mkdirs()
                    context.assets.open("vieneu/sea_g2p.bin").use { input ->
                        FileOutputStream(dictFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
                if (dictFile.exists()) {
                    dictPath = dictFile.absolutePath
                }
            } catch (_: Throwable) {}
        }

        if (dictPath == null || !File(dictPath).exists()) {
            val candidatePaths = listOf(
                "src/main/assets/vieneu/sea_g2p.bin",
                "app/src/main/assets/vieneu/sea_g2p.bin",
                "../app/src/main/assets/vieneu/sea_g2p.bin",
                "vieneu/sea_g2p.bin",
                "sea_g2p.bin"
            )
            for (candidate in candidatePaths) {
                val file = File(candidate)
                if (file.exists()) {
                    dictPath = file.absolutePath
                    break
                }
            }
        }

        if (dictPath != null) {
            try {
                nativeInit(dictPath)
            } catch (_: Throwable) {}
        }

        try {
            phonemize("Khởi động")
        } catch (_: Throwable) {}

        warmedUp = true
    }

    fun phonemize(text: String): String {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            return ""
        }

        phonemeCache[trimmed]?.let { return it }

        val phonemes = try {
            nativePhonemize(trimmed)
        } catch (e: UnsatisfiedLinkError) {
            trimmed
        } catch (e: Throwable) {
            trimmed
        }

        if (phonemes.isNotEmpty()) {
            phonemeCache[trimmed] = phonemes
        }

        return phonemes
    }
}
