package bot.seira.companion.core

/**
 * Which stock chips fit the open conversation.
 *
 * The wire carries no bot kind, so the phone reads the conversation: "Show diff" and "Run tests"
 * make sense only after the bot ran a command or changed a file there. A chip the user wrote or
 * edited always shows. Port of iOS `QuickReplyContext`.
 */
object QuickReplyContext {
    private val CODE_CHIP_IDS = setOf("default.diff", "default.tests")

    private val CODE_TOOLS = setOf(
        "bash", "edit", "multiedit", "write", "notebookedit", "apply_patch", "shell", "exec_command",
        "ran a command", "edited a file", "edited files", "wrote a file",
    )

    /** A shell command shown by its own text, as some engines name a call. */
    private val SHELL_PREFIXES = listOf("/bin/bash ", "/bin/sh ", "/bin/zsh ", "bash -", "sh -c")

    fun isCodeTool(tool: ToolActivity): Boolean {
        if (tool.notice == true) return false
        val name = tool.name.trim().lowercase()
        return name in CODE_TOOLS || SHELL_PREFIXES.any { name.startsWith(it) }
    }

    /** True once the bot ran a command or changed a file in this conversation. */
    fun hasCodeActivity(messages: List<Message>): Boolean =
        messages.any { it.kind == Message.Kind.ACTIVITY && it.tool?.let(::isCodeTool) == true }

    /** A stock chip that the user has not touched. An edited title or prompt makes it the user's own. */
    private fun isUntouchedCodeChip(reply: QuickReply): Boolean {
        if (reply.id !in CODE_CHIP_IDS) return false
        val stock = QuickReply.DEFAULTS.firstOrNull { it.id == reply.id } ?: return false
        return reply.title == stock.title && reply.prompt == stock.prompt
    }

    /** The saved chips for a conversation: the untouched code chips drop out until there is code activity. */
    fun chips(saved: List<QuickReply>, codeActivity: Boolean): List<QuickReply> =
        if (codeActivity) saved else saved.filterNot(::isUntouchedCodeChip)
}
