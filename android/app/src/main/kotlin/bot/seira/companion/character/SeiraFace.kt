package bot.seira.companion.character

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import bot.seira.companion.ui.CharacterMood
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

internal const val SEIRA_FACE_WIDTH = 1024f
internal const val SEIRA_FACE_HEIGHT = 640f

private const val U = 6f
private val F = intArrayOf(0, 1, 1, 2, 3, 5, 8, 13, 21, 34, 55, 89)
private fun fu(i: Int) = F[i] * U

private val EYE_W = fu(8)
private val EYE_H = fu(9)
private val GAP = 2 * fu(7)
private val EYE_TOP = SEIRA_FACE_HEIGHT * 0.45f - EYE_H / 2
private val EYE_CY = EYE_TOP + EYE_H / 2
private val EYE_CX = floatArrayOf(SEIRA_FACE_WIDTH / 2 - GAP / 2 - EYE_W / 2, SEIRA_FACE_WIDTH / 2 + GAP / 2 + EYE_W / 2)
private val MOUTH_Y = EYE_TOP + EYE_H + fu(6)

internal const val SEIRA_DARK_SCREEN = 0xFF0C0F14.toInt()
internal const val SEIRA_BRAND_PINK = 0xFFFF2A57.toInt()
internal const val SEIRA_LIGHT_FEATURE = 0xFFF2F2F3.toInt()
internal const val SEIRA_LIGHT_SCREEN = 0xFFDCE0E6.toInt()

/** The two colours of the screen face. [lightScreen] turns the glow off and softens the glass shading. */
internal data class SeiraFaceInk(val screen: Int, val feature: Int, val lightScreen: Boolean)

/** null is the brand face, "white" has light features on the dark screen, "black" has dark features on a light screen. */
internal fun seiraFaceInk(faceInk: String?): SeiraFaceInk = when (faceInk) {
    "white" -> SeiraFaceInk(SEIRA_DARK_SCREEN, SEIRA_LIGHT_FEATURE, false)
    "black" -> SeiraFaceInk(SEIRA_LIGHT_SCREEN, SEIRA_DARK_SCREEN, true)
    else -> SeiraFaceInk(SEIRA_DARK_SCREEN, SEIRA_BRAND_PINK, false)
}

/** One expression of the desktop face catalog. Lids are the part of the eye height cut from the top and bottom. */
internal class SeiraFaceSpec(
    val eyes: String = "pill",
    val eyesRight: String? = null,
    val mouth: String = "smile",
    val lidIn: Float = 0f,
    val lidOut: Float = 0f,
    val scaleLeft: Float = 1f,
    val scaleRight: Float = 1f,
    val lookX: Float = 0f,
    val lookY: Float = 0f,
    val brow: Float = 0f,
    val blush: Float = 0f,
    val tremble: Boolean = false,
    val roll: Float = 0f,
)

internal val SEIRA_EXPRESSIONS: Map<String, SeiraFaceSpec> = mapOf(
    "calm" to SeiraFaceSpec(),
    "happy" to SeiraFaceSpec(eyes = "arc-up", mouth = "open", roll = 0.05f),
    "laugh" to SeiraFaceSpec(eyes = "chevron", mouth = "open-big"),
    "shy" to SeiraFaceSpec(lookX = 0.6f, lookY = 0.65f, scaleLeft = 0.9f, scaleRight = 0.9f, mouth = "wavy-small", blush = 1f, roll = -0.1f),
    "proud" to SeiraFaceSpec(eyes = "arc-up", mouth = "smile", roll = 0.04f),
    "surprised" to SeiraFaceSpec(eyes = "round-big", mouth = "small-o"),
    "confused" to SeiraFaceSpec(scaleLeft = 0.68f, scaleRight = 1.05f, lookX = 0.2f, lookY = -0.2f, mouth = "wavy", brow = -0.5f, roll = 0.17f),
    "thinking" to SeiraFaceSpec(lookX = 0.75f, lookY = -0.85f, mouth = "flat-side", roll = 0.09f),
    "sad" to SeiraFaceSpec(lidOut = 0.34f, lookY = 0.35f, brow = -1f, mouth = "frown"),
    "angry" to SeiraFaceSpec(lidIn = 0.44f, lidOut = 0.06f, brow = 1f, mouth = "frown"),
    "sleepy" to SeiraFaceSpec(lidIn = 0.56f, lidOut = 0.56f, lookY = 0.3f, mouth = "tiny-o", roll = 0.12f),
    "asleep" to SeiraFaceSpec(eyes = "arc-down", mouth = "tiny-o", roll = 0.1f),
    "wink" to SeiraFaceSpec(eyesRight = "arc-up", mouth = "smile", roll = -0.07f),
    "bored" to SeiraFaceSpec(lidIn = 0.5f, lidOut = 0.5f, lookX = 0.55f, lookY = 0.1f, mouth = "flat", roll = -0.1f),
    "determined" to SeiraFaceSpec(lidIn = 0.3f, lidOut = 0.12f, brow = 0.7f, mouth = "firm"),
    "smug" to SeiraFaceSpec(lidIn = 0.44f, lidOut = 0.44f, lookX = 0.35f, mouth = "smirk", roll = 0.09f),
    "scared" to SeiraFaceSpec(eyes = "small", mouth = "wavy-open", tremble = true),
)

/** The face name for a mood. Suspicious has no face of its own, so it takes the smug one. */
internal fun seiraExpressionFor(mood: CharacterMood): String = when (mood) {
    CharacterMood.HAPPY, CharacterMood.EXCITED -> "happy"
    CharacterMood.PROUD -> "proud"
    CharacterMood.CURIOUS, CharacterMood.THINKING -> "thinking"
    CharacterMood.SHY -> "shy"
    CharacterMood.DROWSY -> "sleepy"
    CharacterMood.BORED -> "bored"
    CharacterMood.PLAYFUL -> "wink"
    CharacterMood.SURPRISED -> "surprised"
    CharacterMood.SAD -> "sad"
    CharacterMood.LAUGHING -> "laugh"
    CharacterMood.CONFUSED -> "confused"
    CharacterMood.ANGRY -> "angry"
    CharacterMood.SCARED -> "scared"
    CharacterMood.SLEEPING -> "asleep"
    CharacterMood.WORKING -> "determined"
    CharacterMood.SUSPICIOUS -> "smug"
    else -> "calm"
}

private val LINE_MOUTHS = setOf("smile", "flat", "smirk", "firm", "flat-side", "wavy", "wavy-small", "frown", "tiny-o", "small-o")

private fun mixColor(a: Int, b: Int, k: Float): Int {
    fun ch(shift: Int) = (((a shr shift) and 255) * (1 - k) + ((b shr shift) and 255) * k).toInt().coerceIn(0, 255)
    return Color.rgb(ch(16), ch(8), ch(0))
}

private fun withAlpha(color: Int, alpha: Float) = (color and 0xFFFFFF) or ((alpha * 255).toInt().coerceIn(0, 255) shl 24)

private fun Path.arc(x: Float, y: Float, r: Float, from: Double, to: Double) {
    arcTo(RectF(x - r, y - r, x + r, y + r), Math.toDegrees(from).toFloat(), Math.toDegrees(to - from).toFloat(), true)
}

private fun Path.dShape(x: Float, y: Float, hw: Float, top: Float, bottom: Float) {
    moveTo(x - hw, y + top)
    lineTo(x + hw, y + top)
    cubicTo(x + hw, y + top + (bottom - top) * 0.75f, x + hw * 0.55f, y + bottom, x, y + bottom)
    cubicTo(x - hw * 0.55f, y + bottom, x - hw, y + top + (bottom - top) * 0.75f, x - hw, y + top)
    close()
}

/**
 * Draws the screen face on a canvas of [SEIRA_FACE_WIDTH] by [SEIRA_FACE_HEIGHT] units. The caller clips it to the visor.
 * [pixelsPerUnit] is the size of one face unit on the target, which the glow radius needs.
 */
internal class SeiraFacePainter(private val canvas: Canvas, private val ink: SeiraFaceInk, private val pixelsPerUnit: Float, private val lashes: Boolean = true) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ink.feature
    }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = ink.feature
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val plain = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    init {
        if (!ink.lightScreen) {
            val blur = fu(5) * pixelsPerUnit
            val radius = max(0.1f, (blur / 2f - 0.5f) / 0.57735f)
            val glow = withAlpha(ink.feature, 0.85f)
            fill.setShadowLayer(radius, 0f, 0f, glow)
            stroke.setShadowLayer(radius, 0f, 0f, glow)
        }
    }

    private fun fillPath(path: Path) = canvas.drawPath(path, fill)

    private fun strokePath(path: Path, width: Float) {
        stroke.strokeWidth = width
        canvas.drawPath(path, stroke)
    }

    private fun drawScreen() {
        val w = SEIRA_FACE_WIDTH
        val h = SEIRA_FACE_HEIGHT
        val shade = if (ink.lightScreen) 0.12f else 0.55f
        val rim = if (ink.lightScreen) 0.1f else 0.5f
        plain.shader = LinearGradient(
            0f, 0f, 0f, h,
            mixColor(ink.screen, Color.WHITE, 0.04f), mixColor(ink.screen, Color.BLACK, shade), Shader.TileMode.CLAMP,
        )
        canvas.drawRect(-w, -h, w * 2, h * 2, plain)
        val radius = w * 0.62f
        plain.shader = RadialGradient(
            w / 2, h * 0.475f, radius,
            intArrayOf(Color.argb(0, 0, 0, 0), Color.argb(0, 0, 0, 0), Color.argb((rim * 255).toInt(), 0, 0, 0)),
            floatArrayOf(0f, h * 0.32f / radius, 1f), Shader.TileMode.CLAMP,
        )
        canvas.drawRect(-w, -h, w * 2, h * 2, plain)
        plain.shader = null
    }

    private fun highlight(x: Float, top: Float, w: Float, out: Float, r: Float = fu(6) / 2) {
        plain.color = Color.WHITE
        canvas.drawCircle(x - out * max(0f, w / 2 - fu(5) - r), top + fu(5) + r, r, plain)
    }

    private fun lashWing(ex: Float, ey: Float, out: Float) {
        if (!lashes) return
        val p = Path()
        p.moveTo(ex - out * 5 * U, ey + 3 * U)
        p.quadTo(ex + out * 2 * U, ey - 0.5f * U, ex + out * 8 * U, ey - 1 * U)
        p.quadTo(ex + out * 3 * U, ey + 5 * U, ex, ey + fu(7))
        p.close()
        fillPath(p)
    }

    private fun lashFlick(ex: Float, ey: Float, out: Float, up: Float) {
        if (!lashes) return
        val p = Path()
        p.moveTo(ex, ey)
        p.quadTo(ex + out * 3 * U, ey - up * U, ex + out * 5 * U, ey - up * 4 * U)
        strokePath(p, 2 * U)
    }

    private class EyeTop(val top: Float)

    private fun pillEye(i: Int, x: Float, cy: Float, s: Float, open: Float, lidIn: Float, lidOut: Float): EyeTop {
        val out = if (i == 0) -1f else 1f
        val w = EYE_W * s
        val h = max(fu(4), EYE_H * s * open)
        val closedish = open < 0.35f
        val top = cy - h / 2
        val yi = top + if (closedish) 0f else lidIn * h
        val yo = top + if (closedish) 0f else lidOut * h
        val xi = x - out * w / 2
        val xo = x + out * w / 2
        val cut = !closedish && (lidIn > 0 || lidOut > 0)
        canvas.save()
        if (cut) {
            val bot = cy + h / 2
            fun yAt(px: Float) = yi + (yo - yi) * (px - xi) / (xo - xi)
            val clip = Path()
            clip.moveTo(x - w, yAt(x - w))
            clip.lineTo(x + w, yAt(x + w))
            clip.lineTo(x + w, bot)
            clip.lineTo(x - w, bot)
            clip.close()
            canvas.clipPath(clip)
        }
        val body = Path()
        body.addRoundRect(RectF(x - w / 2, cy - h / 2, x + w / 2, cy + h / 2), min(w, h) / 2, min(w, h) / 2, Path.Direction.CW)
        fillPath(body)
        if (!closedish) {
            val visTop = min(yi, yo) + kotlin.math.abs(yi - yo) * 0.5f
            highlight(x, max(top, visTop - fu(3)), w, out)
        }
        canvas.restore()
        if (closedish) lashFlick(xo, cy, out, 0.6f) else lashWing(xo, yo, out)
        return EyeTop(if (cut) min(yi, yo) else top)
    }

    private fun modeEye(i: Int, mode: String, x: Float, cy: Float, s: Float, open: Float): EyeTop {
        val out = if (i == 0) -1f else 1f
        val w = EYE_W * s
        val h = EYE_H * s
        val p = Path()
        when (mode) {
            "arc-up" -> {
                val r = w * 0.56f
                val yc = cy + h * 0.12f
                p.arc(x, yc, r, PI * 1.12, PI * 1.88)
                strokePath(p, 3 * U)
                lashFlick(x + out * r * cos(PI * 0.12).toFloat(), yc - r * sin(PI * 0.12).toFloat(), out, 1f)
            }
            "arc-down" -> {
                val r = w * 0.56f
                val yc = cy - h * 0.08f
                p.arc(x, yc, r, PI * 0.12, PI * 0.88)
                strokePath(p, 3 * U)
                lashFlick(x + out * r * cos(PI * 0.12).toFloat(), yc + r * sin(PI * 0.12).toFloat(), out, 0.8f)
            }
            "chevron" -> {
                val a = w * 0.42f
                val b = h * 0.26f
                p.moveTo(x + out * a, cy - b)
                p.lineTo(x - out * a * 0.7f, cy)
                p.lineTo(x + out * a, cy + b)
                strokePath(p, 3 * U)
            }
            "round-big" -> {
                val r = w * 0.66f
                canvas.drawCircle(x, cy, r, fill)
                highlight(x + -out * r * 0.22f + out * max(0f, r - fu(5) - fu(6) / 2), cy - r + r * 0.12f, r * 2, out)
                lashWing(x + out * r * 0.72f, cy - r * 0.7f, out)
            }
            "small" -> {
                val sw = fu(6)
                val sh = fu(7)
                p.addRoundRect(RectF(x - sw / 2, cy - sh / 2, x + sw / 2, cy + sh / 2), sw / 2, sw / 2, Path.Direction.CW)
                fillPath(p)
                highlight(x, cy - sh / 2 - fu(4), sw, out, fu(5) / 2 - 2)
            }
            else -> return pillEye(i, x, cy, s, open, 0f, 0f)
        }
        return EyeTop(cy - h / 2)
    }

    private fun mouth(spec: SeiraFaceSpec, x: Float, y: Float, talk: Float) {
        val id = spec.mouth
        val p = Path()
        if (talk > 0.06f && id in LINE_MOUTHS) {
            p.dShape(x, y, (3.5f + 2.5f * talk) * U, -1.5f * U, (1 + 7 * talk) * U)
            fillPath(p)
            return
        }
        when (id) {
            "flat" -> { p.moveTo(x - 5 * U, y); p.lineTo(x + 5 * U, y); strokePath(p, 2 * U) }
            "firm" -> { p.moveTo(x - 4 * U, y); p.lineTo(x + 4 * U, y); strokePath(p, 2.5f * U) }
            "flat-side" -> { p.moveTo(x + 0.5f * U, y + 0.6f * U); p.lineTo(x + 7 * U, y - 0.6f * U); strokePath(p, 2 * U) }
            "small-o" -> { p.addCircle(x, y + 0.5f * U, 2.6f * U, Path.Direction.CW); strokePath(p, 2 * U) }
            "tiny-o" -> { p.addCircle(x, y + 1 * U, 1.5f * U, Path.Direction.CW); fillPath(p) }
            "smirk" -> {
                p.moveTo(x - 5 * U, y + 1.5f * U)
                p.quadTo(x + 1 * U, y + 3.5f * U, x + 5.5f * U, y - 2 * U)
                strokePath(p, 2 * U)
            }
            "open" -> { p.dShape(x, y, 5 * U, -2 * U, 5 * U); fillPath(p) }
            "open-big" -> { p.dShape(x, y, 8 * U, -3 * U, 9 * U); fillPath(p) }
            "frown" -> { p.arc(x, y + 6 * U, 6 * U, PI * 1.24, PI * 1.76); strokePath(p, 2 * U) }
            "wavy" -> { wave(p, x - 6 * U, x + 6 * U, y, 1.3f * U, 2f); strokePath(p, 2 * U) }
            "wavy-small" -> { wave(p, x - 4 * U, x + 4 * U, y, 1f * U, 1.5f); strokePath(p, 2 * U) }
            "wavy-open" -> {
                val hw = 5.5f * U
                val hh = 4 * U
                for (k in 0..40) {
                    val u = k / 40f
                    val px = x - hw + u * 2 * hw
                    val py = y - hh * 0.4f + sin(u * PI * 4).toFloat() * 0.8f * U
                    if (k == 0) p.moveTo(px, py) else p.lineTo(px, py)
                }
                p.cubicTo(x + hw, y + hh, x + hw * 0.4f, y + hh * 1.2f, x, y + hh * 1.2f)
                p.cubicTo(x - hw * 0.4f, y + hh * 1.2f, x - hw, y + hh, x - hw, y - hh * 0.4f)
                p.close()
                strokePath(p, 2 * U)
            }
            else -> { p.arc(x, y - 4 * U, 7 * U, PI * 0.2, PI * 0.8); strokePath(p, 2 * U) }
        }
    }

    private fun wave(p: Path, x0: Float, x1: Float, y: Float, amp: Float, periods: Float) {
        for (k in 0..48) {
            val u = k / 48f
            val px = x0 + (x1 - x0) * u
            val py = y + sin(u * periods * PI * 2).toFloat() * amp
            if (k == 0) p.moveTo(px, py) else p.lineTo(px, py)
        }
    }

    private fun brow(i: Int, x: Float, top: Float, w: Float, tilt: Float) {
        val out = if (i == 0) -1f else 1f
        val yb = top - 5 * U
        val p = Path()
        p.moveTo(x - out * w * 0.6f, yb + tilt * 3.5f * U)
        p.quadTo(x, yb - 1.5f * U, x + out * w * 0.6f, yb - tilt * 2.5f * U)
        strokePath(p, 1.5f * U)
    }

    private fun blush(x: Float, y: Float, k: Float) {
        val a = min(1f, k)
        plain.shader = RadialGradient(x, y, 8 * U, withAlpha(0xFF6E96, 0.32f * a), withAlpha(0xFF6E96, 0f), Shader.TileMode.CLAMP)
        canvas.drawRect(x - 9 * U, y - 6 * U, x + 9 * U, y + 6 * U, plain)
        plain.shader = null
        for (n in -1..1) {
            val bx = x + n * 2.6f * U
            val p = Path()
            p.moveTo(bx - 1 * U, y + 1.6f * U)
            p.lineTo(bx + 1 * U, y - 1.6f * U)
            stroke.color = withAlpha(0xFF7FA3, a)
            strokePath(p, 1.2f * U)
        }
        stroke.color = ink.feature
    }

    private fun trembleMarks(out: Float, x: Float, cy: Float) {
        for (k in 0..1) {
            val p = Path()
            val rr = (2 + 1.6f * k) * U
            val cx = x + out * (EYE_W * 0.5f + (2.5f + 2 * k) * U)
            val from = if (out > 0) -0.6 else PI - 0.6
            val to = if (out > 0) 0.6 else PI + 0.6
            p.arc(cx, cy, rr, from, to)
            strokePath(p, 1.2f * U)
        }
    }

    /** [open] is 1 with the eyes open and near 0 with the eyes shut. [talk] is 0 to 1. */
    fun draw(spec: SeiraFaceSpec, open: Float, talk: Float) {
        drawScreen()
        val ex = spec.lookX * 48f
        val ey = spec.lookY * 36f
        for (i in 0..1) {
            val out = if (i == 0) -1f else 1f
            val s = if (i == 0) spec.scaleLeft else spec.scaleRight
            val x = EYE_CX[i] + ex + out * (s - 1) * EYE_W * 0.15f
            val cy = EYE_CY + ey
            val mode = if (i == 1 && spec.eyesRight != null) spec.eyesRight else spec.eyes
            val top = if (mode == "pill") pillEye(i, x, cy, s, open, spec.lidIn, spec.lidOut) else modeEye(i, mode, x, cy, s, open)
            if (spec.brow != 0f) brow(i, x, top.top, EYE_W * s, spec.brow)
            if (spec.blush > 0.02f) blush(EYE_CX[i] + out * EYE_W * 0.25f + ex * 0.5f, EYE_CY + EYE_H * 0.56f + ey * 0.5f, spec.blush)
            if (spec.tremble) trembleMarks(out, x, cy)
        }
        mouth(spec, SEIRA_FACE_WIDTH / 2 + ex * 0.6f, MOUTH_Y + ey * 0.4f, talk)
        gloss()
    }

    private fun gloss() {
        val w = SEIRA_FACE_WIDTH
        val h = SEIRA_FACE_HEIGHT
        plain.shader = LinearGradient(0f, h * 0.02f, 0f, h * 0.3f, Color.argb(26, 255, 255, 255), Color.argb(0, 255, 255, 255), Shader.TileMode.CLAMP)
        canvas.drawOval(RectF(w * 0.5f - w * 0.42f, h * 0.06f - h * 0.24f, w * 0.5f + w * 0.42f, h * 0.06f + h * 0.24f), plain)
        plain.shader = null
    }
}

internal val SEIRA_EYE_CENTER_Y: Float get() = EYE_CY
