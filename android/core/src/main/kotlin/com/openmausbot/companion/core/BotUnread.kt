package com.openmausbot.companion.core

/**
 * The threads of one bot that still show as unread, in list order. A bot
 * without a task list counts as one thread. A task without its own flag
 * follows the bot's flag, but only for the selected thread.
 */
fun Bot.unreadThreadIds(): List<String> {
    val tasks = tasks
    if (tasks.isNullOrEmpty()) return if (unread) listOf(threadId) else emptyList()
    return tasks
        .filter { it.unread ?: (it.threadId == threadId && unread) }
        .map { it.threadId }
}
