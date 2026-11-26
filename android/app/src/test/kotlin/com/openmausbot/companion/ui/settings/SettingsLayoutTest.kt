package com.openmausbot.companion.ui.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsLayoutTest {
    @Test
    fun `a paired phone shows every group in order`() {
        assertEquals(
            listOf(
                SettingsGroup.COMPUTER,
                SettingsGroup.APPEARANCE,
                SettingsGroup.NOTIFICATIONS,
                SettingsGroup.CHAT,
                SettingsGroup.WORKSPACE,
                SettingsGroup.PRIVACY,
                SettingsGroup.HELP,
                SettingsGroup.SIGN_OUT,
                SettingsGroup.UPDATES,
            ),
            SettingsLayout.groups(
                showsComputer = true,
                paired = true,
                showsWorkspace = true,
                showsPrivacy = true,
                updatesAvailable = true,
            ),
        )
    }

    @Test
    fun `an unpaired phone with a connect action keeps the computer group but not sign out`() {
        val groups = SettingsLayout.groups(
            showsComputer = true,
            paired = false,
            showsWorkspace = false,
            showsPrivacy = false,
            updatesAvailable = false,
        )
        assertTrue(SettingsGroup.COMPUTER in groups)
        assertFalse(SettingsGroup.SIGN_OUT in groups)
        assertFalse(SettingsGroup.WORKSPACE in groups)
        assertFalse(SettingsGroup.PRIVACY in groups)
        assertFalse(SettingsGroup.UPDATES in groups)
    }

    @Test
    fun `an unpaired phone with nothing to connect shows no computer group`() {
        val groups = SettingsLayout.groups(
            showsComputer = false,
            paired = false,
            showsWorkspace = false,
            showsPrivacy = false,
            updatesAvailable = false,
        )
        assertEquals(
            listOf(SettingsGroup.APPEARANCE, SettingsGroup.NOTIFICATIONS, SettingsGroup.CHAT, SettingsGroup.HELP),
            groups,
        )
    }

    @Test
    fun `voice and captions need a pairing and a build with calls`() {
        val base = listOf(ChatRowKind.ACTIVITY, ChatRowKind.QUICK_REPLIES, ChatRowKind.DENSITY)
        assertEquals(base + listOf(ChatRowKind.VOICE, ChatRowKind.CAPTIONS), SettingsLayout.chatRows(paired = true, callsEnabled = true))
        assertEquals(base, SettingsLayout.chatRows(paired = true, callsEnabled = false))
        assertEquals(base, SettingsLayout.chatRows(paired = false, callsEnabled = true))
    }

    @Test
    fun `sign out removes one computer only when others are saved`() {
        assertFalse(SettingsLayout.removesOneOfMany(1))
        assertTrue(SettingsLayout.removesOneOfMany(2))
    }
}
