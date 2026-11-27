package com.openmausbot.companion.core

/** The choices behind "Move to department". */
object BotDepartments {
    const val MAX_NAME_LENGTH = 60

    /**
     * The departments the computer lists, also when they have no bot, and the
     * departments of the bots, sorted without regard to case.
     */
    fun names(bots: List<Bot>, declared: List<String> = emptyList()): List<String> =
        (declared + bots.mapNotNull { it.section })
            .mapNotNull { it.trim().takeIf(String::isNotEmpty) }
            .distinctBy { it.lowercase() }
            .sortedBy { it.lowercase() }

    /** A new name with 1 to 60 characters, or null when it cannot be used. */
    fun cleanName(raw: String): String? =
        raw.trim().takeIf { it.length in 1..MAX_NAME_LENGTH }

    /** The department a bot is in now; empty means General. */
    fun current(bot: Bot): String = bot.section?.trim().orEmpty()
}
