package bot.seira.companion.ui.home

import bot.seira.companion.core.Bot
import bot.seira.companion.core.CompanionJson
import bot.seira.companion.ui.home.automations.AutomationsRules
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WhatShouldHappenRulesTest {
    private fun bot(id: String, name: String): Bot = CompanionJson.decodeFromString(
        """{"id":"$id","threadId":"t-$id","name":"$name","title":"","description":"","notifications":true,
            "color":"#000000","unread":false,"createdAt":1.0,
            "modelSelection":{"instanceId":"codex","model":"m"}}""",
    )

    @Test
    fun `the default bot is Seira, else the first one`() {
        val bots = listOf(bot("a", "Scout"), bot("b", "seira"))
        assertEquals("b", AutomationsRules.defaultAssistant(bots)?.id)
        assertEquals("a", AutomationsRules.defaultAssistant(bots.take(1))?.id)
        assertNull(AutomationsRules.defaultAssistant(emptyList()))
    }

    @Test
    fun `Set it up waits for a sentence`() {
        assertFalse(AutomationsRules.canSetUp(""))
        assertFalse(AutomationsRules.canSetUp("  \n "))
        assertTrue(AutomationsRules.canSetUp("Every morning at 9, summarize my inbox"))
    }
}
