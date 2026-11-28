package bot.seira.companion.ui.home.chats

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChatsLogicTest {
    @Test
    fun `create needs a name and no run in progress`() {
        assertFalse(NewBotRules.canCreate("", creating = false))
        assertFalse(NewBotRules.canCreate("   ", creating = false))
        assertFalse(NewBotRules.canCreate("Nova", creating = true))
        assertTrue(NewBotRules.canCreate(" Nova ", creating = false))
    }

    @Test
    fun `the job goes to the description and a blank job is left out`() {
        val spec = NewBotRules.spec(" Nova ", " Writes the weekly report ", "key")
        assertEquals("Nova", spec.name)
        assertEquals("Writes the weekly report", spec.description)
        assertEquals("key", spec.retryKey)
        assertNull(NewBotRules.spec("Nova", "  ", "key").description)
    }

    @Test
    fun `a retry key fits the range the computer accepts`() {
        val key = NewBotRules.newRetryKey()
        assertTrue(key.length in 16..128)
        assertTrue(key.all { it.isLetterOrDigit() || it == '-' })
        assertFalse(key == NewBotRules.newRetryKey())
    }
}
