package com.openmausbot.companion.character

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SeiraTintTest {
    private fun hex(argb: Int) = "#%06x".format(argb and 0xFFFFFF)

    @Test
    fun vividLiftsSaturationLikeTheDesktop() {
        assertEquals("#e44048", hex(SeiraTint.vivid(0xFFD94B52.toInt())))
        assertEquals("#009957", hex(SeiraTint.vivid(0xFF009957.toInt())))
        assertEquals("#2a7df3", hex(SeiraTint.vivid(0xFF377FE6.toInt())))
    }

    @Test
    fun greyColoursGetNoTint() {
        assertNull(SeiraTint.hairTint("silver"))
        assertNull(SeiraTint.hairTint("chrome"))
        assertNotNull(SeiraTint.hairTint("red"))
    }

    @Test
    fun anUnknownColourReadsAsGreen() {
        assertEquals(SeiraTint.vivid(SeiraTint.base("green")), SeiraTint.hairTint("nonsense"))
    }

    @Test
    fun gradientTablesHaveThirteenSteps() {
        val tables = SeiraTint.gradientTables(0xFFFFFFFF.toInt())
        assertEquals(3, tables.size)
        tables.forEach { assertEquals(13, it.size) }
        assertEquals(0f, tables[0][0])
        assertEquals(1f, tables[0][12])
        assertEquals(0.5f, tables[1][5], 0.001f)
    }
}
