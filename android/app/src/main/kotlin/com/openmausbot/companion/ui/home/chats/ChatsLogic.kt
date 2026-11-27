package com.openmausbot.companion.ui.home.chats

import com.openmausbot.companion.core.Bot
import com.openmausbot.companion.core.CompanionState
import com.openmausbot.companion.core.NewBot
import com.openmausbot.companion.core.Room
import com.openmausbot.companion.core.RosterRowStatus
import java.util.UUID

internal enum class HomeGroupKind { BOTS, DEPARTMENT, GROUPS }

/** One heading of the Chats tab and what it holds. */
internal data class HomeGroup(
    val kind: HomeGroupKind,
    /** The department name; null for [HomeGroupKind.BOTS] and [HomeGroupKind.GROUPS], whose titles are translated. */
    val name: String?,
    val bots: List<Bot>,
    val rooms: List<Room>,
) {
    /** The key of this group in the saved fold state and in the list. */
    val key: String
        get() = when (kind) {
            HomeGroupKind.BOTS -> "bots"
            HomeGroupKind.DEPARTMENT -> "department:$name"
            HomeGroupKind.GROUPS -> "groups"
        }
}

internal data class HomeLayout(
    /** The chief of staff on its own row; set only in Advanced mode, where the row stands above the groups. */
    val chief: Bot?,
    val groups: List<HomeGroup>,
    /** The department the drawer limits the list to, or null. */
    val department: String?,
) {
    /** True when there is no bot, no department and no group chat to show. */
    val isEmpty: Boolean
        get() = chief == null && groups.all { it.kind != HomeGroupKind.DEPARTMENT && it.bots.isEmpty() && it.rooms.isEmpty() }
}

internal object HomeLayoutRules {
    /**
     * The groups in the order the Chats tab shows them. Simple mode puts the
     * chief of staff first in the Bots group. Advanced mode gives it a row of
     * its own and shows the Bots group only when it holds a bot. Each
     * department follows in the order the computer lists them, empty ones
     * included, then the group chats. A department filter keeps only that
     * department.
     */
    fun layout(state: CompanionState, advanced: Boolean, department: String?): HomeLayout {
        val visible = state.bots.filter { it.hidden != true }
        val chief = state.unsectionedChief
        val loose = visible
            .filter { it.id != chief?.id && departmentOf(it) == null }
            .let { bots -> bots.filter { it.pinned == true } + bots.filter { it.pinned != true } }
        val names = (state.sidebarSections.map { it.name } + visible.mapNotNull(::departmentOf)).distinct()
        val departments = names.map { name -> departmentGroup(visible, name) }

        if (department != null) {
            return HomeLayout(chief = null, groups = departments.filter { it.name == department }, department = department)
        }

        val rooms = state.rooms
        val fleetEmpty = visible.isEmpty() && rooms.isEmpty() && names.isEmpty()
        val groups = buildList {
            if (advanced) {
                if (loose.isNotEmpty()) add(HomeGroup(HomeGroupKind.BOTS, null, loose, emptyList()))
            } else if (!fleetEmpty) {
                add(HomeGroup(HomeGroupKind.BOTS, null, listOfNotNull(chief) + loose, emptyList()))
            }
            addAll(departments)
            if (rooms.isNotEmpty() || !fleetEmpty) add(HomeGroup(HomeGroupKind.GROUPS, null, emptyList(), rooms))
        }
        return HomeLayout(chief = if (advanced) chief else null, groups = groups, department = null)
    }

    /** The name of the department a bot sits in, or null when it sits in none. */
    fun departmentOf(bot: Bot): String? = bot.section?.trim()?.takeIf(String::isNotEmpty)

    private fun departmentGroup(visible: List<Bot>, name: String): HomeGroup {
        val inside = visible.filter { departmentOf(it) == name }
        return HomeGroup(
            HomeGroupKind.DEPARTMENT,
            name,
            inside.filter { it.chiefOfStaff == true } + inside.filter { it.chiefOfStaff != true },
            emptyList(),
        )
    }

    /** Tiles of one row; the last row may hold fewer. */
    fun tileRows(bots: List<Bot>): List<List<Bot>> = bots.chunked(TILE_COLUMNS)

    const val TILE_COLUMNS = 4
}

/** The fold state: the keys of the groups the person folded. A new group starts open. */
internal object FoldRules {
    fun toggle(folded: Set<String>, key: String): Set<String> = if (key in folded) folded - key else folded + key

    fun isOpen(folded: Set<String>, key: String): Boolean = key !in folded

    /** A department that is gone no longer needs its saved fold. */
    fun prune(folded: Set<String>, liveKeys: Set<String>): Set<String> = folded.filterTo(mutableSetOf()) { it in liveKeys }
}

internal enum class HomeDot { NONE, WAITING, WORKING, PROBLEM }

internal object HomeDotRules {
    /** The one dot a bot wears: the person's turn first, then a failure, then work. */
    fun of(bot: Bot, status: RosterRowStatus): HomeDot = when {
        status == RosterRowStatus.WAITING_ON_YOU -> HomeDot.WAITING
        bot.activity in PROBLEM_ACTIVITIES -> HomeDot.PROBLEM
        status == RosterRowStatus.WORKING -> HomeDot.WORKING
        else -> HomeDot.NONE
    }

    /** A tile stays quiet unless the bot waits for the person or has a problem. */
    fun tileShows(dot: HomeDot): Boolean = dot == HomeDot.WAITING || dot == HomeDot.PROBLEM

    private val PROBLEM_ACTIVITIES = setOf("failed", "error", "blocked")
}

internal data class SectionCall(val name: String, val botIds: List<String>)

/** What a save in the "Add bots" sheet changes. */
internal data class DepartmentPlacement(val added: List<String>, val removed: List<String>) {
    val isEmpty: Boolean get() = added.isEmpty() && removed.isEmpty()

    /** One call that files the added bots and one that sends the removed bots back to Bots, each only when it has bots. */
    fun calls(department: String): List<SectionCall> = buildList {
        added.chunked(MAX_BOTS_PER_CALL).forEach { add(SectionCall(department, it)) }
        removed.chunked(MAX_BOTS_PER_CALL).forEach { add(SectionCall("", it)) }
    }

    private companion object {
        const val MAX_BOTS_PER_CALL = 100
    }
}

internal object DepartmentRules {
    /** The bots the "Add bots" sheet lists. The chief of staff stays out, so a save cannot give a department two chiefs. */
    fun selectable(state: CompanionState): List<Bot> =
        state.bots.filter { it.hidden != true && it.chiefOfStaff != true }

    /** The bots that start checked: the ones already in the department. */
    fun members(bots: List<Bot>, department: String): Set<String> =
        bots.filter { HomeLayoutRules.departmentOf(it) == department }.mapTo(linkedSetOf()) { it.id }

    fun diff(bots: List<Bot>, department: String, checked: Set<String>): DepartmentPlacement {
        val inside = members(bots, department)
        return DepartmentPlacement(
            added = bots.filter { it.id in checked && it.id !in inside }.map(Bot::id),
            removed = bots.filter { it.id !in checked && it.id in inside }.map(Bot::id),
        )
    }

    fun toggle(checked: Set<String>, id: String): Set<String> = if (id in checked) checked - id else checked + id

    fun canSave(placement: DepartmentPlacement, saving: Boolean): Boolean = !placement.isEmpty && !saving

    const val MAX_NAME_LENGTH = 60

    enum class NameProblem { EMPTY, EXISTS }

    /** Why a new department name is refused, or null when it is fine. The name is trimmed first. */
    fun nameProblem(name: String, existing: Collection<String>): NameProblem? {
        val trimmed = name.trim()
        return when {
            trimmed.isEmpty() -> NameProblem.EMPTY
            existing.any { it.trim() == trimmed } -> NameProblem.EXISTS
            else -> null
        }
    }

    fun clampName(name: String): String = name.take(MAX_NAME_LENGTH)
}

internal object NewBotRules {
    fun canCreate(name: String, creating: Boolean): Boolean = name.isNotBlank() && !creating

    /** The Job text goes to the bot's description, the line the computer shows as what the bot does. */
    fun spec(name: String, job: String, retryKey: String): NewBot =
        NewBot(name = name.trim(), description = job.trim().ifEmpty { null }, retryKey = retryKey)

    /** Letters, digits and dashes only, 36 characters: inside the 16 to 128 range the computer accepts. */
    fun newRetryKey(): String = UUID.randomUUID().toString()
}
