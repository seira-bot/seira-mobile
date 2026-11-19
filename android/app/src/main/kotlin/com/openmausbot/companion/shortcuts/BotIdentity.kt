package com.openmausbot.companion.shortcuts

import com.openmausbot.companion.core.Bot
import com.openmausbot.companion.core.Chat
import com.openmausbot.companion.core.CompanionState
import com.openmausbot.companion.core.chatSummaries

/** What a shortcut or a conversation notification shows about one bot. */
data class BotIdentity(
    val botId: String,
    val threadId: String,
    val name: String,
    val color: String?,
    val avatarUrl: String?,
) {
    companion object {
        fun of(bot: Bot) = BotIdentity(bot.id, bot.threadId, bot.name, bot.color, bot.avatarUrl)
    }
}

/** The part of the roster that the launcher shortcuts and the Quick Settings tile mirror. */
data class ShortcutSnapshot(
    val recent: List<BotIdentity>,
    val knownBotIds: Set<String>,
    val chiefName: String?,
)

object RecentBots {
    const val LIMIT = 4

    fun snapshot(state: CompanionState, limit: Int = LIMIT): ShortcutSnapshot {
        val recent = state.chatSummaries()
            .filter { it.chat is Chat.BotChat }
            .sortedByDescending { it.lastActivity }
            .map { BotIdentity.of((it.chat as Chat.BotChat).bot) }
            .filter { it.name.isNotBlank() }
            .take(limit)
        return ShortcutSnapshot(
            recent = recent,
            knownBotIds = state.bots.map(Bot::id).toSet(),
            chiefName = state.talkChief()?.name?.takeIf { it.isNotBlank() },
        )
    }
}

/** The bot a Talk request calls: the visible chief of staff, preferring one outside any section. */
fun CompanionState.talkChief(): Bot? =
    unsectionedChief ?: bots.firstOrNull { it.chiefOfStaff == true && it.hidden != true }
