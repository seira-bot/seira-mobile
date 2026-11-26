package com.openmausbot.companion.ui.profile

import com.openmausbot.companion.core.ActivityRow
import com.openmausbot.companion.core.Bot
import com.openmausbot.companion.core.BotData
import com.openmausbot.companion.core.BotProfilePatch
import com.openmausbot.companion.core.DataCard
import com.openmausbot.companion.core.DataSheet
import com.openmausbot.companion.core.DataTable
import com.openmausbot.companion.core.ModelSelection
import com.openmausbot.companion.core.ReplyStyle
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProfileModelTest {
    private fun bot(computer: String? = null) = Bot(
        id = "b1",
        threadId = "t1",
        name = "Scout",
        title = "Research",
        description = "Finds things",
        notifications = true,
        color = "green",
        unread = false,
        modelSelection = ModelSelection("i", "m"),
        createdAt = 0.0,
        computer = computer,
    )

    private fun row(at: String) = ActivityRow(at = at, threadId = "t", tool = "Bash", label = "Ran", outcome = "ran")

    @Test
    fun `tab keys round trip and unknown keys fall back to activity`() {
        ProfileTab.entries.forEach { assertEquals(it, ProfileTab.fromKey(it.key)) }
        assertEquals(ProfileTab.ACTIVITY, ProfileTab.fromKey("nope"))
        assertEquals(ProfileTab.ACTIVITY, ProfileTab.fromKey(null))
    }

    @Test
    fun `an untouched draft makes an empty patch`() {
        val base = ProfileDraft.of(bot())
        assertEquals(BotProfilePatch(), ProfileDraftRules.patch(base, base))
        assertFalse(ProfileDraftRules.isDirty(base, base))
    }

    @Test
    fun `the patch carries only changed fields, trimmed`() {
        val base = ProfileDraft.of(bot())
        val draft = base.copy(name = "  Scout 2 ", soul = " Be brief ", replyStyle = ReplyStyle.CONVERSATIONAL)
        val patch = ProfileDraftRules.patch(draft, base)
        assertEquals("Scout 2", patch.name)
        assertNull(patch.title)
        assertNull(patch.description)
        assertEquals("Be brief", patch.soul)
        assertEquals(ReplyStyle.CONVERSATIONAL, patch.replyStyle)
    }

    @Test
    fun `whitespace only edits are not dirty`() {
        val base = ProfileDraft.of(bot())
        assertFalse(ProfileDraftRules.isDirty(base.copy(title = "  Research "), base))
    }

    @Test
    fun `save needs a name, a change and no save in flight`() {
        val base = ProfileDraft.of(bot())
        assertTrue(ProfileDraftRules.canSave(base.copy(title = "Lead"), base, busy = false))
        assertFalse(ProfileDraftRules.canSave(base.copy(title = "Lead"), base, busy = true))
        assertFalse(ProfileDraftRules.canSave(base.copy(name = " "), base, busy = false))
        assertFalse(ProfileDraftRules.canSave(base, base, busy = false))
    }

    @Test
    fun `today keeps only rows of the day, newest first`() {
        val rows = listOf(
            row("2026-11-26T08:00:00Z"),
            row("2026-11-25T23:59:00Z"),
            row("2026-11-26T10:00:00Z"),
            row("not a time"),
        )
        val today = ProfileActivityRules.today(rows, LocalDate.of(2026, 11, 26), ZoneOffset.UTC)
        assertEquals(listOf("2026-11-26T10:00:00Z", "2026-11-26T08:00:00Z"), today.map { it.at })
    }

    @Test
    fun `a cloud computer is not driven from the phone`() {
        assertFalse(ProfileComputerRules.mayTakeControl(bot("cloud")))
        assertTrue(ProfileComputerRules.mayTakeControl(bot("mac")))
        assertTrue(ProfileComputerRules.mayTakeControl(bot(null)))
    }

    private fun card(id: String, kind: String, status: String = "ready", rows: Long? = null, text: String? = null) =
        DataCard(id = id, kind = kind, title = "Card $id", rowCount = rows, text = text, status = status)

    @Test
    fun `library lists tables then finished table and text cards`() {
        val data = BotData(
            sheet = DataSheet(
                cards = listOf(
                    card("c1", "table", rows = 7),
                    card("c2", "text", text = "Hello"),
                    card("c3", "chart"),
                    card("c4", "table", status = "running"),
                ),
            ),
            tables = listOf(DataTable(name = "sales", rowCount = 12)),
        )
        val items = ProfileLibraryRules.items(data)
        assertEquals(listOf("table:sales", "card:c1", "card:c2"), items.map { it.id })
        assertEquals("sales", items[0].table)
        assertEquals("c1", items[1].cardId)
        assertEquals(7L, items[1].count)
        assertEquals("Hello", items[2].text)
        assertNull(items[2].cardId)
    }

    @Test
    fun `library page request and cells`() {
        val item = LibraryItem("table:sales", "sales", 12, "sales", null, null)
        val request = ProfileLibraryRules.request(item, 50)
        assertEquals("sales", request.table)
        assertEquals(50, request.offset)
        assertEquals(ProfileLibraryRules.PAGE_SIZE, request.limit)
        assertEquals("", ProfileLibraryRules.cell(JsonNull))
        assertEquals("42", ProfileLibraryRules.cell(JsonPrimitive(42)))
        assertTrue(ProfileLibraryRules.hasMore(50, 120))
        assertFalse(ProfileLibraryRules.hasMore(120, 120))
    }
}
