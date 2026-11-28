package bot.seira.companion.ui.profile

import bot.seira.companion.core.Bot

internal enum class BotAction { PIN, MARK_UNREAD, MARK_ALL_READ, MOVE, COPY_ID }

/** What the "Bot actions" group offers for one bot. */
internal object BotActionRules {
    /** Every action, in order. Copy needs a conversation to name. */
    fun visible(bot: Bot): List<BotAction> = buildList {
        add(BotAction.PIN)
        add(BotAction.MARK_UNREAD)
        add(BotAction.MARK_ALL_READ)
        add(BotAction.MOVE)
        if (bot.threadId.isNotBlank()) add(BotAction.COPY_ID)
    }

    /** The first row reads Unpin once the bot is pinned. */
    fun isPinned(bot: Bot): Boolean = bot.pinned == true

    /** Same department, with no regard to case; empty is General. */
    fun sameDepartment(current: String, other: String): Boolean =
        current.trim().equals(other.trim(), ignoreCase = true)
}
