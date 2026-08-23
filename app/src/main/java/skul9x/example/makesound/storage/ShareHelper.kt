package skul9x.example.makesound.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * Helper for generating FileProvider URIs and dispatching standard Android sharing intents
 * to external messaging, social media, and video editor applications (e.g. Zalo, Telegram, CapCut, TikTok).
 */
object ShareHelper {

    const val FILE_PROVIDER_AUTHORITY_SUFFIX = ".fileprovider"
    const val AUDIO_MIME_TYPE = "audio/wav"

    /**
     * Obtains a secure content [Uri] for a local [File] via [FileProvider].
     */
    fun getFileUri(context: Context, file: File): Uri {
        val authority = "${context.packageName}$FILE_PROVIDER_AUTHORITY_SUFFIX"
        return FileProvider.getUriForFile(context, authority, file)
    }

    /**
     * Builds an [Intent] for sharing a WAV audio file.
     */
    fun createShareIntent(
        context: Context,
        file: File,
        chooserTitle: String = "Chia sẻ âm thanh"
    ): Intent {
        val uri = getFileUri(context, file)
        return createShareIntentForUri(uri, chooserTitle)
    }

    /**
     * Builds an [Intent] for sharing an audio [Uri].
     */
    fun createShareIntentForUri(
        uri: Uri,
        chooserTitle: String = "Chia sẻ âm thanh"
    ): Intent {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = AUDIO_MIME_TYPE
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(shareIntent, chooserTitle).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * Launches the system share chooser dialog for a given audio file.
     */
    fun shareAudio(
        context: Context,
        file: File,
        chooserTitle: String = "Chia sẻ âm thanh"
    ) {
        val intent = createShareIntent(context, file, chooserTitle)
        context.startActivity(intent)
    }

    /**
     * Launches the system share chooser dialog for a given audio [Uri].
     */
    fun shareAudio(
        context: Context,
        uri: Uri,
        chooserTitle: String = "Chia sẻ âm thanh"
    ) {
        val intent = createShareIntentForUri(uri, chooserTitle)
        context.startActivity(intent)
    }
}
