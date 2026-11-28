package bot.seira.companion.core

import kotlinx.serialization.Serializable

enum class TeamMemoryKind(val wire: String) {
    PERSON("person"),
    PLACE("place"),
    DECISION("decision"),
    TERM("term"),
}

@Serializable
data class TeamMemorySource(
    val botId: String = "",
    val botName: String = "",
    val threadId: String = "",
    /** Epoch milliseconds. */
    val at: Double = 0.0,
    val messageId: String? = null,
    val turnId: String? = null,
    val quote: String? = null,
)

/**
 * One fact every bot in a section shares. A proposed entry waits for the
 * person; an accepted one rides each bot's prompt. [revision] must go back
 * with every edit or removal, or the computer refuses it.
 */
@Serializable
data class TeamMemoryEntry(
    val id: String,
    val kind: String,
    val name: String,
    val detail: String = "",
    val aliases: List<String> = emptyList(),
    /** accepted, proposed, superseded or deleted. */
    val status: String,
    val source: TeamMemorySource = TeamMemorySource(),
    val updatedAt: Double = 0.0,
    val revision: String? = null,
    val pinned: Boolean? = null,
    val key: String? = null,
)

@Serializable
data class TeamMemoryPage(
    val section: String = "",
    val label: String = "",
    val entries: List<TeamMemoryEntry> = emptyList(),
    val loadedIds: List<String> = emptyList(),
)

/** What a change answers with: the whole page of entries after it. */
@Serializable
data class TeamMemoryEdit(
    val entries: List<TeamMemoryEntry> = emptyList(),
    val loadedIds: List<String> = emptyList(),
)
