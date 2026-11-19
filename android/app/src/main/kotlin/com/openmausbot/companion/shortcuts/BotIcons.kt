package com.openmausbot.companion.shortcuts

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import androidx.core.graphics.ColorUtils
import com.openmausbot.companion.ui.MausPalette

/** Bot faces drawn for system surfaces, which cannot show a Compose avatar. */
object BotIcons {
    private const val INK = 0xFF141417.toInt()
    private const val WHITE = 0xFFFFFFFF.toInt()
    private const val ADAPTIVE_CONTENT = 0.62f

    fun circle(avatar: Bitmap?, name: String, color: String?, size: Int = 192): Bitmap {
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        drawFace(Canvas(out), avatar, name, color, size / 2f, size / 2f, size / 2f)
        return out
    }

    /** A square with a full-bleed plate: the launcher masks it, so the face stays in the safe zone. */
    fun adaptive(avatar: Bitmap?, name: String, color: String?, size: Int = 288): Bitmap {
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(plateColor(color))
        drawFace(canvas, avatar, name, color, size / 2f, size / 2f, size * ADAPTIVE_CONTENT / 2f)
        return out
    }

    fun initial(name: String): String {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return "?"
        return String(Character.toChars(trimmed.codePointAt(0))).uppercase()
    }

    private fun plateColor(color: String?): Int =
        if (color == null) MausPalette.FALLBACK else MausPalette.argb(color)

    private fun drawFace(
        canvas: Canvas,
        avatar: Bitmap?,
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
