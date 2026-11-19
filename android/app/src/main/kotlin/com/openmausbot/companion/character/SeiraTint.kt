package com.openmausbot.companion.character

import com.openmausbot.companion.ui.MausPalette
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The bot's hair colour on Seira's rendered head, the same maths the desktop's
 * `CarvedAvatar` and `seira-hair.ts` do with SVG filters. Pixels are straight
 * (not premultiplied) ARGB ints, as `Bitmap.getPixels` returns them.
 */
internal object SeiraTint {
    private const val STEPS = 12
    private const val SHADE_MAX = 1.2f
    private const val SPECULAR_MAX = 0.3f

    /** The colour a bot name stands for; an unknown name reads as green, as on the desktop. */
    fun base(color: String?): Int =
        if (color != null && color in MausPalette.names) MausPalette.argb(color) else MausPalette.argb("green")

    /** Silver and chrome keep the hair as it was rendered. */
    fun isGrey(color: String?): Boolean = color == "silver" || color == "chrome"

    /** The tint laid over the hair, or null for the grey colours. */
    fun hairTint(color: String?): Int? = if (isGrey(color)) null else vivid(base(color))

    /** The flat style wants more punch than the plain colour: saturation lifted 15%. */
    fun vivid(argb: Int): Int {
        val r = ((argb shr 16) and 0xFF) / 255f
        val g = ((argb shr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        val high = max(r, max(g, b))
        val low = min(r, min(g, b))
        val l = (high + low) / 2f
        val d = high - low
        if (d == 0f) return argb or (0xFF shl 24)
        val s = min(1f, (if (l > 0.5f) d / (2f - high - low) else d / (high + low)) * 1.15f)
        val h = when (high) {
            r -> (g - b) / d + if (g < b) 6f else 0f
            g -> (b - r) / d + 2f
            else -> (r - g) / d + 4f
        } / 6f
        val q = if (l < 0.5f) l * (1f + s) else l + s - l * s
        val p = 2f * l - q
        fun channel(x: Float): Int {
            val t = (x + 1f) % 1f
            val v = when {
                t < 1f / 6f -> p + (q - p) * 6f * t
                t < 1f / 2f -> q
                t < 2f / 3f -> p + (q - p) * (2f / 3f - t) * 6f
                else -> p
            }
            return (v * 255f).roundToInt().coerceIn(0, 255)
        }
        return (0xFF shl 24) or (channel(h + 1f / 3f) shl 16) or (channel(h) shl 8) or channel(h - 1f / 3f)
    }

    /**
     * Seira's own hair: the head multiplied by the tint where the mask is white.
     * The mask counts by luminance times alpha, as an SVG mask does.
     */
    fun multiplyHair(head: IntArray, mask: IntArray, tint: Int): IntArray {
        require(head.size == mask.size) { "The hair mask must match the head." }
        val tr = ((tint shr 16) and 0xFF) / 255f
        val tg = ((tint shr 8) and 0xFF) / 255f
        val tb = (tint and 0xFF) / 255f
        val out = IntArray(head.size)
        for (i in head.indices) {
            val m = mask[i]
            val coverage = luminance(m) * (((m ushr 24) and 0xFF) / 255f)
            val h = head[i]
            val ab = ((h ushr 24) and 0xFF) / 255f
            if (coverage <= 0f || ab <= 0f) {
                out[i] = h
                continue
            }
            val br = ((h shr 16) and 0xFF) / 255f
            val bg = ((h shr 8) and 0xFF) / 255f
            val bb = (h and 0xFF) / 255f
            out[i] = composite(
                coverage, tr, tg, tb,
                ab, br, bg, bb,
                blendR = br * tr, blendG = bg * tg, blendB = bb * tb,
            )
        }
        return out
    }

    /** The red, green and blue tables that turn grey hair into hair of [tint]. */
    fun gradientTables(tint: Int): Array<FloatArray> {
        val channels = floatArrayOf(
            ((tint shr 16) and 0xFF) / 255f,
            ((tint shr 8) and 0xFF) / 255f,
            (tint and 0xFF) / 255f,
        )
        return Array(3) { c ->
            FloatArray(STEPS + 1) { i ->
                val shade = i.toFloat() / STEPS * SHADE_MAX
                val specular = SPECULAR_MAX * ((shade - 1f) / (SHADE_MAX - 1f)).coerceIn(0f, 1f)
                (((min(1f, channels[c] * shade) * (1f - specular) + specular) * 1000f).roundToInt()) / 1000f
            }
        }
    }

    /** Other styles: the grey hair picture through the gradient tables, then over the head. */
    fun gradientHair(head: IntArray, hair: IntArray, tables: Array<FloatArray>): IntArray {
        require(head.size == hair.size) { "The hair picture must match the head." }
        val out = IntArray(head.size)
        for (i in head.indices) {
            val s = hair[i]
            val a = (s ushr 24) and 0xFF
            val h = head[i]
            if (a == 0) {
                out[i] = h
                continue
            }
            val sr = lookup(tables[0], ((s shr 16) and 0xFF) / 255f)
            val sg = lookup(tables[1], ((s shr 8) and 0xFF) / 255f)
            val sb = lookup(tables[2], (s and 0xFF) / 255f)
            val ab = ((h ushr 24) and 0xFF) / 255f
            val br = ((h shr 16) and 0xFF) / 255f
            val bg = ((h shr 8) and 0xFF) / 255f
            val bb = (h and 0xFF) / 255f
            out[i] = composite(
                a / 255f, sr, sg, sb,
                ab, br, bg, bb,
                blendR = sr, blendG = sg, blendB = sb,
            )
        }
        return out
    }

    /** An SVG `feFuncX type="table"`: linear between the table's evenly spaced values. */
    private fun lookup(table: FloatArray, c: Float): Float {
        val spans = table.size - 1
        val k = min((c * spans).toInt(), spans - 1)
        return table[k] + (c * spans - k) * (table[k + 1] - table[k])
    }

    private fun luminance(argb: Int): Float {
        val r = ((argb shr 16) and 0xFF) / 255f
        val g = ((argb shr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        return 0.2125f * r + 0.7154f * g + 0.0721f * b
    }

    /** W3C compositing of a source (alpha [a], colour [sr] [sg] [sb]) over a backdrop, with a blend result. */
    private fun composite(
        a: Float, sr: Float, sg: Float, sb: Float,
        ab: Float, br: Float, bg: Float, bb: Float,
        blendR: Float, blendG: Float, blendB: Float,
    ): Int {
        val ao = a + ab - a * ab
        if (ao <= 0f) return 0
        fun channel(s: Float, b: Float, blended: Float): Int {
            val premultiplied = a * (1f - ab) * s + a * ab * blended + (1f - a) * ab * b
            return ((premultiplied / ao) * 255f).roundToInt().coerceIn(0, 255)
        }
        return ((ao * 255f).roundToInt().coerceIn(0, 255) shl 24) or
            (channel(sr, br, blendR) shl 16) or
            (channel(sg, bg, blendG) shl 8) or
            channel(sb, bb, blendB)
    }
}
