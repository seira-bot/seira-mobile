package bot.seira.companion.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import bot.seira.companion.audio.LiveCallPhase
import bot.seira.companion.core.Bot
import bot.seira.companion.core.ModelSelection
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
class LiveCallScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val taps = mutableListOf<String>()
    private val bot = Bot(
        id = "b1", threadId = "t1", name = "Dina", title = "", description = "", notifications = true,
        color = "green", unread = false, modelSelection = ModelSelection("instance", "model"), createdAt = 1.0,
    )
    private val actions = LiveCallScreenActions(
        onBack = { taps += "back" },
        onMute = { taps += "mute:$it" },
        onSpeaker = { taps += "speaker:$it" },
        onHangUp = { taps += "hangup" },
        onRetry = { taps += "retry" },
        onClose = { taps += "close" },
    )

    @androidx.compose.runtime.Composable
    private fun WithEnvironment(content: @androidx.compose.runtime.Composable () -> Unit) {
        val scene = androidx.compose.runtime.remember { WiringScene() }
        androidx.compose.runtime.CompositionLocalProvider(LocalCompanion provides scene.environment) {
            CompanionTheme(darkTheme = false, content = content)
        }
    }

    private fun live(muted: Boolean = false, speaker: Boolean = true, caption: String = "Tomorrow is free.", heard: String = "Check my calendar.") =
        LiveCallBarModel.Local("Live with Dina", caption, heard, muted, speaker, LiveCallPhase.LIVE, clock = "2:14")

    private fun mount(model: LiveCallBarModel.Local, speaking: Boolean = false) {
        compose.setContent {
            WithEnvironment { LiveCallScreen(model, bot, "Dina", speaking, actions, animateAvatar = false) }
        }
    }

    @Test
    fun theScreenShowsTheBotTheClockTheCaptionsAndTheControls() {
        mount(live())
        compose.onNodeWithContentDescription("Back to chat").assertIsDisplayed()
        compose.onNodeWithText("Live call").assertIsDisplayed()
        compose.onNodeWithTag("CallClock").assertTextEquals("2:14")
        compose.onAllNodesWithText("Dina")[0].assertIsDisplayed()
        compose.onNodeWithText("Listening").assertIsDisplayed()
        compose.onNodeWithTag("CallHeard").assertTextEquals("Check my calendar.")
        compose.onNodeWithTag("CallCaption").assertTextEquals("Tomorrow is free.")
        compose.onNodeWithText("Mute").assertIsDisplayed()
        compose.onNodeWithText("Speaker").assertIsDisplayed()
        compose.onNodeWithText("Captions").assertIsDisplayed()
        compose.onNodeWithContentDescription("End call").assertIsDisplayed()
    }

    @Test
    fun theVoiceStatusNamesTheBotWhileItSpeaks() {
        mount(live(), speaking = true)
        compose.onNodeWithText("Dina is speaking").assertIsDisplayed()
    }

    @Test
    fun backToChatEndAndTheControlsCallTheirActions() {
        mount(live())
        compose.onNodeWithContentDescription("Back to chat").performClick()
        compose.onNodeWithText("Mute").performClick()
        compose.onNodeWithText("Speaker").performClick()
        compose.onNodeWithContentDescription("End call").performClick()
        assertEquals(listOf("back", "mute:true", "speaker:false", "hangup"), taps)
    }

    @Test
    fun mutedShrinksTheRingsAndSaysSo() {
        mount(live(muted = true))
        compose.onNodeWithText("You are muted").assertIsDisplayed()
        compose.onNodeWithText("Unmute").assertIsDisplayed()
        compose.onNodeWithTag("CallRings").assert(SemanticsMatcher.expectValue(RingScaleKey, 0.94f))
    }

    @Test
    fun anUnmutedCallKeepsFullRings() {
        mount(live())
        compose.onNodeWithTag("CallRings").assert(SemanticsMatcher.expectValue(RingScaleKey, 1f))
    }

    @Test
    fun theCaptionsButtonHidesAndShowsTheCard() {
        mount(live())
        compose.onNodeWithTag("CallCaptionCard").assertExists()
        compose.onNodeWithText("Captions").performClick()
        compose.waitForIdle()
        compose.onAllNodesWithTag("CallCaptionCard").assertCountEquals(0)
        compose.onNodeWithText("Captions").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("CallCaptionCard").assertExists()
    }

    @Test
    fun connectingShowsItsTextAndNoCaptionCard() {
        mount(LiveCallBarModel.Local("Connecting…", "", "", false, true, LiveCallPhase.STARTING))
        compose.onNodeWithText("Connecting…").assertIsDisplayed()
        compose.onAllNodesWithTag("CallCaptionCard").assertCountEquals(0)
    }

    @Test
    fun anEndedCallShowsTheReasonTryAgainAndClose() {
        mount(LiveCallBarModel.Local("Call dropped.", "", "", false, true, LiveCallPhase.ENDED, canRetry = true))
        compose.onNodeWithText("Call dropped.").assertIsDisplayed()
        compose.onNodeWithText("Try again").performClick()
        compose.onNodeWithText("Close").performClick()
        compose.onAllNodesWithContentDescription("End call").assertCountEquals(0)
        assertEquals(listOf("retry", "close"), taps)
    }

    @Test
    fun anEndThatTryingAgainCannotFixOffersOnlyClose() {
        mount(LiveCallBarModel.Local(LiveCallRules.NEEDS_KEY_MESSAGE, "", "", false, true, LiveCallPhase.ENDED, canRetry = false))
        compose.onAllNodesWithText("Try again").assertCountEquals(0)
        compose.onNodeWithText("Close").assertIsDisplayed()
    }

    @Test
    fun backToChatLeavesTheBarOfTheContinuingCall() {
        var expanded by mutableStateOf(true)
        val model = live()
        compose.setContent {
            WithEnvironment {
                Box {
                    if (expanded) {
                        LiveCallScreen(model, bot, "Dina", false, LiveCallScreenActions({ expanded = false }, {}, {}, {}, {}, {}), animateAvatar = false)
                    } else {
                        LiveCallBar(model, LiveCallBarActions({}, {}, {}, {}, {}, onOpen = { expanded = true }))
                    }
                }
            }
        }
        compose.onNodeWithContentDescription("Back to chat").performClick()
        compose.onNodeWithContentDescription("Live call").assertIsDisplayed()
        compose.onAllNodesWithContentDescription("End call").assertCountEquals(0)
        compose.onNodeWithContentDescription("Live call").performClick()
        compose.onNodeWithContentDescription("Back to chat").assertIsDisplayed()
    }

    @Test
    fun theRulesPickTheStatusFromTheModelOnly() {
        val base = live(caption = "", heard = "")
        assertEquals(CallVoiceStatus.LISTENING, LiveCallScreenRules.status(base, speaking = false))
        assertEquals(CallVoiceStatus.SPEAKING, LiveCallScreenRules.status(base, speaking = true))
        assertEquals(CallVoiceStatus.MUTED, LiveCallScreenRules.status(base.copy(muted = true), speaking = true))
        assertEquals(CallVoiceStatus.CONNECTING, LiveCallScreenRules.status(base.copy(phase = LiveCallPhase.STARTING), false))
        assertTrue(LiveCallScreenRules.voiceIsSpeaking("Hi", ""))
        assertFalse(LiveCallScreenRules.voiceIsSpeaking("Hi", "wait"))
        assertEquals(List(5) { 4 }, LiveCallScreenRules.barHeights(muted = true))
        assertEquals(listOf(6, 12, 16, 10, 6), LiveCallScreenRules.barHeights(muted = false))
    }

    @Test
    fun theScreenOpensWithTheCallAndMinimisesWhenItEnds() {
        assertEquals(true, LiveCallScreenRules.expansionFor(LiveCallPhase.IDLE, LiveCallPhase.STARTING))
        assertEquals(null, LiveCallScreenRules.expansionFor(LiveCallPhase.STARTING, LiveCallPhase.LIVE))
        assertEquals(false, LiveCallScreenRules.expansionFor(LiveCallPhase.LIVE, LiveCallPhase.ENDING))
        assertEquals(false, LiveCallScreenRules.expansionFor(LiveCallPhase.STARTING, LiveCallPhase.ENDED))
        assertEquals(false, LiveCallScreenRules.expansionFor(LiveCallPhase.ENDED, LiveCallPhase.IDLE))
    }
}
