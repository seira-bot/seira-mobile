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
        assertEquals("FFF4F4F6", hex(c.app))
        assertEquals("FFFFFFFF", hex(c.tint))
        assertEquals("FFFFFFFF", hex(c.s1))
        assertEquals("FFE9EAEE", hex(c.s2))
        assertEquals("FFD9DBE1", hex(c.s3))
        assertEquals("FF121317", hex(c.ink))
        assertEquals("FF454A53", hex(c.ink2))
        assertEquals("FF5C626D", hex(c.ink3))
        assertEquals(hex(Color(0xFF0A0B0D).copy(alpha = 0.10f)), hex(c.hairline))
        assertEquals(hex(Color(0xFF0A0B0D).copy(alpha = 0.20f)), hex(c.line2))
        assertEquals("FF121317", hex(c.primary))
        assertEquals("FFFFFFFF", hex(c.onPrimary))
        assertEquals("FFC42A4D", hex(c.accent))
        assertEquals("FFA81E3D", hex(c.accentText))
        assertEquals(hex(Color(0xFFC42A4D).copy(alpha = 0.10f)), hex(c.accentSoft))
        assertEquals("FFFFFFFF", hex(c.onAccent))
        assertEquals("FF0A6A44", hex(c.success))
        assertEquals("FF7D4E00", hex(c.warning))
        assertEquals("FF7D4E00", hex(c.warningText))
        assertEquals("FFB3261E", hex(c.danger))
        assertEquals("FFE9EAEE", hex(c.control))
        assertEquals("FFE9EAEE", hex(c.bubble))
        assertEquals("FFE9EAEE", hex(c.seg))
        assertEquals("FFA81E3D", hex(c.seiraName))
        assertEquals("FF0B7480", hex(c.lumiName))
        assertEquals(hex(Color(0xFFFFFFFF).copy(alpha = 0.78f)), hex(c.glass))
        assertEquals(hex(Color(0xFF000000).copy(alpha = 0.62f)), hex(c.scrim))
        assertEquals("FFE5383B", hex(c.call.danger))
        assertEquals("FFE9EAEE", hex(c.call.control))
    }

    @Test
    fun `dark tokens match the design`() {
        val c = SeiraColors.Dark
        assertEquals(true, c.isDark)
        assertEquals("FF0B0C0E", hex(c.app))
        assertEquals("FF15171B", hex(c.tint))
        assertEquals("FF15171B", hex(c.s1))
        assertEquals("FF1F2228", hex(c.s2))
        assertEquals("FF2C3038", hex(c.s3))
        assertEquals("FFEDEEF0", hex(c.ink))
        assertEquals("FFA8ADB5", hex(c.ink2))
        assertEquals("FF8B919B", hex(c.ink3))
        assertEquals(hex(Color(0xFFFFFFFF).copy(alpha = 0.08f)), hex(c.hairline))
        assertEquals(hex(Color(0xFFFFFFFF).copy(alpha = 0.17f)), hex(c.line2))
        assertEquals("FFF2F3F5", hex(c.primary))
        assertEquals("FF0B0C0E", hex(c.onPrimary))
        assertEquals("FFFF3D67", hex(c.accent))
        assertEquals("FFFF7D98", hex(c.accentText))
        assertEquals(hex(Color(0xFFFF3D67).copy(alpha = 0.16f)), hex(c.accentSoft))
        assertEquals("FF1B0409", hex(c.onAccent))
        assertEquals("FF3DDC97", hex(c.success))
        assertEquals("FFFFB547", hex(c.warning))
        assertEquals("FFFFB547", hex(c.warningText))
        assertEquals("FFFF8A80", hex(c.danger))
        assertEquals("FF1F2228", hex(c.control))
        assertEquals("FF15171B", hex(c.sheet))
        assertEquals("FF2C3038", hex(c.thumb))
        assertEquals(hex(Color(0xFF15171B).copy(alpha = 0.72f)), hex(c.glass))
        assertEquals("FF070809", hex(c.call.app))
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
