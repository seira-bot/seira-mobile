package com.openmausbot.companion.character

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import com.openmausbot.companion.ui.CharacterMood
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SeiraCharacterPreviewTest {
    private val context get() = RuntimeEnvironment.getApplication() as Application

    private class Case(
        val hair: String?,
        val color: String,
        val accessories: List<String>,
        val faceInk: String?,
        val mood: CharacterMood = CharacterMood.IDLE,
        val talk: Float = 0f,
    )

    private val cases = listOf(
        Case("seira", "red", emptyList(), null),
        Case("bob", "blue", listOf("glasses", "earring"), "white"),
        Case("spiky", "silver", listOf("headphones"), "black"),
        Case("twin-tails", "coral", listOf("ribbon", "scarf"), null),
        Case("buzz", "orange", listOf("cap", "bowtie", "sunglasses"), "white", CharacterMood.HAPPY),
        Case("long-wavy", "purple", emptyList(), null, CharacterMood.SLEEPING),
        Case("ponytail", "teal", listOf("tiara"), null, CharacterMood.IDLE, talk = 1f),
    )

    @Test
    fun `preview frames are drawn and saved`() {
        val dir = File("build/outputs/character-preview").also { it.mkdirs() }
        cases.forEachIndexed { index, case ->
            val bitmap = SeiraCharacterSnapshot.render(
                context, case.hair, case.color, case.accessories, case.faceInk, 512, case.mood, case.talk,
            )
            assertEquals(512, bitmap.width)
            assertTrue(opaquePixels(bitmap) > 2000, "frame $index is blank")
            for ((x, y) in listOf(0 to 0, 511 to 0, 0 to 511, 511 to 511)) {
                assertEquals(0, Color.alpha(bitmap.getPixel(x, y)), "frame $index corner $x,$y")
            }
            File(dir, "frame-$index.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Test
    fun `an unknown hair style draws the seira style`() {
        val seira = SeiraCharacterSnapshot.render(context, "seira", "red", emptyList(), null, 256)
        val unknown = SeiraCharacterSnapshot.render(context, "no-such-style", "red", emptyList(), null, 256)
        assertTrue(seira.sameAs(unknown))
    }

    @Test
    fun `an unknown accessory is skipped`() {
        val plain = SeiraCharacterSnapshot.render(context, "bob", "blue", emptyList(), null, 256)
        val skipped = SeiraCharacterSnapshot.render(context, "bob", "blue", listOf("top-hat"), null, 256)
        assertTrue(plain.sameAs(skipped))
    }

    @Test
    fun `one per slot is kept, at most three`() {
        val assets = SeiraAssets.forContext(context)
        val look = FrameLook.of(
            assets, "bob", "red", listOf("glasses", "sunglasses", "ribbon", "scarf", "cap"), null, CharacterMood.IDLE,
        )
        assertEquals(listOf("glasses", "ribbon", "scarf"), look.accessories)
    }

    @Test
    fun `the cast has 26 characters and Seira comes first`() {
        val cast = Json.parseToJsonElement(SeiraAssets.forContext(context).castJson()).jsonArray
        assertEquals(26, cast.size)
        assertEquals("seira", cast[0].jsonObject["id"]!!.jsonPrimitive.content)
        assertEquals("Seira", cast[0].jsonObject["name"]!!.jsonPrimitive.content)
    }

    @Test
    fun `the talk level changes the mouth`() {
        val shut = SeiraCharacterSnapshot.render(context, "seira", "chrome", emptyList(), null, 256)
        val open = SeiraCharacterSnapshot.render(context, "seira", "chrome", emptyList(), null, 256, CharacterMood.IDLE, 1f)
        assertTrue(!shut.sameAs(open))
    }

    private fun opaquePixels(bitmap: Bitmap): Int {
        var count = 0
        for (y in 0 until bitmap.height step 2) for (x in 0 until bitmap.width step 2) {
            if (Color.alpha(bitmap.getPixel(x, y)) > 200) count++
        }
        return count
    }
}
