package bot.seira.companion.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.click
import androidx.compose.ui.unit.dp
import bot.seira.companion.core.OptionCard
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private fun permission(vararg options: String) = OptionCard(
    title = "Run a command",
    subtitle = "ls",
    options = options.toList(),
    requestId = "r1",
    tool = "Bash",
    requestType = "permission",
)

class CardSwipeRulesTest {
    private val width = 1000f
    private val density = 2f

    @Test
    fun `a drag past 35 percent of the width decides its side`() {
        assertEquals(SwipeSide.ALLOW, CardSwipe.verdict(350f, width, 0f, density))
        assertEquals(SwipeSide.DENY, CardSwipe.verdict(-350f, width, 0f, density))
    }

    @Test
    fun `a drag short of the threshold springs back`() {
        assertNull(CardSwipe.verdict(349f, width, 0f, density))
        assertNull(CardSwipe.verdict(-349f, width, 0f, density))
        assertNull(CardSwipe.verdict(0f, width, 0f, density))
    }

    @Test
    fun `a fast fling in the drag direction decides, a fling back does not`() {
        assertEquals(SwipeSide.ALLOW, CardSwipe.verdict(100f, width, 2_500f, density))
        assertEquals(SwipeSide.DENY, CardSwipe.verdict(-100f, width, -2_500f, density))
        assertNull(CardSwipe.verdict(100f, width, -2_500f, density))
    }

    @Test
    fun `a fling needs some travel so a tap with jitter does nothing`() {
        assertNull(CardSwipe.verdict(10f, width, 5_000f, density))
    }

    @Test
    fun `the overlay grows with distance and stops at the threshold`() {
        assertEquals(0f, CardSwipe.progress(0f, width))
        assertEquals(0.5f, CardSwipe.progress(175f, width), 0.001f)
        assertEquals(1f, CardSwipe.progress(-900f, width))
    }

    @Test
    fun `a drag that starts at the window edge belongs to the system back gesture`() {
        val guard = 48f
        assertTrue(CardSwipe.startsInSystemEdge(10f, 1080f, 0f, 0f, guard))
        assertTrue(CardSwipe.startsInSystemEdge(1075f, 1080f, 0f, 0f, guard))
        assertTrue(CardSwipe.startsInSystemEdge(60f, 1080f, 90f, 0f, guard))
        assertFalse(CardSwipe.startsInSystemEdge(540f, 1080f, 0f, 0f, guard))
    }

    @Test
    fun `a permission card with Allow and Deny can be swiped`() {
        assertTrue(CardSwipe.isSwipeable(permission("Allow", "Deny")))
        assertEquals("Allow", CardSwipe.choiceFor(permission("Allow", "Deny"), SwipeSide.ALLOW))
        assertEquals("Deny", CardSwipe.choiceFor(permission("Allow", "Deny"), SwipeSide.DENY))
    }

    @Test
    fun `Always allow is never a swipe`() {
        assertNull(CardSwipe.choiceFor(permission("Always allow", "Deny"), SwipeSide.ALLOW))
        assertFalse(CardSwipe.isSwipeable(permission("Always allow", "Deny")))
    }

    @Test
    fun `an answered or expired card cannot be swiped`() {
        assertFalse(CardSwipe.isSwipeable(permission("Allow", "Deny").copy(answered = "allow")))
        assertFalse(CardSwipe.isSwipeable(permission("Allow", "Deny").copy(expired = true)))
        assertFalse(CardSwipe.isSwipeable(permission("Allow", "Deny").copy(requestId = null)))
    }

    @Test
    fun `a proposal, a question and a skill review keep their buttons only`() {
        val proposal = OptionCard(title = "Delete bot?", subtitle = "x", options = listOf("Confirm", "Cancel"), requestId = "r2")
        assertFalse(CardSwipe.isSwipeable(proposal))
        val question = permission("Allow", "Deny").copy(requestType = "question")
        assertFalse(CardSwipe.isSwipeable(question))
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SwipeableCardFrameTest {
    @get:Rule val compose = createComposeRule()

    private val decisions = mutableListOf<SwipeSide>()
    private var thresholdTicks = 0

    private fun show(enabled: Boolean = true) {
        decisions.clear()
        thresholdTicks = 0
        compose.setContent {
            SwipeableCardFrame(
                enabled = enabled,
                shape = RoundedCornerShape(22.dp),
                onThreshold = { thresholdTicks++ },
                onDecision = { decisions += it },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(Modifier.fillMaxWidth().height(160.dp))
            }
        }
    }

    @Test
    fun `swiping right asks to allow and swiping left asks to deny`() {
        show()
        compose.onNodeWithTag(CARD_SWIPE_FRAME_TAG).performTouchInput { swipeRight(startX = width * 0.2f, endX = width * 0.9f) }
        compose.waitForIdle()
        assertEquals(listOf(SwipeSide.ALLOW), decisions)
        assertEquals(1, thresholdTicks)
        compose.onNodeWithTag(CARD_SWIPE_FRAME_TAG).performTouchInput { swipeLeft(startX = width * 0.8f, endX = width * 0.1f) }
        compose.waitForIdle()
        assertEquals(listOf(SwipeSide.ALLOW, SwipeSide.DENY), decisions)
    }

    @Test
    fun `a short drag and a tap decide nothing`() {
        show()
        compose.onNodeWithTag(CARD_SWIPE_FRAME_TAG).performTouchInput {
            swipeRight(startX = width * 0.5f, endX = width * 0.6f, durationMillis = 800)
        }
        compose.onNodeWithTag(CARD_SWIPE_FRAME_TAG).performTouchInput { click() }
        compose.waitForIdle()
        assertEquals(emptyList(), decisions)
        assertEquals(0, thresholdTicks)
    }

    @Test
    fun `a vertical drag decides nothing`() {
        show()
        compose.onNodeWithTag(CARD_SWIPE_FRAME_TAG).performTouchInput { swipeDown() }
        compose.waitForIdle()
        assertEquals(emptyList(), decisions)
    }

    @Test
    fun `a card that is not swipeable ignores the gesture`() {
        show(enabled = false)
        compose.onNodeWithTag(CARD_SWIPE_FRAME_TAG).assertDoesNotExist()
    }

    @Test
    fun `a disabled frame decides nothing on a horizontal swipe`() {
        decisions.clear()
        compose.setContent {
            SwipeableCardFrame(
                enabled = false,
                shape = RoundedCornerShape(22.dp),
                onThreshold = { thresholdTicks++ },
                onDecision = { decisions += it },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(Modifier.fillMaxWidth().height(160.dp).testTag("content"))
            }
        }
        compose.onNodeWithTag("content").performTouchInput { swipeRight(startX = width * 0.2f, endX = width * 0.9f) }
        compose.waitForIdle()
        assertEquals(emptyList(), decisions)
    }

    @Test
    fun `the card keeps its own state when swiping turns off after a decision`() {
        var enabled by mutableStateOf(true)
        decisions.clear()
        compose.setContent {
            SwipeableCardFrame(
                enabled = enabled,
                shape = RoundedCornerShape(22.dp),
                onThreshold = {},
                onDecision = { decisions += it },
                modifier = Modifier.fillMaxWidth(),
            ) {
                var open by remember { mutableStateOf(false) }
                Box(Modifier.fillMaxWidth().height(160.dp)) {
                    Box(Modifier.size(40.dp).testTag("opener").clickable { open = true })
                    if (open) Text("details", Modifier.testTag("details"))
                }
            }
        }
        compose.onNodeWithTag("opener").performClick()
        compose.onNodeWithTag("details").assertExists()
        compose.onNodeWithTag(CARD_SWIPE_FRAME_TAG).performTouchInput { swipeRight(startX = width * 0.2f, endX = width * 0.9f) }
        compose.waitForIdle()
        assertEquals(listOf(SwipeSide.ALLOW), decisions)
        enabled = false
        compose.waitForIdle()
        compose.onNodeWithTag("details").assertExists()
    }
}
