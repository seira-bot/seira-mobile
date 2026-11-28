package bot.seira.companion.ui.home.chats

import bot.seira.companion.core.NewBot
import java.util.UUID

internal object NewBotRules {
    fun canCreate(name: String, creating: Boolean): Boolean = name.isNotBlank() && !creating

    /** The Job text goes to the bot's description, the line the computer shows as what the bot does. */
    fun spec(name: String, job: String, retryKey: String): NewBot =
        NewBot(name = name.trim(), description = job.trim().ifEmpty { null }, retryKey = retryKey)

    /** Letters, digits and dashes only, 36 characters: inside the 16 to 128 range the computer accepts. */
    fun newRetryKey(): String = UUID.randomUUID().toString()
}
