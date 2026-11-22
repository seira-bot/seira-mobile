package com.openmausbot.companion.core

/**
 * The failed-turn row a thread gets when the bot's cloud computer is not set
 * up. A server that predates the fix fails every message with the same row, so
 * the phone words it itself and keeps one notice per run of such failures.
 */
object CloudComputerNotice {
    const val TEXT = "This bot's cloud computer isn't set up."

    private const val LEGACY_STATE = "cc-needs-key"
    private val LEGACY_TEXT = Regex(
        """connect your \S+ account|cloud desktops from \S+, a paid service""",
        RegexOption.IGNORE_CASE,
    )

    fun matches(tool: ToolActivity): Boolean {
        val cause = failedTurnCause(tool.name) ?: return false
        return tool.place?.state == LEGACY_STATE || LEGACY_TEXT.containsMatchIn(cause)
    }

    /** The same words for a notification body that carries the old error. */
    fun words(text: String): String = if (LEGACY_TEXT.containsMatchIn(text)) TEXT else text

    /**
     * Every notice after the first of a run. A run ends when the bot answers,
     * so a failure that comes back later is announced again.
     */
    fun repeatedIds(messages: List<Message>): Set<String> {
        val repeated = mutableSetOf<String>()
        var announced = false
        for (message in messages) {
            val tool = message.tool
            when {
                message.kind == Message.Kind.ACTIVITY && tool != null && matches(tool) ->
                    if (announced) repeated += message.id else announced = true
                message.role == Message.Role.BOT && message.kind == Message.Kind.TEXT -> announced = false
            }
        }
        return repeated
    }
}
