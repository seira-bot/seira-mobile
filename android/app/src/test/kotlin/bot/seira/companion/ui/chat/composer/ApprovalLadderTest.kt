package bot.seira.companion.ui.chat.composer

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.center
import androidx.compose.ui.test.down
import androidx.compose.ui.test.up
import bot.seira.companion.core.ApprovalMode
import bot.seira.companion.core.ApprovalModeResult
import bot.seira.companion.ui.bot
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ApprovalLadderTest {
    @get:Rule val compose = createComposeRule()

    private val requests = mutableListOf<ApprovalMode>()

    private fun showLadder(shown: ApprovalMode? = ApprovalMode.ASK) {
        requests.clear()
        compose.setContent { ApprovalLadder(shown = shown, enabled = true, onRequest = { requests += it }) }
    }

    @Test
    fun `a tap on the Edits label requests Edits`() {
        showLadder()
        compose.onNodeWithTag(modeRowTag(ApprovalMode.EDITS)).performClick()
        compose.waitForIdle()
        assertEquals(listOf(ApprovalMode.EDITS), requests)
    }

    @Test
    fun `a short press on the Full label requests nothing and shows the hint`() {
        showLadder()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag(modeRowTag(ApprovalMode.FULL)).performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(300)
        compose.onNodeWithTag(modeRowTag(ApprovalMode.FULL)).performTouchInput { up() }
        compose.mainClock.advanceTimeBy(100)
        assertTrue(requests.isEmpty())
        compose.onNodeWithTag(HOLD_HINT_TAG).assertExists()
    }

    @Test
    fun `a press on the Full label that lasts the hold requests Full`() {
        showLadder()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag(modeRowTag(ApprovalMode.FULL)).performTouchInput { down(center) }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        repeat(55) { compose.mainClock.advanceTimeByFrame() }
        assertEquals(listOf(ApprovalMode.FULL), requests)
        compose.onNodeWithTag(modeRowTag(ApprovalMode.FULL)).performTouchInput { up() }
        compose.mainClock.advanceTimeBy(100)
        assertEquals(listOf(ApprovalMode.FULL), requests)
    }

    @Test
    fun `no hold is asked while Full is already the mode`() {
        showLadder(ApprovalMode.FULL)
        compose.onNodeWithTag(modeRowTag(ApprovalMode.AUTO)).performClick()
        compose.waitForIdle()
        assertEquals(listOf(ApprovalMode.AUTO), requests)
    }

    @Test
    fun `with Custom as the mode the Custom row shows and Edits can still be set`() {
        val changes = mutableListOf<ApprovalMode>()
        compose.setContent {
            ApprovalModeSheet(
                current = ApprovalMode.CUSTOM,
                change = { mode, _ ->
                    changes += mode
                    ApprovalModeResult.Changed(bot(), mode)
                },
                onDismiss = {},
            )
        }
        compose.waitForIdle()
        compose.onNodeWithTag(modeRowTag(ApprovalMode.CUSTOM)).assertExists()
        compose.onNodeWithTag(modeRowTag(ApprovalMode.EDITS)).performClick()
        compose.waitForIdle()
        assertEquals(listOf(ApprovalMode.EDITS), changes)
    }
}
