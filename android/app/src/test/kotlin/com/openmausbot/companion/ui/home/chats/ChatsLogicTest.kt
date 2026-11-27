package com.openmausbot.companion.ui.home.chats

import com.openmausbot.companion.core.Bot
import com.openmausbot.companion.core.CompanionState
import com.openmausbot.companion.core.GroupResponder
import com.openmausbot.companion.core.ModelSelection
import com.openmausbot.companion.core.Room
import com.openmausbot.companion.core.RosterRowStatus
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
            bot("ops-chief").copy(section = "Ops", chiefOfStaff = true),
            bot("hidden").copy(hidden = true),
        ),
        rooms = listOf(room("group"), room("direct").copy(dm = true)),
        declaredSections = listOf("Ops", "Empty"),
    )

    @Test
    fun `simple mode shows bots, each department in order, then group chats`() {
        val layout = HomeLayoutRules.layout(state, advanced = false, department = null)
        assertEquals(
            listOf("bots", "department:Ops", "department:Empty", "groups"),
            layout.groups.map(HomeGroup::key),
        )
        assertNull(layout.chief)
    }

    @Test
    fun `simple mode puts Seira first and then the bots with no department`() {
        val bots = HomeLayoutRules.layout(state, advanced = false, department = null).groups.first()
        assertEquals(listOf("chief", "pinned", "plain"), bots.bots.map(Bot::id))
    }

    @Test
    fun `a department with no bot still shows`() {
        val empty = HomeLayoutRules.layout(state, advanced = false, department = null).groups.first { it.name == "Empty" }
        assertTrue(empty.bots.isEmpty())
    }

    @Test
    fun `a department lists its chief first`() {
        val ops = HomeLayoutRules.layout(state, advanced = false, department = null).groups.first { it.name == "Ops" }
        assertEquals(listOf("ops-chief", "ops-bot"), ops.bots.map(Bot::id))
    }

    @Test
    fun `a department a bot names shows even when the computer did not list it`() {
        val other = state.copy(bots = state.bots + bot("extra").copy(section = "Sales"))
        val names = HomeLayoutRules.layout(other, advanced = false, department = null).groups.mapNotNull(HomeGroup::name)
        assertEquals(listOf("Ops", "Empty", "Sales"), names)
    }

    @Test
    fun `group chats hold every group chat`() {
        val groups = HomeLayoutRules.layout(state, advanced = false, department = null).groups.last()
        assertEquals(HomeGroupKind.GROUPS, groups.kind)
        assertEquals(listOf("group", "direct"), groups.rooms.map(Room::id))
    }

    @Test
    fun `advanced mode gives the chief its own row and keeps the other bots in Bots`() {
        val layout = HomeLayoutRules.layout(state, advanced = true, department = null)
        assertEquals("chief", layout.chief?.id)
        assertEquals(listOf("pinned", "plain"), layout.groups.first().bots.map(Bot::id))
    }

    @Test
    fun `advanced mode leaves out an empty Bots group`() {
        val onlyChief = CompanionState(bots = listOf(bot("chief").copy(chiefOfStaff = true)))
        val layout = HomeLayoutRules.layout(onlyChief, advanced = true, department = null)
        assertTrue(layout.groups.none { it.kind == HomeGroupKind.BOTS })
        assertEquals("chief", layout.chief?.id)
    }

    @Test
    fun `a department filter keeps only that department`() {
        val layout = HomeLayoutRules.layout(state, advanced = false, department = "Ops")
        assertEquals(listOf("department:Ops"), layout.groups.map(HomeGroup::key))
        assertEquals("Ops", layout.department)
        assertNull(layout.chief)
        assertTrue(HomeLayoutRules.layout(state, advanced = false, department = "Nowhere").groups.isEmpty())
    }

    @Test
    fun `a fleet with nothing to list is empty`() {
        assertTrue(HomeLayoutRules.layout(CompanionState(), advanced = false, department = null).isEmpty)
        assertTrue(HomeLayoutRules.layout(CompanionState(), advanced = true, department = null).isEmpty)
        assertFalse(HomeLayoutRules.layout(state, advanced = false, department = null).isEmpty)
    }

    @Test
    fun `tiles come four to a row and the last row may be short`() {
        val rows = HomeLayoutRules.tileRows(List(9) { bot("b$it") })
        assertEquals(listOf(4, 4, 1), rows.map { it.size })
    }

    @Test
    fun `a group key names its kind and department`() {
        assertEquals("bots", HomeGroup(HomeGroupKind.BOTS, null, emptyList(), emptyList()).key)
        assertEquals("department:Ops", HomeGroup(HomeGroupKind.DEPARTMENT, "Ops", emptyList(), emptyList()).key)
        assertEquals("groups", HomeGroup(HomeGroupKind.GROUPS, null, emptyList(), emptyList()).key)
    }

    @Test
    fun `a new group starts open and a toggle folds then opens it`() {
        val none = emptySet<String>()
        assertTrue(FoldRules.isOpen(none, "department:Ops"))
        val folded = FoldRules.toggle(none, "department:Ops")
        assertFalse(FoldRules.isOpen(folded, "department:Ops"))
        assertTrue(FoldRules.isOpen(FoldRules.toggle(folded, "department:Ops"), "department:Ops"))
    }

    @Test
    fun `folding one department leaves the others open`() {
        val folded = FoldRules.toggle(emptySet(), "department:Ops")
        assertTrue(FoldRules.isOpen(folded, "department:Sales"))
    }

    @Test
    fun `pruning drops the fold of a department that is gone`() {
        val folded = setOf("department:Ops", "department:Gone")
        assertEquals(setOf("department:Ops"), FoldRules.prune(folded, setOf("department:Ops", "bots")))
    }

    @Test
    fun `the dot shows the person's turn first, then a problem, then work`() {
        val plain = bot("plain")
        assertEquals(HomeDot.WAITING, HomeDotRules.of(plain, RosterRowStatus.WAITING_ON_YOU))
        assertEquals(HomeDot.PROBLEM, HomeDotRules.of(plain.copy(activity = "failed"), RosterRowStatus.IDLE))
        assertEquals(HomeDot.WORKING, HomeDotRules.of(plain, RosterRowStatus.WORKING))
        assertEquals(HomeDot.NONE, HomeDotRules.of(plain, RosterRowStatus.IDLE))
    }

    @Test
    fun `a tile shows a dot only for a wait or a problem`() {
        assertTrue(HomeDotRules.tileShows(HomeDot.WAITING))
        assertTrue(HomeDotRules.tileShows(HomeDot.PROBLEM))
        assertFalse(HomeDotRules.tileShows(HomeDot.WORKING))
        assertFalse(HomeDotRules.tileShows(HomeDot.NONE))
    }

    @Test
    fun `the Add bots list leaves out hidden bots and the chief`() {
        assertEquals(
            listOf("plain", "pinned", "ops-bot"),
            DepartmentRules.selectable(state).map(Bot::id),
        )
    }

    @Test
    fun `the bots of a department start checked`() {
        val bots = DepartmentRules.selectable(state)
        assertEquals(setOf("ops-bot"), DepartmentRules.members(bots, "Ops"))
    }

    @Test
    fun `a save with nothing changed has no calls`() {
        val bots = DepartmentRules.selectable(state)
        val placement = DepartmentRules.diff(bots, "Ops", DepartmentRules.members(bots, "Ops"))
        assertTrue(placement.isEmpty)
        assertTrue(placement.calls("Ops").isEmpty())
        assertFalse(DepartmentRules.canSave(placement, saving = false))
    }

    @Test
    fun `the diff names the bots added and the bots removed`() {
        val bots = DepartmentRules.selectable(state)
        val checked = DepartmentRules.toggle(DepartmentRules.toggle(DepartmentRules.members(bots, "Ops"), "plain"), "ops-bot")
        val placement = DepartmentRules.diff(bots, "Ops", checked)
        assertEquals(listOf("plain"), placement.added)
        assertEquals(listOf("ops-bot"), placement.removed)
    }

    @Test
    fun `a save sends one call to add and one call with an empty name to remove`() {
        val placement = DepartmentPlacement(added = listOf("a", "b"), removed = listOf("c"))
        assertEquals(
            listOf(SectionCall("Ops", listOf("a", "b")), SectionCall("", listOf("c"))),
            placement.calls("Ops"),
        )
    }

    @Test
    fun `a call is left out when its list is empty`() {
        assertEquals(listOf(SectionCall("Ops", listOf("a"))), DepartmentPlacement(listOf("a"), emptyList()).calls("Ops"))
        assertEquals(listOf(SectionCall("", listOf("c"))), DepartmentPlacement(emptyList(), listOf("c")).calls("Ops"))
    }

    @Test
    fun `save waits while a save runs`() {
        val placement = DepartmentPlacement(listOf("a"), emptyList())
        assertTrue(DepartmentRules.canSave(placement, saving = false))
        assertFalse(DepartmentRules.canSave(placement, saving = true))
    }

    @Test
    fun `a department name is trimmed and refused when empty or already used`() {
        val existing = listOf("Ops", "Sales")
        assertEquals(DepartmentRules.NameProblem.EMPTY, DepartmentRules.nameProblem("   ", existing))
        assertEquals(DepartmentRules.NameProblem.EXISTS, DepartmentRules.nameProblem(" Ops ", existing))
        assertNull(DepartmentRules.nameProblem(" Marketing ", existing))
    }

    @Test
    fun `a department name stops at sixty characters`() {
        assertEquals(60, DepartmentRules.clampName("x".repeat(80)).length)
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
