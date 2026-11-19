package com.openmausbot.companion.character

import android.graphics.Color
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.openmausbot.companion.core.Bot
import com.openmausbot.companion.core.ModelSelection
import com.openmausbot.companion.core.SeiraLook
import com.openmausbot.companion.shortcuts.BotIcons
import com.openmausbot.companion.shortcuts.BotIdentity
import com.openmausbot.companion.ui.BotAvatar
import com.openmausbot.companion.ui.LocalCompanion
import com.openmausbot.companion.ui.WiringScene
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w390dp-h844dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BotLookAvatarTest {
    @get:Rule
    val rule = createComposeRule()

    private val bot = Bot(
        id = "b1",
        threadId = "thread-b1",
        name = "Dina Pratama",
        title = "research",
        description = "",
        notifications = true,
        color = "blue",
        unread = false,
        modelSelection = ModelSelection("instance-1", "model-1"),
        createdAt = 0.0,
        mascotBody = "cursor",
        mascotAccessories = listOf("glasses"),
    )

    @Test
    fun `a bot without a photo draws the carved avatar of its look`() {
        val scene = WiringScene()
        rule.setContent {
            CompositionLocalProvider(LocalCompanion provides scene.environment) {
                Column {
                    BotAvatar(bot = bot, size = 64.dp, animated = false, contentDescription = "bot")
                    CarvedAvatar(look = SeiraLook.from(bot), size = 64.dp, contentDescription = "carved")
                }
            }
        }
        val drawn = rule.onNodeWithContentDescription("bot").captureToImage().asAndroidBitmap()
        val carved = rule.onNodeWithContentDescription("carved").captureToImage().asAndroidBitmap()
        assertTrue(drawn.sameAs(carved))
        assertTrue(opaquePixels(drawn) > 0)
    }

    @Test
    fun `an icon without a photo draws the character on the plate`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val identity = BotIdentity.of(bot)
        assertEquals("cursor", identity.mascotBody)
        assertEquals(listOf("glasses"), identity.mascotAccessories)
        val character = BotIcons.character(context, identity)
        val plate = BotIcons.characterPlate(bot.color)

        val circle = BotIcons.circle(null, bot.name, bot.color, character = character)
        assertNotEquals(plate, circle.getPixel(circle.width / 2, circle.height / 2))
        assertTrue(Color.alpha(circle.getPixel(circle.width / 2, circle.height / 2)) == 255)

        val adaptive = BotIcons.adaptive(null, bot.name, bot.color, character = character)
        assertNotEquals(plate, adaptive.getPixel(adaptive.width / 2, adaptive.height / 2))
    }

    private fun opaquePixels(bitmap: android.graphics.Bitmap): Int {
        var count = 0
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
            if (Color.alpha(bitmap.getPixel(x, y)) > 200) count++
        }
        return count
    }
}
