package bot.seira.companion.ui

import android.app.UiModeManager
import bot.seira.companion.ui.theme.NightModeApplier
import bot.seira.companion.ui.theme.ThemeMode
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class ThemeModeTest {
    @Test
    fun `wire values round trip and bad values read as auto`() {
        ThemeMode.entries.forEach { assertEquals(it, ThemeMode.fromWire(it.wireValue)) }
        assertEquals(ThemeMode.AUTO, ThemeMode.DEFAULT)
        assertEquals(ThemeMode.AUTO, ThemeMode.fromWire(null))
        assertEquals(ThemeMode.AUTO, ThemeMode.fromWire("sepia"))
        assertEquals(ThemeMode.AUTO, ThemeMode.fromWire("LIGHT"))
    }

    @Test
    fun `each mode resolves dark against the phone`() {
        assertFalse(ThemeMode.LIGHT.isDark(systemDark = true))
        assertFalse(ThemeMode.LIGHT.isDark(systemDark = false))
        assertTrue(ThemeMode.DARK.isDark(systemDark = true))
        assertTrue(ThemeMode.DARK.isDark(systemDark = false))
        assertTrue(ThemeMode.AUTO.isDark(systemDark = true))
        assertFalse(ThemeMode.AUTO.isDark(systemDark = false))
    }

    @Test
    fun `modes map to system night modes`() {
        assertEquals(UiModeManager.MODE_NIGHT_NO, NightModeApplier.nightModeFor(ThemeMode.LIGHT))
        assertEquals(UiModeManager.MODE_NIGHT_YES, NightModeApplier.nightModeFor(ThemeMode.DARK))
        assertEquals(UiModeManager.MODE_NIGHT_AUTO, NightModeApplier.nightModeFor(ThemeMode.AUTO))
    }

    @Test
    fun `the system is told from API 31 and not before`() {
        val sent = mutableListOf<Int>()
        NightModeApplier(sent::add, sdkInt = 31).apply(ThemeMode.DARK)
        assertEquals(listOf(UiModeManager.MODE_NIGHT_YES), sent)
        NightModeApplier(sent::add, sdkInt = 30).apply(ThemeMode.LIGHT)
        assertEquals(1, sent.size)
        NightModeApplier(setNightMode = null, sdkInt = 34).apply(ThemeMode.LIGHT)
    }
}
