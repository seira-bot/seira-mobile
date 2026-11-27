package com.openmausbot.companion.ui.profile

import com.openmausbot.companion.core.Bot
import com.openmausbot.companion.core.CompanionJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BotActionRulesTest {
    private fun bot(threadId: String, pinned: Boolean? = null): Bot = CompanionJson.decodeFromString(
        """{"id":"b","threadId":"$threadId","name":"Scout","title":"","description":"","notifications":true,
            "color":"#000000","unread":false,"createdAt":1.0,${if (pinned == null) "" else "\"pinned\":$pinned,"}
            "modelSelection":{"instanceId":"codex","model":"m"}}""",
    )

    @Test
    fun `copy is hidden when the bot has no conversation`() {
        assertEquals(
            listOf(BotAction.PIN, BotAction.MARK_UNREAD, BotAction.MARK_ALL_READ, BotAction.MOVE),
            BotActionRules.visible(bot("")),
        )
        assertTrue(BotAction.COPY_ID in BotActionRules.visible(bot("t1")))
    }

    @Test
    fun `pin reads from the bot`() {
        assertFalse(BotActionRules.isPinned(bot("t1")))
        assertTrue(BotActionRules.isPinned(bot("t1", pinned = true)))
    }

    @Test
    fun `departments match without regard to case`() {
        assertTrue(BotActionRules.sameDepartment("Sales", " sales"))
        assertTrue(BotActionRules.sameDepartment("", " "))
        assertFalse(BotActionRules.sameDepartment("Sales", ""))
    }
}
