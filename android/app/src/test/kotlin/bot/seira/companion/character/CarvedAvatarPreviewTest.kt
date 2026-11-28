package bot.seira.companion.character

import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CarvedAvatarPreviewTest {
    private class Case(val hair: String?, val color: String, val accessories: List<String>)

    private val cases = listOf(
        Case("seira", "red", emptyList()),
        Case("bob", "blue", listOf("glasses", "earring")),
        Case("spiky", "silver", listOf("headphones")),
        Case("twin-tails", "coral", listOf("ribbon", "scarf")),
        Case("buzz", "orange", listOf("cap", "bowtie", "sunglasses")),
        Case("cursor", "green", emptyList()),
    )

    @Test
    fun rendersEachLookAndWritesAPreview() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val out = File("build/outputs/character-preview").apply { mkdirs() }
        cases.forEachIndexed { index, case ->
            val bitmap = CarvedAvatarSnapshot.render(context, case.hair, case.color, case.accessories, 256)
            File(out, "carved-${index + 1}.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            assertEquals(256, bitmap.width)
            assertEquals(256, bitmap.height)
            assertEquals(Bitmap.Config.ARGB_8888, bitmap.config)
            assertTrue(coveredPixels(bitmap) > 256 * 256 / 10, "look ${index + 1} is blank")
            if (case.accessories.none { it == "glasses" || it == "sunglasses" }) assertEyeIsInk(bitmap, case)
        }
    }

    private fun coveredPixels(bitmap: Bitmap): Int {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return pixels.count { (it ushr 24) > 0 }
    }

    private fun assertEyeIsInk(bitmap: Bitmap, case: Case) {
        val look = CarvedLook.of(case.hair, case.accessories)
        val eyes = look.body.eyes
        val scale = bitmap.width / look.viewBox[2]
        val x = ((eyes.cx - eyes.dx - look.viewBox[0]) * scale).roundToInt()
        val y = ((eyes.cy - look.viewBox[1]) * scale).roundToInt()
        val pixel = bitmap.getPixel(x, y)
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        assertTrue(r > 180 && g < 80 && b < 110, "eye pixel of ${case.hair} is #%06x".format(pixel and 0xFFFFFF))
    }
}
