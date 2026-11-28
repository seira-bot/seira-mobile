package bot.seira.companion.ui.search

import bot.seira.companion.core.Bot
import bot.seira.companion.core.Room
import bot.seira.companion.core.Routine
import bot.seira.companion.core.SearchHit
import bot.seira.companion.ui.SearchPolicy

/** The chips above the results. */
internal enum class SearchFilter { ALL, MESSAGES, BOTS, GROUPS, ROUTINES }

/** What the page shows under the field when nothing lists. */
internal enum class SearchEmpty { PROMPT, NONE, FAILED }

internal data class SearchResults(
    val bots: List<Bot> = emptyList(),
    val groups: List<Room> = emptyList(),
    val routines: List<Routine> = emptyList(),
    val messages: List<SearchHit> = emptyList(),
) {
    fun isEmpty(): Boolean = bots.isEmpty() && groups.isEmpty() && routines.isEmpty() && messages.isEmpty()
}

internal object SearchRules {
    fun needsRemote(query: String): Boolean = SearchPolicy.decide(query) is SearchPolicy.Decision.Remote

    fun bots(all: List<Bot>, query: String): List<Bot> {
        val needle = query.trim()
        if (needle.isEmpty()) return emptyList()
        return all.filter { bot ->
            bot.hidden != true &&
                (
                    bot.name.contains(needle, ignoreCase = true) ||
                        bot.title.contains(needle, ignoreCase = true) ||
                        bot.description.contains(needle, ignoreCase = true)
                    )
        }
    }

    fun groups(all: List<Room>, query: String): List<Room> {
        val needle = query.trim()
        if (needle.isEmpty()) return emptyList()
        return all.filter { it.name.contains(needle, ignoreCase = true) }
    }

    fun routines(all: List<Routine>, query: String): List<Routine> {
        val needle = query.trim()
        if (needle.isEmpty()) return emptyList()
        return all.filter { it.name.contains(needle, ignoreCase = true) || it.prompt.contains(needle, ignoreCase = true) }
    }

    /** The results a chip lets through. */
    fun visible(results: SearchResults, filter: SearchFilter): SearchResults = SearchResults(
        bots = if (filter == SearchFilter.ALL || filter == SearchFilter.BOTS) results.bots else emptyList(),
        groups = if (filter == SearchFilter.ALL || filter == SearchFilter.GROUPS) results.groups else emptyList(),
        routines = if (filter == SearchFilter.ALL || filter == SearchFilter.ROUTINES) results.routines else emptyList(),
        messages = if (filter == SearchFilter.ALL || filter == SearchFilter.MESSAGES) results.messages else emptyList(),
    )

    /**
     * The line to show when nothing lists, or null when results list or a
     * search is still running. A query too short for the message search shows
     * the prompt.
     */
    fun empty(query: String, visible: SearchResults, searching: Boolean, failed: Boolean): SearchEmpty? {
        if (!visible.isEmpty()) return null
        if (query.trim().isEmpty()) return SearchEmpty.PROMPT
        if (searching) return null
        if (failed) return SearchEmpty.FAILED
        return if (needsRemote(query)) SearchEmpty.NONE else SearchEmpty.PROMPT
    }

    /** A failure shows when the search returned nothing and the shared error changed during it. */
    fun failed(before: String?, after: String?, hits: List<SearchHit>): Boolean =
        hits.isEmpty() && after != null && after != before
}
