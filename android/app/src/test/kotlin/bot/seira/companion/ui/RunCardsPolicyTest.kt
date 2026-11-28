package bot.seira.companion.ui

import bot.seira.companion.core.GoalRuns
import bot.seira.companion.core.Message
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class RunCardsPolicyTest {
    private fun message(kind: Message.Kind, text: String?) =
        Message("m", Message.Role.BOT, kind, 1.0, text = text)

    @Test
    fun everyKnownGoalStatusHasALabelAndAnUnknownOneShowsAsSpelled() {
        for (status in GoalRuns.knownStatuses) assertNotNull(goalStatusLabelRes(status), status)
        assertNull(goalStatusLabelRes("rehearsing"))
    }

    @Test
    fun everyBurstPartStatusHasAWord() {
        for (status in listOf("queued", "running", "done", "failed", "cancelled")) {
            assertNotNull(burstChildStatusRes(status), status)
        }
        assertNull(burstChildStatusRes("vanished"))
    }

    @Test
    fun theThreeRunKindsCopyTheirText() {
        for (kind in listOf(Message.Kind.GOAL_RUN, Message.Kind.BURST, Message.Kind.CALL)) {
            assertEquals("Hello", MessageActions.copyableText(message(kind, "Hello")), kind.name)
            assertNull(MessageActions.copyableText(message(kind, " ")), kind.name)
            assertNull(MessageActions.copyableText(message(kind, null)), kind.name)
        }
    }

    @Test
    fun theCallRowShowsAtMostThreeSpokenLines() {
        assertEquals(3, CALL_ROW_LINES)
    }
}
