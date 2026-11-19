package com.openmausbot.companion.character

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import com.openmausbot.companion.ui.CharacterMood
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private const val BOX_X0 = -1.9f
private const val BOX_X1 = 1.9f
private const val BOX_Y0 = -2.05f
private const val BOX_Y1 = 2.0f
private const val GROUND = 1.82f
private const val BODY_PIVOT_Y = 1.25f
private const val MAX_ACCESSORIES = 3
private const val NEAR_VIEW_YAW = 20f
private const val DEG = (Math.PI / 180.0).toFloat()

/** What the character wears and feels. Build it with [of] so equal looks compare equal. */
internal data class SeiraLook(
    val hair: String,
    val color: String,
    val accessories: List<String>,
    val faceInk: String?,
    val mood: CharacterMood,
) {
    companion object {
        /** Unknown hair becomes "seira". Unknown accessories drop out, then one per slot, at most three. */
        fun of(
            assets: SeiraAssets,
            hair: String?,
            color: String,
            accessories: List<String>,
            faceInk: String?,
            mood: CharacterMood,
        ): SeiraLook {
            val slots = HashSet<String>()
            val worn = accessories.take(MAX_ACCESSORIES).filter { id ->
                val spec = assets.accessory(id)
                spec != null && slots.add(spec.slot)
            }
            return SeiraLook(assets.styleFor(hair).id, color, worn, faceInk, mood)
        }
    }
}

private class ViewUse(
    val view: SeiraViewSpec,
    val frame: SeiraImage,
    val visor: SeiraImage?,
    val weight: Float,
    val mirror: Boolean,
) {
    val yaw: Float get() = (if (mirror) -1f else 1f) * view.yaw

    fun toWorld(px: Float, py: Float): Pair<Float, Float> {
        val x = (px - view.pivotX) / view.radius
        return (if (mirror) -x else x) to (py - view.pivotY) / view.radius
    }
}

private class FacePlace(val cx: Float, val cy: Float, val scale: Float, val squeeze: Float)

/** Draws the animated character with the layers of the desktop runtime. A renderer is not thread safe. */
internal class SeiraCharacterRenderer(private val assets: SeiraAssets) {
    private var headLayer: Bitmap? = null
    private var maskLayer: Bitmap? = null
    private var faceLayer: Bitmap? = null
    private val plain = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val add = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
    }
    private val destIn = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG)

    private var unit = 1f
    private var originX = 0f
    private var originY = 0f
    private var sway = 0f
    private var roll = 0f

    /**
     * Draws one frame into the box 0..[width] by 0..[height] of [canvas].
     * [yawDegrees] turns the head, [swayDegrees] leans the body, [blink] closes the eyes (0..1) and [talk] opens the mouth (0..1).
     */
    fun draw(
        canvas: Canvas,
        look: SeiraLook,
        width: Int,
        height: Int,
        yawDegrees: Float = 0f,
        swayDegrees: Float = 0f,
        blink: Float = 0f,
        talk: Float = 0f,
    ) {
        if (width <= 0 || height <= 0) return
        val style = assets.styleFor(look.hair)
        val spec = SEIRA_EXPRESSIONS.getValue(seiraExpressionFor(look.mood))
        val ink = seiraFaceInk(look.faceInk)
        val tint = SeiraRecolor.tintFor(style.id, look.color)

        unit = min(width / (BOX_X1 - BOX_X0), height / (BOX_Y1 - BOX_Y0))
        originX = width / 2f - (BOX_X0 + BOX_X1) / 2f * unit
        originY = height / 2f - (BOX_Y0 + BOX_Y1) / 2f * unit
        sway = swayDegrees
        roll = spec.roll / DEG

        val views = viewsFor(style, yawDegrees, tint)
        if (views.isEmpty()) return

        canvas.save()
        canvas.translate(originX, originY)
        canvas.scale(unit, unit)
        shadow.shader = RadialGradient(
            0f, GROUND, 1.2f, Color.argb(51, 20, 24, 32), Color.argb(0, 20, 24, 32), Shader.TileMode.CLAMP,
        )
        canvas.drawOval(RectF(-1.2f, GROUND - 0.2f, 1.2f, GROUND + 0.2f), shadow)
        canvas.restore()

        val head = layer(width, height, headLayer).also { headLayer = it }
        val hc = Canvas(head)
        hc.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        val blend = views.size > 1
        for (use in views) {
            hc.save()
            headTransform(hc)
            viewTransform(hc, use)
            val paint = if (blend) add else plain
            paint.alpha = (use.weight * 255f).toInt().coerceIn(0, 255)
            hc.drawBitmap(use.frame.bitmap, 0f, 0f, paint)
            hc.restore()
        }
        plain.alpha = 255

        val place = facePlace(style, views, yawDegrees)
        if (place != null && views.any { it.visor != null }) {
            val mask = layer(width, height, maskLayer).also { maskLayer = it }
            val mc = Canvas(mask)
            mc.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
            for (use in views) {
                val visor = use.visor ?: continue
                mc.save()
                headTransform(mc)
                viewTransform(mc, use)
                add.alpha = (use.weight * 255f).toInt().coerceIn(0, 255)
                mc.drawBitmap(visor.bitmap, 0f, 0f, add)
                mc.restore()
            }
            val faceBitmap = layer(width, height, faceLayer).also { faceLayer = it }
            val fc = Canvas(faceBitmap)
            fc.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
            fc.save()
            headTransform(fc)
            fc.translate(place.cx, place.cy)
            fc.scale(place.scale * place.squeeze, place.scale)
            fc.translate(-SEIRA_FACE_WIDTH / 2, -SEIRA_FACE_HEIGHT / 2)
            SeiraFacePainter(fc, ink, unit * place.scale).draw(spec, 1f - blink * 0.92f, talk.coerceIn(0f, 1f))
            fc.restore()
            fc.drawBitmap(mask, 0f, 0f, destIn)
            hc.drawBitmap(faceBitmap, 0f, 0f, plain)
        }
        canvas.drawBitmap(head, 0f, 0f, null)

        drawAccessories(canvas, look, views, place, yawDegrees)
    }

    private fun layer(width: Int, height: Int, current: Bitmap?): Bitmap =
        if (current != null && current.width == width && current.height == height) current
        else Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

    private fun bodyTransform(c: Canvas) {
        c.translate(originX, originY)
        c.scale(unit, unit)
        c.translate(0f, BODY_PIVOT_Y)
        c.rotate(sway)
        c.translate(0f, -BODY_PIVOT_Y)
    }

    private fun headTransform(c: Canvas) {
        bodyTransform(c)
        c.rotate(roll)
    }

    private fun viewTransform(c: Canvas, use: ViewUse) {
        if (use.mirror) c.scale(-1f, 1f)
        c.scale(1f / use.view.radius, 1f / use.view.radius)
        c.translate(-use.view.pivotX, -use.view.pivotY)
    }

    private fun viewsFor(style: SeiraStyleSpec, yaw: Float, tint: Int?): List<ViewUse> {
        val front = style.views["front"]
        val turned = style.views["yaw20"]
        val a = abs(yaw)
        val mirror = yaw < 0
        fun use(view: SeiraViewSpec?, weight: Float, mirrored: Boolean): ViewUse? {
            if (view == null || weight <= 0.001f) return null
            val frame = assets.frame(style, view, tint) ?: return null
            return ViewUse(view, frame, assets.visor(view), weight, mirrored)
        }
        val out = when {
            turned == null || a <= 0.001f -> listOfNotNull(use(front ?: turned, 1f, false))
            front == null || a >= NEAR_VIEW_YAW -> listOfNotNull(use(turned, 1f, mirror))
            else -> listOfNotNull(use(front, 1f - a / NEAR_VIEW_YAW, false), use(turned, a / NEAR_VIEW_YAW, mirror))
        }
        val total = out.sumOf { it.weight.toDouble() }.toFloat()
        return if (out.size < 2 || total <= 0f) out else out.map { ViewUse(it.view, it.frame, it.visor, it.weight / total, it.mirror) }
    }

    private fun facePlace(style: SeiraStyleSpec, views: List<ViewUse>, yaw: Float): FacePlace? {
        val frontView = style.views["front"] ?: style.views["yaw20"] ?: return null
        val frontBox = assets.visor(frontView)?.maskBox ?: return null
        val frontUse = ViewUse(frontView, views[0].frame, null, 1f, false)
        val fc = frontUse.toWorld(frontBox[0] + frontBox[2] / 2f, frontBox[1] + frontBox[3] / 2f)
        val frontWidth = frontBox[2] / frontView.radius

        var sx = 0f
        var sy = 0f
        var sw = 0f
        for (use in views) {
            val box = use.visor?.maskBox ?: continue
            val c = use.toWorld(box[0] + box[2] / 2f, box[1] + box[3] / 2f)
            sx += c.first * use.weight
            sy += c.second * use.weight
            sw += use.weight
        }
        val y0 = wrap(yaw)
        var cx = if (sw > 0f) sx / sw else fc.first + sin(y0 * DEG) * 0.88f
        val cy = if (sw > 0f) sy / sw else fc.second
        if (sw > 0f) {
            for (use in views) cx += use.weight * 0.88f * (sin(y0 * DEG) - sin(use.yaw * DEG))
        }
        if (sw <= 0f) return null
        return FacePlace(cx, cy, frontWidth / SEIRA_FACE_WIDTH, max(0.05f, cos(y0 * DEG)))
    }

    private fun wrap(deg: Float): Float = (((deg + 180f) % 360f) + 360f) % 360f - 180f

    private fun viewAnchor(views: List<ViewUse>, name: String): Pair<Float, Float>? {
        var x = 0f
        var y = 0f
        var w = 0f
        for (use in views) {
            val key = if (use.mirror) swapSide(name) else name
            val a = use.view.anchors[key] ?: continue
            val c = use.toWorld(a.first, a.second)
            x += c.first * use.weight
            y += c.second * use.weight
            w += use.weight
        }
        return if (w > 0f) (x / w) to (y / w) else null
    }

    private fun swapSide(name: String) = when {
        name.endsWith("L") -> name.dropLast(1) + "R"
        name.endsWith("R") -> name.dropLast(1) + "L"
        else -> name
    }

    private fun drawAccessories(canvas: Canvas, look: SeiraLook, views: List<ViewUse>, place: FacePlace?, yaw: Float) {
        for (id in look.accessories) {
            val spec = assets.accessory(id) ?: continue
            val image = assets.accessoryImage(spec) ?: continue
            var visible = 0f
            for (use in views) if (use.view.name in spec.views) visible += use.weight
            if (visible < 0.02f) continue
            val point = when (spec.anchor) {
                "eyes" -> place?.let { it.cx to it.cy + (SEIRA_EYE_CENTER_Y - SEIRA_FACE_HEIGHT / 2) * it.scale }
                "face" -> place?.let { it.cx to it.cy }
                else -> viewAnchor(views, spec.anchor)
            } ?: continue
            val squeeze = if (spec.anchor == "eyes" || spec.anchor == "mouth") max(0.2f, cos(wrap(yaw) * DEG)) else 1f
            val scale = spec.width / image.bitmap.width
            canvas.save()
            headTransform(canvas)
            canvas.translate(point.first, point.second)
            canvas.scale(scale * squeeze, scale)
            plain.alpha = (visible.coerceIn(0f, 1f) * 255f).toInt()
            canvas.drawBitmap(image.bitmap, -spec.pivotX, -spec.pivotY, plain)
            plain.alpha = 255
            canvas.restore()
        }
    }
}
