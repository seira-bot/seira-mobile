package com.openmausbot.companion.ui.home.chats

import com.openmausbot.companion.core.Bot
import com.openmausbot.companion.core.Chat
import com.openmausbot.companion.core.CompanionState
import com.openmausbot.companion.core.GroupResponder
import com.openmausbot.companion.core.ModelSelection
import com.openmausbot.companion.core.Room
import com.openmausbot.companion.ui.ChatUpdate
import com.openmausbot.companion.ui.UpdateKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChatsLogicTest {
    private val state = CompanionState(
        bots = listOf(
            bot("chief").copy(chiefOfStaff = true),
            bot("plain"),
            bot("pinned").copy(pinned = true),
            bot("ops-bot").copy(section = "Ops"),
            bot("hidden").copy(hidden = true),
        ),
        rooms = listOf(
            room("group"),
            room("direct").copy(dm = true),
            room("ops-room").copy(section = "Ops"),
        ),
    )

    @Test
    fun `the all chip lists chats, each department and then groups`() {
        val sections = ChatsLayout.sections(state, ChatsFilter.ALL, department = null)
        assertEquals(
            listOf(ChatsSection.Kind.CHATS, ChatsSection.Kind.DEPARTMENT, ChatsSection.Kind.GROUPS),
            sections.map { it.kind },
        )
        assertEquals(listOf("chief", "pinned", "plain"), sections[0].bots.map(Bot::id))
        assertEquals("Ops", sections[1].name)
        assertEquals(listOf("ops-bot"), sections[1].bots.map(Bot::id))
        assertEquals(listOf("ops-room"), sections[1].rooms.map(Room::id))
        assertEquals(listOf("group", "direct"), sections[2].rooms.map(Room::id))
    }

    @Test
    fun `the bots chip leaves groups out`() {
        val sections = ChatsLayout.sections(state, ChatsFilter.BOTS, department = null)
        assertFalse(sections.any { it.kind == ChatsSection.Kind.GROUPS })
        assertTrue(sections.all { it.rooms.isEmpty() })
    }

    @Test
    fun `the groups chip keeps the groups section and drops bots`() {
        val sections = ChatsLayout.sections(state, ChatsFilter.GROUPS, department = null)
        assertTrue(sections.all { it.bots.isEmpty() })
        assertEquals(ChatsSection.Kind.GROUPS, sections.last().kind)
    }

    @Test
    fun `a department filter keeps only that department`() {
        val sections = ChatsLayout.sections(state, ChatsFilter.ALL, department = "Ops")
        assertEquals(listOf("department-Ops"), sections.map(ChatsSection::key))
        assertTrue(ChatsLayout.sections(state, ChatsFilter.ALL, department = "Nowhere").isEmpty())
    }

    @Test
    fun `a fleet with nothing to list is empty`() {
        val sections = ChatsLayout.sections(CompanionState(), ChatsFilter.ALL, department = null)
        assertTrue(ChatsLayout.isEmpty(sections))
        assertFalse(ChatsLayout.isEmpty(ChatsLayout.sections(state, ChatsFilter.ALL, department = null)))
    }

    @Test
    fun `the hero shows the newest update that needs the person`() {
        val bot = bot("plain")
        val chat = Chat.BotChat(bot)
        val updates = listOf(
            ChatUpdate(chat, UpdateKind.WORKING, "Working"),
            ChatUpdate(chat, UpdateKind.NEEDS_YOU, "Pick one"),
            ChatUpdate(chat, UpdateKind.NEEDS_YOU, "Older"),
        )
        assertEquals("Pick one", ChatsHeroRules.needsYou(updates)?.line)
        assertNull(ChatsHeroRules.needsYou(updates.take(1)))
    }

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

    private fun bot(id: String) = Bot(
        id = id,
        threadId = "thread-$id",
        name = id,
        title = "",
        description = "",
        notifications = true,
        color = "blue",
        unread = false,
        modelSelection = ModelSelection("instance", "model"),
        createdAt = 1.0,
    )

    private fun room(id: String) = Room(
        id = id,
        threadId = "thread-$id",
        name = id,
        memberIds = emptyList(),
        defaultResponder = GroupResponder("auto"),
        bulletin = "",
        unread = false,
        createdAt = 0.0,
    )
}
