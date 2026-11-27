package bot.seira.companion.core

import kotlin.test.Test
import kotlin.test.assertEquals

class BotUnreadTest {
    private fun bot(unread: Boolean, tasks: List<BotTask>? = null) = Bot(
        id = "b1",
        threadId = "selected",
        name = "Scout",
        title = "",
        description = "",
        notifications = true,
        color = "green",
        unread = unread,
        modelSelection = ModelSelection("codex", "model"),
        createdAt = 1.0,
        tasks = tasks,
    )

    private fun task(id: String, unread: Boolean?) = BotTask(id, id, 1.0, unread = unread)

    @Test
    fun aBotWithoutTasksCountsItsSelectedThread() {
        assertEquals(listOf("selected"), bot(unread = true).unreadThreadIds())
        assertEquals(emptyList(), bot(unread = false).unreadThreadIds())
        assertEquals(listOf("selected"), bot(unread = true, tasks = emptyList()).unreadThreadIds())
    }

    @Test
    fun aTaskFlagWinsAndAnUnflaggedTaskFollowsTheBotOnlyWhenSelected() {
        val tasks = listOf(task("selected", null), task("filed", true), task("other", null), task("done", false))
        assertEquals(listOf("selected", "filed"), bot(unread = true, tasks = tasks).unreadThreadIds())
        assertEquals(listOf("filed"), bot(unread = false, tasks = tasks).unreadThreadIds())
    }
}
