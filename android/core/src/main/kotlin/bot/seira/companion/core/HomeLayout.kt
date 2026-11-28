package bot.seira.companion.core

enum class HomeGroupKind { BOTS, DEPARTMENT, GROUPS }

/** One heading of the Chats tab and what it holds. */
data class HomeGroup(
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

data class HomeLayout(
    /** The chief of staff on its own row; set only in Advanced mode, where the row stands above the groups. */
    val chief: Bot?,
    val groups: List<HomeGroup>,
    /** The department the drawer limits the list to, or null. */
    val department: String?,
) {
    /** A department with no bot still shows, so only bots and group chats count. */
    val isEmpty: Boolean
        get() = chief == null && groups.all { it.kind != HomeGroupKind.DEPARTMENT && it.bots.isEmpty() && it.rooms.isEmpty() }
}

object HomeLayoutRules {
    const val TILE_COLUMNS = 4

    /**
     * The groups in the order the Chats tab shows them. Simple mode puts the
     * chief of staff first in the Bots group. Advanced mode gives it a row of
     * its own and shows the Bots group only when it holds a bot. The Bots group
     * also holds the pinned bots. Each department follows in the order the
     * computer lists them, empty ones included, with its bots and then its
     * channels. A section that holds only channels is not a department, so its
     * channels join the group chats with the bot chats, which come last. A department filter keeps only that department.
     */
    fun layout(state: CompanionState, advanced: Boolean, department: String?): HomeLayout {
        val declared = state.declaredSections.toSet()
        val departments = state.sidebarSections
            .filter { it.name in declared || it.chiefs.isNotEmpty() || it.bots.isNotEmpty() }
            .map { section ->
                HomeGroup(HomeGroupKind.DEPARTMENT, section.name, section.chiefs + section.bots, section.channels)
            }

        if (department != null) {
            val kept = departments.filter { it.name == department }
            return HomeLayout(chief = null, groups = kept, department = if (kept.isEmpty()) null else department)
        }

        val chief = state.unsectionedChief
        val loose = state.pinnedBots + state.unsectionedBots
        val shown = departments.mapTo(mutableSetOf()) { it.name }
        val rooms = state.rooms.filter { it.dm != true && it.section?.trim() !in shown } + state.botChats
        val fleetEmpty = state.bots.none { it.hidden != true } && state.rooms.isEmpty() && departments.isEmpty()
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

    /** Tiles of one row; the last row may hold fewer. */
    fun tileRows(bots: List<Bot>, columns: Int = TILE_COLUMNS): List<List<Bot>> =
        if (columns > 0) bots.chunked(columns) else emptyList()
}

enum class HomeDot { NONE, WAITING, WORKING, PROBLEM }

object HomeDotRules {
    /** The one dot a bot wears: the person's turn first, then a failure, then work. */
    fun of(bot: Bot, status: RosterRowStatus): HomeDot = when {
        status == RosterRowStatus.WAITING_ON_YOU -> HomeDot.WAITING
        bot.activity in PROBLEM_ACTIVITIES -> HomeDot.PROBLEM
        status == RosterRowStatus.WORKING -> HomeDot.WORKING
        else -> HomeDot.NONE
    }

    /** A tile stays quiet unless the bot waits for the person or has a problem. */
    fun tileShows(dot: HomeDot): Boolean = dot == HomeDot.WAITING || dot == HomeDot.PROBLEM

    /** How many bots wait for the person; a folded department header shows it. */
    fun waitingCount(dots: Collection<HomeDot>): Int = dots.count { it == HomeDot.WAITING }

    private val PROBLEM_ACTIVITIES = setOf("failed", "error", "blocked")
}

/** The fold state: the keys of the groups the person folded. A new group starts open. */
object FoldRules {
    fun toggle(folded: Set<String>, key: String): Set<String> = if (key in folded) folded - key else folded + key

    fun isOpen(folded: Set<String>, key: String): Boolean = key !in folded

    /** A department that is gone no longer needs its saved fold. */
    fun prune(folded: Set<String>, liveKeys: Set<String>): Set<String> = folded.filterTo(mutableSetOf()) { it in liveKeys }
}

data class SectionCall(val name: String, val botIds: List<String>)

/** What a save in the "Add bots" sheet changes. */
data class DepartmentPlacement(val added: List<String>, val removed: List<String>) {
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

/** What a save did: all calls worked, some worked, or none did. */
enum class PlacementOutcome {
    SAVED,
    PARTIAL,
    FAILED,
    ;

    companion object {
        fun of(succeeded: Int, total: Int): PlacementOutcome = when {
            succeeded >= total -> SAVED
            succeeded > 0 -> PARTIAL
            else -> FAILED
        }
    }
}

/**
 * The person's own toggles in the "Add bots" sheet. It holds only what the
 * person changed, so a change made on the computer while the sheet is open
 * stays in place.
 */
data class DepartmentEdit(val added: Set<String> = emptySet(), val removed: Set<String> = emptySet())

object DepartmentRules {
    const val MAX_NAME_LENGTH = 60

    enum class NameProblem { EMPTY, EXISTS }

    /** The bots the "Add bots" sheet lists. The chief of staff stays out, so a save cannot give a department two chiefs. */
    fun selectable(state: CompanionState): List<Bot> =
        state.bots.filter { it.hidden != true && it.chiefOfStaff != true }

    /** The bots that sit in the department now. */
    fun members(bots: List<Bot>, department: String): Set<String> =
        bots.filter { HomeLayoutRules.departmentOf(it) == department }.mapTo(linkedSetOf()) { it.id }

    /** Whether a bot shows a check mark: its live membership with the person's toggles on top. */
    fun isChecked(members: Set<String>, edit: DepartmentEdit, id: String): Boolean =
        id in edit.added || (id in members && id !in edit.removed)

    /** The edit after the person taps a bot. A toggle that matches the live membership leaves no trace. */
    fun toggle(members: Set<String>, edit: DepartmentEdit, id: String): DepartmentEdit {
        val target = !isChecked(members, edit, id)
        val added = edit.added - id
        val removed = edit.removed - id
        return when {
            target && id !in members -> DepartmentEdit(added + id, removed)
            !target && id in members -> DepartmentEdit(added, removed + id)
            else -> DepartmentEdit(added, removed)
        }
    }

    /** The calls a save needs, from the live membership at the moment of the save. */
    fun placement(bots: List<Bot>, department: String, edit: DepartmentEdit): DepartmentPlacement {
        val inside = members(bots, department)
        return DepartmentPlacement(
            added = bots.filter { it.id in edit.added && it.id !in inside }.map(Bot::id),
            removed = bots.filter { it.id in edit.removed && it.id in inside }.map(Bot::id),
        )
    }

    fun canSave(placement: DepartmentPlacement, saving: Boolean): Boolean = !placement.isEmpty && !saving

    /** Why a new department name is refused, or null when it is fine. The name is trimmed first and case does not count. */
    fun nameProblem(name: String, existing: Collection<String>): NameProblem? {
        val trimmed = name.trim()
        return when {
            trimmed.isEmpty() -> NameProblem.EMPTY
            existing.any { it.trim().equals(trimmed, ignoreCase = true) } -> NameProblem.EXISTS
            else -> null
        }
    }

    fun clampName(name: String): String = name.take(MAX_NAME_LENGTH)
}
