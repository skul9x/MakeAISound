package skul9x.example.makesound.storage

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import skul9x.example.makesound.engine.VieNeuConfig
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Storage manager for audio preview caching and MediaStore export.
 *
 * Fully compliant with Android Scoped Storage (Android 10 - 15) without requiring
 * dangerous storage permissions for app-created media under `Music/MakeAiSound`.
 */
object AudioStorageManager {

    const val ALBUM_DIRECTORY = "Music/MakeAiSound"
    const val DEFAULT_PREFIX = "MakeAiSound"
    const val CACHE_DIR_NAME = "audio_preview"
    const val MIME_TYPE_WAV = "audio/wav"

    private val DATE_FORMAT = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

    /**
     * Generates a clean, intelligent default filename for exported audio.
     * E.g. `MakeAiSound_TrucLy_20260823_123045.wav`
     */
    fun generateFileName(
        speakerName: String,
        timestampMs: Long = System.currentTimeMillis(),
        extension: String = "wav"
    ): String {
        val sanitizedSpeaker = speakerName
            .replace("\\s+".toRegex(), "_")
            .replace("[^a-zA-Z0-9_\\-]".toRegex(), "")
            .ifEmpty { "Voice" }

        val timestamp = DATE_FORMAT.format(Date(timestampMs))
        val ext = extension.removePrefix(".")
        return "${DEFAULT_PREFIX}_${sanitizedSpeaker}_$timestamp.$ext"
    }

    /**
     * Retrieves or creates the internal cache directory for instant audio previews.
     */
    fun getPreviewCacheDir(context: Context): File {
        val dir = File(context.cacheDir, CACHE_DIR_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * Saves 16-bit PCM audio samples to a temporary WAV file in the app cache directory.
     * Executes on [Dispatchers.IO].
     */
    suspend fun saveToCache(
        context: Context,
        pcm16: ShortArray,
        sampleRate: Int = VieNeuConfig.SAMPLE_RATE,
        prefix: String = "preview"
    ): File = withContext(Dispatchers.IO) {
        val cacheDir = getPreviewCacheDir(context)
        val file = File(cacheDir, "${prefix}_${System.currentTimeMillis()}.wav")
        WavWriter.writeWav(file, pcm16, sampleRate)
        file
    }

    /**
     * Saves raw WAV byte buffer directly to a temporary WAV file in the app cache directory.
     * Executes on [Dispatchers.IO].
     */
    suspend fun saveToCache(
        context: Context,
        wavBytes: ByteArray,
        prefix: String = "preview"
    ): File = withContext(Dispatchers.IO) {
        val cacheDir = getPreviewCacheDir(context)
        val file = File(cacheDir, "${prefix}_${System.currentTimeMillis()}.wav")
        FileOutputStream(file).use { fos ->
            fos.write(wavBytes)
            fos.flush()
        }
        file
    }

    /**
     * Exports 16-bit PCM audio to the device's public `Music/MakeAiSound` storage via [MediaStore.Audio].
     *
     * @return [Uri] pointing to the inserted MediaStore item, or null if export failed.
     */
    suspend fun exportToMediaStore(
        context: Context,
        pcm16: ShortArray,
        speakerName: String,
        sampleRate: Int = VieNeuConfig.SAMPLE_RATE,
        customFileName: String? = null
    ): Uri? = withContext(Dispatchers.IO) {
        val fileName = customFileName ?: generateFileName(speakerName)
        val resolver = context.contentResolver

        val contentValues = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Audio.Media.MIME_TYPE, MIME_TYPE_WAV)
            put(MediaStore.Audio.Media.TITLE, fileName.removeSuffix(".wav"))
            put(MediaStore.Audio.Media.ARTIST, speakerName)
            put(MediaStore.Audio.Media.ALBUM, DEFAULT_PREFIX)
            put(MediaStore.Audio.Media.DATE_ADDED, System.currentTimeMillis() / 1000)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Audio.Media.RELATIVE_PATH, ALBUM_DIRECTORY)
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
        }

        val collectionUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val itemUri = resolver.insert(collectionUri, contentValues) ?: return@withContext null

        try {
            resolver.openOutputStream(itemUri)?.use { outStream ->
                WavWriter.writeWav(outStream, pcm16, sampleRate)
            } ?: throw IllegalStateException("Could not open output stream for URI: $itemUri")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Audio.Media.IS_PENDING, 0)
                resolver.update(itemUri, contentValues, null, null)
            }

            itemUri
        } catch (e: Exception) {
            // Clean up dangling entry if failed
            try {
                resolver.delete(itemUri, null, null)
            } catch (_: Exception) {}
            throw e
        }
    }

    /**
     * Exports an existing cached WAV [File] to the public `Music/MakeAiSound` directory via [MediaStore.Audio].
     */
    suspend fun exportToMediaStore(
        context: Context,
        wavFile: File,
        speakerName: String,
        customFileName: String? = null
    ): Uri? = withContext(Dispatchers.IO) {
        val fileName = customFileName ?: generateFileName(speakerName)
        val resolver = context.contentResolver

        val contentValues = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Audio.Media.MIME_TYPE, MIME_TYPE_WAV)
            put(MediaStore.Audio.Media.TITLE, fileName.removeSuffix(".wav"))
            put(MediaStore.Audio.Media.ARTIST, speakerName)
            put(MediaStore.Audio.Media.ALBUM, DEFAULT_PREFIX)
            put(MediaStore.Audio.Media.DATE_ADDED, System.currentTimeMillis() / 1000)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Audio.Media.RELATIVE_PATH, ALBUM_DIRECTORY)
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
        }

        val collectionUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val itemUri = resolver.insert(collectionUri, contentValues) ?: return@withContext null

        try {
            resolver.openOutputStream(itemUri)?.use { outStream ->
                FileInputStream(wavFile).use { inStream ->
                    inStream.copyTo(outStream)
                }
            } ?: throw IllegalStateException("Could not open output stream for URI: $itemUri")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Audio.Media.IS_PENDING, 0)
                resolver.update(itemUri, contentValues, null, null)
            }

            itemUri
        } catch (e: Exception) {
            try {
                resolver.delete(itemUri, null, null)
            } catch (_: Exception) {}
            throw e
        }
    }

    /**
     * Clears all cached preview audio files to free internal storage.
     *
     * @return Number of deleted cache files.
     */
    fun clearCache(context: Context): Int {
        val cacheDir = getPreviewCacheDir(context)
        val files = cacheDir.listFiles() ?: return 0
        var deleted = 0
        for (f in files) {
            if (f.isFile && f.delete()) {
                deleted++
            }
        }
        return deleted
    }
}
