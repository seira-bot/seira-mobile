package bot.seira.companion.ui.team

import bot.seira.companion.core.Bot
import bot.seira.companion.core.CompanionState
import bot.seira.companion.core.TeamMap
import bot.seira.companion.core.TeamMemoryEntry

/** One department of the team map. [name] is null for the group of bots that belong to none. */
internal data class TeamGroup(val name: String?, val bots: List<Bot>)

internal data class TeamLayout(val chief: Bot?, val groups: List<TeamGroup>) {
    val isEmpty: Boolean get() = chief == null && groups.isEmpty()
}

internal enum class TeamStatus { WORKING, WAITING }

internal object TeamMapRules {
    /**
     * The Chief of Staff first, then each department with its own chiefs ahead
     * of its bots, then the bots that belong to no department. A department the
     * computer lists shows even with no bot. A pinned bot stays in its department,
     * and the Chief of Staff appears only once.
     */
    fun layout(state: CompanionState): TeamLayout {
        val visible = state.bots.filter { it.hidden != true }
        val chief = state.unsectionedChief ?: visible.firstOrNull { it.chiefOfStaff == true }
        val members = visible.filter { it.id != chief?.id }
        val names = (state.sidebarSections.map { it.name } + members.mapNotNull { departmentOf(it) }).distinct()
        val sections = names
            .map { name ->
                val inside = members.filter { departmentOf(it) == name }
                TeamGroup(name, inside.filter { it.chiefOfStaff == true } + inside.filter { it.chiefOfStaff != true })
            }
            .filter { it.bots.isNotEmpty() || it.name in state.declaredSections }
        val general = members.filter { it.chiefOfStaff != true && departmentOf(it) == null }
        val groups = sections + if (general.isEmpty()) emptyList() else listOf(TeamGroup(null, general))
        return TeamLayout(chief = chief, groups = groups)
    }

    private fun departmentOf(bot: Bot): String? = bot.section?.trim()?.takeIf(String::isNotEmpty)

    fun status(map: TeamMap?, botId: String): TeamStatus? = when {
        map == null -> null
        map.running.any { it.targetBotId == botId } -> TeamStatus.WORKING
        map.queued.any { it.targetBotId == botId } -> TeamStatus.WAITING
        else -> null
    }
}

internal object TeamMemoryRules {
    const val NAME_LIMIT = 120
    const val DETAIL_LIMIT = 600

    /** The department names the notes are kept under, with the general one first. */
    fun sections(state: CompanionState): List<String> = listOf("") + state.sidebarSections.map { it.name }

    /** The first line is the note; anything after it is its detail. A blank note is nothing. */
    fun split(text: String): Pair<String, String>? {
        val lines = text.trim().lines().map { it.trim() }.filter { it.isNotEmpty() }
        val first = lines.firstOrNull() ?: return null
        val name = first.take(NAME_LIMIT).trim()
        val rest = (listOf(first.drop(NAME_LIMIT).trim()) + lines.drop(1)).filter { it.isNotEmpty() }
        return name to rest.joinToString("\n").take(DETAIL_LIMIT)
    }

    fun accepted(entries: List<TeamMemoryEntry>): List<TeamMemoryEntry> =
        entries.filter { it.status == "accepted" }

    fun proposed(entries: List<TeamMemoryEntry>): List<TeamMemoryEntry> =
        entries.filter { it.status == "proposed" }

    /** Whether [entry] changed under the person: it is gone, or it carries another revision. */
    fun changedSince(entry: TeamMemoryEntry, latest: List<TeamMemoryEntry>): Boolean {
        val now = latest.firstOrNull { it.id == entry.id } ?: return true
        return now.revision != entry.revision
    }
}
