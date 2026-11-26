package com.openmausbot.companion.ui.team

import com.openmausbot.companion.core.Bot
import com.openmausbot.companion.core.CompanionState
import com.openmausbot.companion.core.TeamMap
import com.openmausbot.companion.core.TeamMemoryEntry

/** One department of the team map. [name] is null for the group of bots that belong to none. */
internal data class TeamGroup(val name: String?, val bots: List<Bot>)

internal data class TeamLayout(val chief: Bot?, val groups: List<TeamGroup>) {
    val isEmpty: Boolean get() = chief == null && groups.isEmpty()
}

internal enum class TeamStatus { WORKING, WAITING }

internal object TeamMapRules {
    /**
     * The Chief of Staff first, then each department with its own chiefs ahead
     * of its bots, then the bots that belong to no department.
     */
    fun layout(state: CompanionState): TeamLayout {
        val chief = state.unsectionedChief ?: state.bots.firstOrNull { it.hidden != true && it.chiefOfStaff == true }
        val sections = state.sidebarSections
            .map { TeamGroup(it.name, it.chiefs + it.bots) }
            .filter { it.bots.isNotEmpty() }
        val general = state.unsectionedBots
        val groups = sections + if (general.isEmpty()) emptyList() else listOf(TeamGroup(null, general))
        return TeamLayout(chief = chief, groups = groups)
    }

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
