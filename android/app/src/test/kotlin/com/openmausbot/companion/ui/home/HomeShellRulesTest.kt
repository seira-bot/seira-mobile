package com.openmausbot.companion.ui.home

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomeShellRulesTest {
    @Test
    fun `a restored filter survives until the fleet has arrived`() {
        assertFalse(HomeShellRules.staleFilter(fleetLoaded = false, departments = emptyList(), filter = "Sales"))
    }

    @Test
    fun `a filter for a department that is gone is dropped once the fleet is here`() {
        assertTrue(HomeShellRules.staleFilter(fleetLoaded = true, departments = listOf("Ops"), filter = "Sales"))
    }

    @Test
    fun `a filter for a department that exists stays`() {
        assertFalse(HomeShellRules.staleFilter(fleetLoaded = true, departments = listOf("Sales"), filter = "Sales"))
    }

    @Test
    fun `no filter is never stale`() {
        assertFalse(HomeShellRules.staleFilter(fleetLoaded = true, departments = emptyList(), filter = null))
    }
}
