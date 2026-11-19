package com.openmausbot.companion.ui

import com.openmausbot.companion.audio.LiveCallSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CallsDisabledRulesTest {
    private val idle = LiveCallSnapshot()

    @Test
    fun `a build without calls offers no call`() {
        assertFalse(LiveCallRules.offersCall(idle, null, callsEnabled = false))
        assertTrue(LiveCallRules.offersCall(idle, null, callsEnabled = true))
    }

    @Test
    fun `a build without calls shows no call button in a bot chat`() {
        assertEquals(ChatCallAction.NONE, ChatCallRules.of(idle, null, isBotChat = true, callsEnabled = false))
        assertEquals(ChatCallAction.START, ChatCallRules.of(idle, null, isBotChat = true, callsEnabled = true))
    }
}
