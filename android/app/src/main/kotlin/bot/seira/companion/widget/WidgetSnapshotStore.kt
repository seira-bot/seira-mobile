package bot.seira.companion.widget

import android.content.Context
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.serialization.json.Json

/**
 * Reads and writes the snapshot file. The app that publishes and the widget that
 * renders both go through this type, so the format cannot drift between them.
 *
 * Security invariant: the file holds message lines, so it lives in
 * `noBackupFilesDir`, which Auto Backup and device transfer never copy.
 */
class WidgetSnapshotStore(val directory: File) {
    val file: File get() = File(directory, FILE_NAME)
    val facesDirectory: File get() = File(directory, "faces")

    /** The last snapshot, or null when no readable one exists. An undecodable file stays on disk. */
    fun read(): WidgetSnapshot? = try {
        if (file.isFile) json.decodeFromString<WidgetSnapshot>(file.readText()) else null
    } catch (_: Exception) {
        null
    }

    /** Writes through a temp file and one atomic rename, so a reader never sees half a file. */
    fun write(snapshot: WidgetSnapshot) {
        directory.mkdirs()
        val temp = File.createTempFile("updates", ".tmp", directory)
        try {
            temp.writeText(json.encodeToString(WidgetSnapshot.serializer(), snapshot))
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally {
            temp.delete()
        }
    }

    /** Removes the snapshot and the faces. A missing file is not an error. */
    fun remove() {
        file.delete()
        facesDirectory.deleteRecursively()
    }

    companion object {
        const val FILE_NAME = "updates.json"
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        fun forContext(context: Context): WidgetSnapshotStore =
            WidgetSnapshotStore(File(context.applicationContext.noBackupFilesDir, "widget"))
    }
}
