package com.openmausbot.companion.widget

internal object WidgetFixture {
    const val NOW = 1_800_000_000_000L

    fun row(
        threadId: String = "thread-1",
        kind: WidgetSnapshot.Kind = WidgetSnapshot.Kind.NEEDS_YOU,
        line: String = "git push",
        requestId: String? = "request-1",
        options: List<String> = listOf("Allow", "Deny"),
        isPermission: Boolean = true,
        plainAllow: String? = "Allow",
        fullyShown: Boolean = true,
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
        requestId = requestId,
        options = options,
        isPermission = isPermission,
        plainAllow = plainAllow,
        fullyShown = fullyShown,
    )

    fun working(threadId: String = "thread-2", line: String = "Working…") = row(
        threadId = threadId,
        kind = WidgetSnapshot.Kind.WORKING,
        line = line,
        requestId = null,
        options = emptyList(),
        isPermission = false,
        plainAllow = null,
    )

    fun snapshot(
        rows: List<WidgetSnapshot.Row> = listOf(row()),
        writtenAt: Long = NOW,
        connectionId: String = "computer-1",
    ) = WidgetSnapshot(writtenAt, connectionId, "reduced", rows)
}
