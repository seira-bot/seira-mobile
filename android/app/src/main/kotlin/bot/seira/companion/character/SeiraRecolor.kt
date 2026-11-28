package bot.seira.companion.character

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private val APP_COLORS = mapOf(
    "green" to 0x009957,
    "blue" to 0x377FE6,
    "red" to 0xD94B52,
    "orange" to 0xE78531,
    "purple" to 0x8057C8,
    "cyan" to 0x0EA5C6,
    "pink" to 0xD84F8B,
    "yellow" to 0xD8A729,
    "teal" to 0x01A492,
    "coral" to 0xE5634E,
    "silver" to 0xA8ADB4,
    "chrome" to 0xB3BAC4,
)

private val GREY_COLORS = setOf("silver", "chrome")
private const val SEIRA_SILVER = 0xDADADF

internal object SeiraRecolor {
    /**
     * The hair tint for a hair style and an app colour, as 0xRRGGBB, or null to keep the hair as drawn.
     * Grey colours keep the silver of the Seira style. Every other style would stay magenta, so it gets silver.
     */
    fun tintFor(hair: String?, color: String): Int? {
        val colour = if (color in GREY_COLORS) null else APP_COLORS[color]
        return colour ?: if ((hair ?: SEIRA_DEFAULT_HAIR) != SEIRA_DEFAULT_HAIR) SEIRA_SILVER else null
    }

    /**
     * Recolours the hair of an unpremultiplied ARGB frame in place.
     * [mask] holds the hair mask in its red channel. The median shade of the source maps to [tint] (0xRRGGBB).
     */
    fun recolor(pixels: IntArray, mask: IntArray?, tint: Int) {
        val shades = ArrayList<Float>()
        var p = 0
        while (p < pixels.size) {
            val px = pixels[p]
            if ((px ushr 24) >= 200) {
                val mk = if (mask != null) ((mask[p] shr 16) and 255) / 255f else 0f
                if (mk > 0.9f) {
                    val r = (px shr 16) and 255
                    val g = (px shr 8) and 255
                    val b = px and 255
                    val magenta = r - g > 60 && b - g > 60
                    shades.add(if (magenta) max(r, b).toFloat() else 0.3f * r + 0.59f * g + 0.11f * b)
                }
            }
            p += 16
        }
        shades.sort()
        val ref = (if (shades.isEmpty()) 1f else shades[shades.size / 2]).let { if (it == 0f) 1f else it }

        val tr = (tint shr 16) and 255
        val tg = (tint shr 8) and 255
        val tb = tint and 255
        for (i in pixels.indices) {
            val px = pixels[i]
            val a = px ushr 24
            if (a == 0) continue
            val mk = if (mask != null) ((mask[i] shr 16) and 255) / 255f else 0f
            if (mk <= 0f) continue
            val r = (px shr 16) and 255
            val g = (px shr 8) and 255
            val b = px and 255
            val magenta = r - g > 40 && b - g > 40
            val shade: Float
            val spec: Float
            if (magenta) {
                shade = max(r, b) / ref
                spec = min(1f, g / max(1f, max(r, b).toFloat())) * 0.9f
            } else {
                val l = 0.3f * r + 0.59f * g + 0.11f * b
                shade = l / ref
                spec = max(0f, (l - ref) / max(1f, 255f - ref)) * 0.55f
            }
            val cr = min(255f, tr * shade)
            val cg = min(255f, tg * shade)
            val cb = min(255f, tb * shade)
            val hr = cr + (255f - cr) * spec
            val hg = cg + (255f - cg) * spec
            val hb = cb + (255f - cb) * spec
            val nr = r + (hr - r) * mk
            val ng = g + (hg - g) * mk
            val nb = b + (hb - b) * mk
            pixels[i] = (a shl 24) or (channel(nr) shl 16) or (channel(ng) shl 8) or channel(nb)
        }
    }

    private fun channel(v: Float): Int = v.roundToInt().coerceIn(0, 255)
}
