package com.openmausbot.companion.ui

import com.openmausbot.companion.core.Connection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ComputerSwitchRulesTest {
    private val laptop = Connection(id = "laptop", name = "Laptop", host = "laptop.local", port = 8810)
    private val studio = Connection(id = "studio", name = "Studio", host = "studio.local", port = 8810)

    @Test
    fun `the active computer comes first and the rest keep their order`() {
        val third = studio.copy(id = "third", name = "Third")
        assertEquals(listOf(third, laptop, studio), ComputerSwitchRules.ordered(listOf(laptop, studio, third), "third"))
    }

    @Test
    fun `a unique name needs no address`() {
        assertNull(ComputerSwitchRules.distinguishingAddress(laptop, listOf(laptop, studio)))
    }

    @Test
    fun `computers with one name show their address`() {
        val twin = studio.copy(name = "Laptop")
        assertEquals(laptop.displayAddress, ComputerSwitchRules.distinguishingAddress(laptop, listOf(laptop, twin)))
        assertEquals(twin.displayAddress, ComputerSwitchRules.distinguishingAddress(twin, listOf(laptop, twin)))
    }

    @Test
    fun `a relay-only computer shows no placeholder address`() {
        val relayOnly = Connection(id = "vps", name = "Laptop", host = "relay-only.invalid", port = 443)
        assertNull(ComputerSwitchRules.distinguishingAddress(relayOnly, listOf(laptop, relayOnly)))
    }
}
