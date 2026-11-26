package com.openmausbot.companion.ui.computers

import com.openmausbot.companion.core.Bot
import com.openmausbot.companion.core.Connection
import com.openmausbot.companion.ui.ComputerSwitchRules

internal data class ComputerEntry(
    val connection: Connection,
    val current: Boolean,
    val distinguishingAddress: String?,
)

internal enum class BotScreenState { WORKING, WAITING, IDLE }

internal data class BotScreenEntry(val bot: Bot, val state: BotScreenState)

internal object ComputersRules {
    const val CLOUD = "cloud"

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
