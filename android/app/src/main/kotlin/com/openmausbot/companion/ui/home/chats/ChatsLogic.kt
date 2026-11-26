package com.openmausbot.companion.ui.home.chats

import com.openmausbot.companion.core.Bot
import com.openmausbot.companion.core.CompanionState
import com.openmausbot.companion.core.NewBot
import com.openmausbot.companion.core.Room
import com.openmausbot.companion.ui.ChatUpdate
import com.openmausbot.companion.ui.UpdateKind
import java.util.UUID

/** The three chips above the list. */
internal enum class ChatsFilter { ALL, BOTS, GROUPS }

/** One heading of the Chats list and the chats under it. */
internal data class ChatsSection(
    val kind: Kind,
    /** The department name; null for [Kind.CHATS] and [Kind.GROUPS], whose titles are translated. */
    val name: String?,
    val bots: List<Bot>,
    val rooms: List<Room>,
) {
    enum class Kind { CHATS, DEPARTMENT, GROUPS }

    val key: String
        get() = when (kind) {
            Kind.CHATS -> "chats"
            Kind.DEPARTMENT -> "department-$name"
            Kind.GROUPS -> "groups"
        }
}

internal object ChatsLayout {
    /**
     * The sections in the order the list shows them: bots with no section, one
     * section per department, then groups. A department filter keeps only that
     * department. A section left empty by the chip is dropped, except Groups,
     * which keeps its "New group" action while the chip allows groups.
     */
    fun sections(state: CompanionState, filter: ChatsFilter, department: String?): List<ChatsSection> {
        val showBots = filter != ChatsFilter.GROUPS
        val showRooms = filter != ChatsFilter.BOTS
        val out = mutableListOf<ChatsSection>()

        if (department == null && showBots) {
            val bots = (listOfNotNull(state.unsectionedChief) + state.pinnedBots + state.unsectionedBots)
                .distinctBy(Bot::id)
            if (bots.isNotEmpty()) out += ChatsSection(ChatsSection.Kind.CHATS, null, bots, emptyList())
        }

        state.sidebarSections
            .filter { department == null || it.name == department }
            .forEach { section ->
                val bots = if (showBots) section.chiefs + section.bots else emptyList()
                val rooms = if (showRooms) section.channels else emptyList()
                if (bots.isNotEmpty() || rooms.isNotEmpty()) {
                    out += ChatsSection(ChatsSection.Kind.DEPARTMENT, section.name, bots, rooms)
                }
            }

        if (department == null && showRooms) {
            out += ChatsSection(
                ChatsSection.Kind.GROUPS,
                null,
                emptyList(),
                state.unsectionedChannels + state.botChats,
            )
        }
        return out
    }

    fun isEmpty(sections: List<ChatsSection>): Boolean =
        sections.all { it.bots.isEmpty() && it.rooms.isEmpty() }
}

internal object ChatsHeroRules {
    /** The newest chat that waits for the person, or null when nothing waits. */
    fun needsYou(updates: List<ChatUpdate>): ChatUpdate? =
        updates.firstOrNull { it.kind == UpdateKind.NEEDS_YOU }
}

internal object NewBotRules {
    fun canCreate(name: String, creating: Boolean): Boolean = name.isNotBlank() && !creating

    /** The Job text goes to the bot's description, the line the computer shows as what the bot does. */
    fun spec(name: String, job: String, retryKey: String): NewBot =
        NewBot(name = name.trim(), description = job.trim().ifEmpty { null }, retryKey = retryKey)

    /** Letters, digits and dashes only, 36 characters: inside the 16 to 128 range the computer accepts. */
    fun newRetryKey(): String = UUID.randomUUID().toString()
}
