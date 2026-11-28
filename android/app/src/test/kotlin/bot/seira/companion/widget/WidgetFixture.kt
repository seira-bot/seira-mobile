package bot.seira.companion.widget

internal object WidgetFixture {
    const val NOW = 1_800_000_000_000L

    fun row(
        threadId: String = "thread-1",
        kind: WidgetSnapshot.Kind = WidgetSnapshot.Kind.NEEDS_YOU,
        line: String = "git push",
        since: Long? = null,
        name: String = "Scout",
    ) = WidgetSnapshot.Row(
        botId = "bot-1",
        threadId = threadId,
        name = name,
        color = "green",
        lookKey = "seira|green|",
        kind = kind,
        line = line,
        since = since,
    )

    fun working(threadId: String = "thread-2", line: String = "Working…") = row(
        threadId = threadId,
        kind = WidgetSnapshot.Kind.WORKING,
        line = line,
    )

    fun snapshot(
        rows: List<WidgetSnapshot.Row> = listOf(row()),
        writtenAt: Long = NOW,
        connectionId: String = "computer-1",
    ) = WidgetSnapshot(writtenAt, connectionId, "reduced", rows)
}
