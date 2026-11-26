package com.openmausbot.companion.ui

import com.openmausbot.companion.R
import com.openmausbot.companion.core.Session
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ActionErrorCopyTest {
    @Test
    fun `the core errors have a string resource`() {
        assertEquals(
            R.string.shell_error_queued_steer_unsupported,
            ActionErrorCopy.resource(Session.QUEUED_STEER_UNSUPPORTED_MESSAGE),
        )
        assertEquals(R.string.shell_error_reload_memory, ActionErrorCopy.resource(Session.RELOAD_MEMORY_MESSAGE))
    }

    @Test
    fun `other text is left to the general copy`() {
        assertNull(ActionErrorCopy.resource("Something else"))
    }
}
