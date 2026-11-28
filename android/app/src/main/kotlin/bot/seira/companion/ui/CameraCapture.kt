package bot.seira.companion.ui

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

/**
 * Where the camera writes a photo before it is attached: the app's own cache,
 * handed to the camera app through the file provider. The folder is emptied
 * before each capture and after the photo is attached or dropped.
 */
internal object CameraCapture {
    private const val DIRECTORY = "camera-capture"
    private const val AUTHORITY_SUFFIX = "transcripts"

    fun newUri(context: Context): Uri {
        clear(context)
        val directory = File(context.cacheDir, DIRECTORY).apply { mkdirs() }
        val file = File(directory, "photo-${UUID.randomUUID()}.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.$AUTHORITY_SUFFIX", file)
    }

    fun clear(context: Context) {
        File(context.cacheDir, DIRECTORY).listFiles()?.forEach { it.delete() }
    }
}
