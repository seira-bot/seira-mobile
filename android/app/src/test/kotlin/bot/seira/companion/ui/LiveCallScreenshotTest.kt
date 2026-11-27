package bot.seira.companion.ui

import androidx.compose.ui.test.junit4.createComposeRule
import bot.seira.companion.audio.LiveCallPhase
import bot.seira.companion.core.Bot
import bot.seira.companion.core.ModelSelection
import bot.seira.companion.ui.theme.ThemeMode
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
class LiveCallScreenshotTest {
    @get:Rule val compose = createComposeRule()
    private val bot = Bot(
        id = "b1", threadId = "t1", name = "Seira", title = "", description = "", notifications = true,
        color = "pink", unread = false, modelSelection = ModelSelection("instance", "model"), createdAt = 1.0,
    )
    private val actions = LiveCallScreenActions({}, {}, {}, {}, {}, {})

    @androidx.compose.runtime.Composable
    private fun WithEnvironment(content: @androidx.compose.runtime.Composable () -> Unit) {
        val scene = androidx.compose.runtime.remember { WiringScene() }
        androidx.compose.runtime.CompositionLocalProvider(LocalCompanion provides scene.environment, content = content)
    }

    private fun model(muted: Boolean) = LiveCallBarModel.Local(
        title = "Live with Seira",
        caption = "Dina is free from 10:00 to 11:00. Should I send the invite now?",
        heard = "Please check Dina's calendar for tomorrow morning.",
        muted = muted,
        speaker = false,
        phase = LiveCallPhase.LIVE,
        clock = "2:14",
    )

    @Test
    fun lightCallIsWritten() {
        assertTrue(compose.captureDesign("call", ThemeMode.LIGHT) { WithEnvironment { LiveCallScreen(model(false), bot, "Seira", true, actions, animateAvatar = false) } }.length() > 0)
    }

    @Test
    fun darkCallIsWritten() {
        assertTrue(compose.captureDesign("call", ThemeMode.DARK) { WithEnvironment { LiveCallScreen(model(false), bot, "Seira", true, actions, animateAvatar = false) } }.length() > 0)
    }

    @Test
    fun mutedCallIsWritten() {
        assertTrue(compose.captureDesign("call-muted", ThemeMode.LIGHT) { WithEnvironment { LiveCallScreen(model(true), bot, "Seira", false, actions, animateAvatar = false) } }.length() > 0)
    }
}
