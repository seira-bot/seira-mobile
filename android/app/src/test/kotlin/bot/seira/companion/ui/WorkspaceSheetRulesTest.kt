package bot.seira.companion.ui

import bot.seira.companion.core.PhoneWorkspace
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WorkspaceSheetRulesTest {
    @Test
    fun `six hex digits become an opaque color with or without the hash`() {
        assertEquals(0xFF3366FFL, WorkspaceSheetRules.parseColor("#3366ff"))
        assertEquals(0xFF3366FFL, WorkspaceSheetRules.parseColor("3366FF"))
        assertEquals(0xFF3366FFL, WorkspaceSheetRules.parseColor("  #3366ff "))
    }

    @Test
    fun `anything else is no color`() {
        assertNull(WorkspaceSheetRules.parseColor(null))
        assertNull(WorkspaceSheetRules.parseColor(""))
        assertNull(WorkspaceSheetRules.parseColor("red"))
        assertNull(WorkspaceSheetRules.parseColor("#36f"))
        assertNull(WorkspaceSheetRules.parseColor("#3366ffaa"))
        assertNull(WorkspaceSheetRules.parseColor("#33ggff"))
    }

    @Test
    fun `a blank name is no name`() {
        assertEquals("Work", WorkspaceSheetRules.name(PhoneWorkspace(id = "a", name = " Work ")))
        assertNull(WorkspaceSheetRules.name(PhoneWorkspace(id = "a", name = "  ")))
        assertNull(WorkspaceSheetRules.name(PhoneWorkspace(id = "a", name = null)))
    }
}
