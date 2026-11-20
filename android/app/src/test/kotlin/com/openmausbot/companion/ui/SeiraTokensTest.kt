package com.openmausbot.companion.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.openmausbot.companion.ui.theme.SeiraColors
import com.openmausbot.companion.ui.theme.identityPlate
import com.openmausbot.companion.ui.theme.identityPlateArgb
import kotlin.test.assertEquals
import org.junit.Test

class SeiraTokensTest {
    private fun hex(color: Color): String = "%08X".format(color.toArgb())

    @Test
    fun `light tokens match the design`() {
        val c = SeiraColors.Light
        assertEquals(false, c.isDark)
        assertEquals("FFFFFFFF", hex(c.app))
        assertEquals("FFF4F4F6", hex(c.tint))
        assertEquals("FF141417", hex(c.ink))
        assertEquals("FF141417", hex(c.primary))
        assertEquals("FFFFFFFF", hex(c.onPrimary))
        assertEquals("FF2563EB", hex(c.accent))
        assertEquals("FF1D4FD0", hex(c.accentText))
        assertEquals("FF13834F", hex(c.success))
        assertEquals("FFD98A00", hex(c.warning))
        assertEquals("FFA35F00", hex(c.warningText))
        assertEquals("FFC4232D", hex(c.danger))
        assertEquals("FFEBEBEE", hex(c.control))
        assertEquals("FFF0F0F2", hex(c.bubble))
        assertEquals("FFE8E8EB", hex(c.seg))
        assertEquals("FFC81E4A", hex(c.seiraName))
        assertEquals("FF0B7480", hex(c.lumiName))
        assertEquals(hex(Color(0xFF141417).copy(alpha = 0.68f)), hex(c.ink2))
        assertEquals(hex(Color(0xFF141417).copy(alpha = 0.60f)), hex(c.ink3))
        assertEquals(hex(Color(0xFFFFFFFF).copy(alpha = 0.78f)), hex(c.glass))
        assertEquals("FFE5383B", hex(c.call.danger))
        assertEquals("FFEFEFF1", hex(c.call.control))
    }

    @Test
    fun `dark tokens match the design`() {
        val c = SeiraColors.Dark
        assertEquals(true, c.isDark)
        assertEquals("FF111113", hex(c.app))
        assertEquals("FF1B1B1E", hex(c.tint))
        assertEquals("FFF2F2F3", hex(c.ink))
        assertEquals("FFF2F2F3", hex(c.primary))
        assertEquals("FF111113", hex(c.onPrimary))
        assertEquals("FF4F8DFF", hex(c.accent))
        assertEquals("FF8CB5FF", hex(c.accentText))
        assertEquals("FF3CCF8E", hex(c.success))
        assertEquals("FFF5A524", hex(c.warning))
        assertEquals("FFF5B04A", hex(c.warningText))
        assertEquals("FFFF6B70", hex(c.danger))
        assertEquals("FF26262A", hex(c.control))
        assertEquals("FF1C1C1F", hex(c.sheet))
        assertEquals("FF3A3A40", hex(c.thumb))
        assertEquals(hex(Color(0xFFF2F2F3).copy(alpha = 0.50f)), hex(c.ink3))
        assertEquals(hex(Color(0xFF1E1E22).copy(alpha = 0.72f)), hex(c.glass))
        assertEquals("FF0D0D0F", hex(c.call.app))
        assertEquals("FFFF4D5E", hex(c.call.danger))
        assertEquals("FF232327", hex(c.call.control))
    }

    @Test
    fun `the plate mixes the identity colour into a neutral base`() {
        assertEquals(BotPalette.mix(0xFFF1F1F4.toInt(), BotPalette.argb("red"), 0.26), identityPlateArgb("red", false))
        assertEquals(BotPalette.mix(0xFF1C1C21.toInt(), BotPalette.argb("red"), 0.34), identityPlateArgb("red", true))
        assertEquals(Color(identityPlateArgb("blue", true)), identityPlate("blue", true))
    }

    @Test
    fun `an unknown identity colour falls back to the grey`() {
        assertEquals(
            BotPalette.mix(0xFFF1F1F4.toInt(), BotPalette.FALLBACK, 0.26),
            identityPlateArgb(null, false),
        )
        assertEquals(identityPlateArgb(null, true), identityPlateArgb("not-a-colour", true))
    }
}
