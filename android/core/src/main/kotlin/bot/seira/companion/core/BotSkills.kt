package bot.seira.companion.core

import kotlinx.serialization.Serializable

/** A skill of a bot, by name. A phone only reads the list; changing skills stays on the computer. */
@Serializable
data class BotSkill(
    val name: String,
    val description: String = "",
    val enabled: Boolean = false,
    val editable: Boolean? = null,
    val source: String? = null,
    val license: String? = null,
    val compatibility: String? = null,
    val warnings: List<String> = emptyList(),
    /** private for the bot's own skill, library for one shared by the workspace. Absent on older computers. */
    val origin: String? = null,
)

@Serializable
internal data class BotSkillsResponse(val skills: List<BotSkill> = emptyList())
