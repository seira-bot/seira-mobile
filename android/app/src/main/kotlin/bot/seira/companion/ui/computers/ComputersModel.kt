package bot.seira.companion.ui.computers

import bot.seira.companion.core.AiUsageRules
import bot.seira.companion.core.Bot
import bot.seira.companion.core.Connection
import bot.seira.companion.ui.ComputerSwitchRules

internal data class ComputerEntry(
    val connection: Connection,
    val current: Boolean,
    val distinguishingAddress: String?,
)

internal enum class BotScreenState { WORKING, WAITING, IDLE }

internal data class BotScreenEntry(val bot: Bot, val state: BotScreenState)

internal object ComputersRules {
    const val CLOUD = "cloud"

    /** The AI usage row shows only when a page exists, a computer is paired and the computer has not hidden usage. */
    fun showsAiUsage(hasPage: Boolean, connectionId: String?, hiddenFor: String?): Boolean =
        hasPage && connectionId != null && !AiUsageRules.hidden(hiddenFor, connectionId)

    /** The current computer first, then the others in the order they were paired. */
    fun entries(computers: List<Connection>, activeId: String?): List<ComputerEntry> =
        ComputerSwitchRules.ordered(computers, activeId).map {
            ComputerEntry(
                connection = it,
                current = it.id == activeId,
                distinguishingAddress = ComputerSwitchRules.distinguishingAddress(it, computers),
            )
        }

    /** Bots that work on a cloud desktop, with what each is doing now. */
    fun botScreens(bots: List<Bot>): List<BotScreenEntry> =
        bots.filter { it.hidden != true && it.computer == CLOUD }.map { bot ->
            BotScreenEntry(
                bot = bot,
                state = when {
                    bot.busy == true -> BotScreenState.WORKING
                    bot.waitingOnTeammate == true -> BotScreenState.WAITING
                    else -> BotScreenState.IDLE
                },
            )
        }
}
