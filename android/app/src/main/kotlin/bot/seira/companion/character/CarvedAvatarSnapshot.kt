package bot.seira.companion.character

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import bot.seira.companion.ui.CharacterMood

internal object CarvedAvatarSnapshot {
    /** A square ARGB_8888 picture of the avatar on a transparent background. */
    fun render(
        context: Context,
        hair: String?,
        color: String,
        accessories: List<String>,
        sizePx: Int,
        mood: CharacterMood = CharacterMood.IDLE,
    ): Bitmap {
        require(sizePx > 0) { "The picture needs a size." }
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        CarvedAvatarRenderer.draw(
            Canvas(bitmap), context.assets, hair, color, accessories, moodPose(mood), sizePx.toFloat(),
        )
        return bitmap
    }
}
