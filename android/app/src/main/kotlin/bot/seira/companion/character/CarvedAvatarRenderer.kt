package bot.seira.companion.character

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.LruCache
import androidx.core.graphics.PathParser
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

/** Draws one carved Seira avatar onto a [Canvas]. Pictures and tinted heads are cached for the process. */
internal object CarvedAvatarRenderer {
    private const val UNITS = 240f
    private const val GLOW_MIN_PX = 40f

    // The desktop blurs with a Gaussian of 3.5 units. Skia reads a blur radius r as sigma 0.57735 * r + 0.5.
    private const val GLOW_RADIUS = (3.5f - 0.5f) / 0.57735f
    private const val CACHE_BYTES = 12 * 1024 * 1024

    private val bitmaps = object : LruCache<String, Bitmap>(CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    private val visors = ConcurrentHashMap<String, Path>()
    private val glowFilter by lazy { BlurMaskFilter(GLOW_RADIUS, BlurMaskFilter.Blur.NORMAL) }

    fun draw(
        canvas: Canvas,
        assets: AssetManager,
        hair: String?,
        color: String,
        accessories: List<String>,
        pose: CarvedPose,
        sizePx: Float,
        swayDegrees: Float = 0f,
        eyeColor: Int? = null,
    ) = draw(canvas, assets, CarvedLook.of(hair, accessories), color, pose, sizePx, swayDegrees, eyeColor)

    fun draw(
        canvas: Canvas,
        assets: AssetManager,
        look: CarvedLook,
        color: String,
        pose: CarvedPose,
        sizePx: Float,
        swayDegrees: Float = 0f,
        eyeColor: Int? = null,
    ) {
        val body = look.body
        val geometry = carvedGeometry(body, pose)
        val scale = sizePx / look.viewBox[2].toFloat()
        val glow = sizePx >= GLOW_MIN_PX
        canvas.save()
        try {
            canvas.scale(scale, scale)
            canvas.translate(-look.viewBox[0].toFloat(), -look.viewBox[1].toFloat())
            canvas.translate(BODY_PIVOT_X.toFloat(), BODY_PIVOT_Y.toFloat())
            canvas.rotate(geometry.lean.toFloat() + swayDegrees)
            canvas.scale(geometry.scaleX.toFloat(), geometry.scaleY.toFloat())
            canvas.translate(-BODY_PIVOT_X.toFloat(), -BODY_PIVOT_Y.toFloat())
            val headPx = UNITS * scale
            head(assets, body, color, headPx)?.let { canvas.drawBitmap(it, null, RectF(0f, 0f, UNITS, UNITS), bitmapPaint) }
            drawEyes(canvas, body, geometry, glow, eyeColor ?: body.eyes.ink)
            if (body.eyes.mouth) drawMouth(canvas, body, geometry, glow)
            for (sprite in look.sprites) {
                val picture = decode(assets, "accessory/${sprite.sticker.file}") ?: continue
                val x = sprite.x.toFloat()
                val y = sprite.y.toFloat()
                canvas.drawBitmap(picture, null, RectF(x, y, x + sprite.width.toFloat(), y + sprite.height.toFloat()), bitmapPaint)
            }
        } finally {
            canvas.restore()
        }
    }

    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    private fun drawEyes(canvas: Canvas, body: SeiraBody, g: CarvedGeometry, glow: Boolean, eyeInk: Int) {
        val visor = visors.getOrPut(body.id) { PathParser.createPathFromPathData(body.clip) }
        canvas.save()
        canvas.clipPath(visor)
        val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = eyeInk }
        val halo = Paint(ink).apply { maskFilter = glowFilter }
        for (eye in g.eyes) {
            canvas.save()
            canvas.rotate(eye.tilt.toFloat(), eye.pivotX.toFloat(), eye.pivotY.toFloat())
            val rect = RectF(
                eye.x.toFloat(), eye.y.toFloat(),
                (eye.x + eye.width).toFloat(), (eye.y + eye.height).toFloat(),
            )
            val radius = eye.radius.toFloat()
            if (glow) canvas.drawRoundRect(rect, radius, radius, halo)
            canvas.drawRoundRect(rect, radius, radius, ink)
            canvas.restore()
        }
        canvas.restore()
    }

    private fun drawMouth(canvas: Canvas, body: SeiraBody, g: CarvedGeometry, glow: Boolean) {
        val alpha = { opacity: Double -> (opacity.coerceIn(0.0, 1.0) * 255).roundToInt() }
        val ink = body.eyes.mouthInk
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = body.eyes.w * 0.36f
            strokeCap = Paint.Cap.ROUND
            color = ink
            this.alpha = alpha(g.smile.opacity)
        }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink; this.alpha = alpha(g.open.opacity) }
        val s = g.smile
        val smile = Path().apply {
            moveTo(s.startX.toFloat(), s.startY.toFloat())
            quadTo(s.controlX.toFloat(), s.controlY.toFloat(), s.endX.toFloat(), s.endY.toFloat())
        }
        val o = g.open
        val mouth = RectF(
            (o.cx - o.rx).toFloat(), (o.cy - o.ry).toFloat(), (o.cx + o.rx).toFloat(), (o.cy + o.ry).toFloat(),
        )
        val passes = if (glow) listOf(glowFilter, null) else listOf(null)
        for (filter in passes) {
            stroke.maskFilter = filter
            fill.maskFilter = filter
            if (g.smile.opacity > 0) {
                canvas.save()
                canvas.rotate(s.tilt.toFloat(), s.pivotX.toFloat(), s.pivotY.toFloat())
                canvas.drawPath(smile, stroke)
                canvas.restore()
            }
            if (g.open.opacity > 0 && o.rx > 0 && o.ry > 0) canvas.drawOval(mouth, fill)
        }
    }

    /** The head with its hair tinted for [color], at the picture size that suits a head [headPx] wide. */
    private fun head(assets: AssetManager, body: SeiraBody, color: String, headPx: Float): Bitmap? {
        val size = SeiraBodies.pictureSizes.firstOrNull { it >= headPx } ?: SeiraBodies.pictureSizes.last()
        val tint = SeiraTint.hairTint(color)
        val hairPicture = body.hairPicture
        val key = "head:${body.id}:${if (tint == null || hairPicture == null) "plain" else color}:$size"
        synchronized(bitmaps) { bitmaps.get(key) }?.let { return it }
        val plain = decode(assets, "${body.id}/front-$size.webp") ?: return null
        if (tint == null || hairPicture == null) return plain
        val hair = decode(assets, "${body.id}/hair-$size.$hairPicture") ?: return plain
        if (hair.width != plain.width || hair.height != plain.height) return plain
        val headPixels = pixels(plain)
        val tinted = if (body.id == "seira") {
            SeiraTint.multiplyHair(headPixels, pixels(hair), tint)
        } else {
            SeiraTint.gradientHair(headPixels, pixels(hair), SeiraTint.gradientTables(tint))
        }
        val result = Bitmap.createBitmap(tinted, plain.width, plain.height, Bitmap.Config.ARGB_8888)
        synchronized(bitmaps) { bitmaps.put(key, result) }
        return result
    }

    private fun pixels(bitmap: Bitmap): IntArray {
        val out = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(out, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return out
    }

    private fun decode(assets: AssetManager, path: String): Bitmap? {
        val key = "file:$path"
        synchronized(bitmaps) { bitmaps.get(key) }?.let { return it }
        val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
        val bitmap = runCatching {
            assets.open("seira-mascot/$path").use { BitmapFactory.decodeStream(it, null, options) }
        }.getOrNull() ?: return null
        synchronized(bitmaps) { bitmaps.put(key, bitmap) }
        return bitmap
    }
}
