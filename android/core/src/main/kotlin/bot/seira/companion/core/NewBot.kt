package bot.seira.companion.core

/** How a bot's replies are shaped. Absent means [DEFAULT]. */
enum class ReplyStyle(val wire: String) {
    DEFAULT("default"),
    CONVERSATIONAL("conversational"),
}

/**
 * What a phone may say when it makes a bot: who the bot is and how it looks.
 * A field left null is left to the computer, which picks a name, colour and
 * look when none is given. [retryKey] makes a repeated send return the same bot.
 */
data class NewBot(
    val name: String? = null,
    val title: String? = null,
    val description: String? = null,
    val soul: String? = null,
    val replyStyle: ReplyStyle? = null,
    val color: String? = null,
    val mascotBody: String? = null,
    val mascotAccessories: List<String>? = null,
    val mascotFaceColor: String? = null,
    val mascotExpression: String? = null,
    val retryKey: String? = null,
) {
    val isEmpty: Boolean
        get() = this == NewBot()
}
