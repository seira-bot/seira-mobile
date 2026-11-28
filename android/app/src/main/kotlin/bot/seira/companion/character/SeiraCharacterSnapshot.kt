package bot.seira.companion.character

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import bot.seira.companion.ui.CharacterMood

internal object SeiraCharacterSnapshot {
    /** One still frame on a transparent background: front view, eyes open, no sway. */
    fun render(
        context: Context,
        hair: String?,
        color: String,
        accessories: List<String>,
        faceInk: String?,
        sizePx: Int,
        mood: CharacterMood = CharacterMood.IDLE,
    ): Bitmap = render(context, hair, color, accessories, faceInk, sizePx, mood, talkLevel = 0f)

    internal fun render(
        context: Context,
        hair: String?,
        color: String,
        accessories: List<String>,
        faceInk: String?,
        sizePx: Int,
        mood: CharacterMood,
        talkLevel: Float,
    ): Bitmap {
        val assets = SeiraAssets.forContext(context)
        val look = FrameLook.of(assets, hair, color, accessories, faceInk, mood)
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        SeiraCharacterRenderer(assets).draw(Canvas(bitmap), look, sizePx, sizePx, talk = talkLevel)
        return bitmap
    }
}
