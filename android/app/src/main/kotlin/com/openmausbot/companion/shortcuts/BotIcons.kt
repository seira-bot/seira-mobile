package com.openmausbot.companion.shortcuts

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import androidx.core.graphics.ColorUtils
import com.openmausbot.companion.character.CarvedAvatarSnapshot
import com.openmausbot.companion.core.SeiraLook
import com.openmausbot.companion.ui.BotPalette
import com.openmausbot.companion.ui.theme.identityPlateArgb

/** Bot faces drawn for system surfaces, which cannot show a Compose avatar. */
object BotIcons {
    private const val INK = 0xFF141417.toInt()
    private const val WHITE = 0xFFFFFFFF.toInt()
    private const val ADAPTIVE_CONTENT = 0.62f
    private const val CHARACTER_PX = 192

    /** The carved Seira avatar of [identity] on a transparent background, for the icons below. */
    fun character(context: Context, identity: BotIdentity): Bitmap {
        val look = SeiraLook.of(identity.mascotBody, identity.color, accessories = identity.mascotAccessories)
        return CarvedAvatarSnapshot.render(context, look.hair, look.color, look.accessories, CHARACTER_PX)
    }

    /** With no [avatar] photo, [character] is drawn on the identity plate; with neither, the initial of [name] is. */
    fun circle(avatar: Bitmap?, name: String, color: String?, size: Int = 192, character: Bitmap? = null): Bitmap {
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        drawFace(Canvas(out), avatar, character, name, color, size / 2f, size / 2f, size / 2f)
        return out
    }

    /** A square with a full-bleed plate: the launcher masks it, so the face stays in the safe zone. */
    fun adaptive(avatar: Bitmap?, name: String, color: String?, size: Int = 288, character: Bitmap? = null): Bitmap {
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(if (avatar == null && character != null) characterPlate(color) else plateColor(color))
        drawFace(canvas, avatar, character, name, color, size / 2f, size / 2f, size * ADAPTIVE_CONTENT / 2f)
        return out
    }

    fun characterPlate(color: String?): Int = identityPlateArgb(color, dark = false)

    fun initial(name: String): String {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return "?"
        return String(Character.toChars(trimmed.codePointAt(0))).uppercase()
    }

    private fun plateColor(color: String?): Int =
        if (color == null) BotPalette.FALLBACK else BotPalette.argb(color)

    private fun drawFace(
        canvas: Canvas,
        avatar: Bitmap?,
        character: Bitmap?,
        name: String,
        color: String?,
        cx: Float,
        cy: Float,
        radius: Float,
    ) {
        val fill = plateColor(color)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        if (avatar != null && avatar.width > 0 && avatar.height > 0) {
            val shader = BitmapShader(avatar, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            val side = minOf(avatar.width, avatar.height).toFloat()
            val scale = radius * 2f / side
            shader.setLocalMatrix(
                Matrix().apply {
                    postTranslate(-(avatar.width - side) / 2f, -(avatar.height - side) / 2f)
                    postScale(scale, scale)
                    postTranslate(cx - radius, cy - radius)
                },
            )
            paint.shader = shader
            canvas.drawCircle(cx, cy, radius, paint)
            return
        }
        if (character != null) {
            paint.color = characterPlate(color)
            canvas.drawCircle(cx, cy, radius, paint)
            val face = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            canvas.drawBitmap(character, null, RectF(cx - radius, cy - radius, cx + radius, cy + radius), face)
            return
        }
        paint.color = fill
        canvas.drawCircle(cx, cy, radius, paint)
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = if (ColorUtils.calculateLuminance(fill) > 0.5) INK else WHITE
            textSize = radius * 1.05f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        val baseline = cy - (text.descent() + text.ascent()) / 2f
        canvas.drawText(initial(name), cx, baseline, text)
    }
}
