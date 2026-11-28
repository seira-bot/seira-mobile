package bot.seira.companion.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import bot.seira.companion.character.CarvedAvatarSnapshot
import bot.seira.companion.shortcuts.BotIcons
import bot.seira.companion.ui.BotPalette
import java.io.File

/**
 * Small face pictures for the widget, drawn once per look and kept as PNG files.
 * A RemoteViews update has a bitmap memory budget, so a face stays at [SIZE_PX].
 */
class WidgetFaces(private val context: Context, private val directory: File) {
    constructor(context: Context, store: WidgetSnapshotStore) : this(context, store.facesDirectory)

    /** Draws and saves the face of every look in [rows] that has no file yet. */
    fun ensure(rows: List<WidgetSnapshot.Row>) {
        for (row in rows.distinctBy(WidgetSnapshot.Row::lookKey)) {
            val file = fileFor(row.lookKey)
            if (file.isFile) continue
            directory.mkdirs()
            val bitmap = draw(row)
            val temp = File.createTempFile("face", ".tmp", directory)
            try {
                temp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                if (!temp.renameTo(file)) temp.delete()
            } finally {
                temp.delete()
            }
        }
    }

    fun load(row: WidgetSnapshot.Row): Bitmap =
        fileFor(row.lookKey).takeIf(File::isFile)?.let { BitmapFactory.decodeFile(it.path) } ?: dot(row.color)

    fun fileFor(lookKey: String): File = File(directory, lookKey.replace(UNSAFE, "_") + ".png")

    private fun draw(row: WidgetSnapshot.Row): Bitmap {
        val parts = row.lookKey.split("|")
        if (parts.firstOrNull() == "room") return dot(row.color)
        val accessories = parts.getOrNull(2).orEmpty().split(",").filter(String::isNotBlank)
        val character = CarvedAvatarSnapshot.render(
            context, parts.getOrNull(0)?.takeIf(String::isNotBlank), row.color, accessories, SIZE_PX,
        )
        return BotIcons.circle(null, row.name, row.color, SIZE_PX, character)
    }

    companion object {
        const val SIZE_PX = 96
        private val UNSAFE = Regex("[^A-Za-z0-9._-]")

        fun dot(color: String): Bitmap {
            val out = Bitmap.createBitmap(SIZE_PX, SIZE_PX, Bitmap.Config.ARGB_8888)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = BotPalette.argb(color) }
            Canvas(out).drawCircle(SIZE_PX / 2f, SIZE_PX / 2f, SIZE_PX / 2f, paint)
            return out
        }
    }
}
