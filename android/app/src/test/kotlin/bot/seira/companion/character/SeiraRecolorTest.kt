package bot.seira.companion.character

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SeiraRecolorTest {
    @Test
    fun `a grey colour keeps the silver hair of the seira style`() {
        assertNull(SeiraRecolor.tintFor("seira", "silver"))
        assertNull(SeiraRecolor.tintFor("seira", "chrome"))
        assertNull(SeiraRecolor.tintFor(null, "chrome"))
    }

    @Test
    fun `a grey colour gives another style the seira silver`() {
        assertEquals(0xDADADF, SeiraRecolor.tintFor("bob", "silver"))
        assertEquals(0xDADADF, SeiraRecolor.tintFor("spiky", "chrome"))
    }

    @Test
    fun `an app colour gives its hex`() {
        assertEquals(0xD94B52, SeiraRecolor.tintFor("bob", "red"))
        assertEquals(0x377FE6, SeiraRecolor.tintFor("seira", "blue"))
        assertEquals(0xE5634E, SeiraRecolor.tintFor("twin-tails", "coral"))
    }

    @Test
    fun `recolour keeps alpha and moves magenta hair to the tint`() {
        val pixel = 0xFF shl 24 or (0xFF shl 16) or 0xFF
        val w = 8
        val pixels = IntArray(w * w) { pixel }
        val mask = IntArray(w * w) { 0xFF000000.toInt() or 0xFFFFFF }
        pixels[3] = 0x80 shl 24 or (0xFF shl 16) or 0xFF
        pixels[10] = 0

        SeiraRecolor.recolor(pixels, mask, 0x377FE6)

        assertEquals(0x80, pixels[3] ushr 24)
        assertEquals(0, pixels[10] ushr 24)
        val out = pixels[0]
        assertEquals(0xFF, out ushr 24)
        val r = (out shr 16) and 255
        val g = (out shr 8) and 255
        val b = out and 255
        assertTrue(b > r && b > g, "blue should lead after the recolour: $r $g $b")
        assertEquals(0x37, r, 2)
    }

    @Test
    fun `pixels outside the hair mask stay as they were`() {
        val pixel = 0xFF shl 24 or (0xFF shl 16) or 0xFF
        val pixels = IntArray(64) { pixel }
        val mask = IntArray(64)
        SeiraRecolor.recolor(pixels, mask, 0x009957)
        assertTrue(pixels.all { it == pixel })
    }

    private fun assertEquals(expected: Int, actual: Int, tolerance: Int) {
        assertTrue(kotlin.math.abs(expected - actual) <= tolerance, "expected $expected within $tolerance of $actual")
    }
}
