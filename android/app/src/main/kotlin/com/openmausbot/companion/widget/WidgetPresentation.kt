package com.openmausbot.companion.widget

/** What the widget shows for a snapshot at a moment, decided without any drawing code. */
internal sealed interface WidgetView {
    data object Unpaired : WidgetView

    /** [asOf] is the write time once the content is stale. */
    data class Quiet(val asOf: Long?) : WidgetView

    data class Active(val rows: List<WidgetSnapshot.Row>, val asOf: Long?) : WidgetView
}

internal object WidgetPresentation {
    /** A widget digest lists at most this many rows, spent top-down by priority. */
    const val DIGEST_BUDGET = 8
    const val FLAT_ROWS = 4

    fun view(snapshot: WidgetSnapshot?, now: Long): WidgetView {
        if (snapshot == null) return WidgetView.Unpaired
        val age = now - snapshot.writtenAt
        val asOf = snapshot.writtenAt.takeIf { age >= WidgetSnapshot.STALE_AFTER_MS }
        if (snapshot.rows.isEmpty()) return WidgetView.Quiet(asOf)
        return WidgetView.Active(snapshot.rows, asOf)
    }

    fun sections(rows: List<WidgetSnapshot.Row>, budget: Int = DIGEST_BUDGET): List<Pair<WidgetSnapshot.Kind, List<WidgetSnapshot.Row>>> {
        var left = budget
        val out = mutableListOf<Pair<WidgetSnapshot.Kind, List<WidgetSnapshot.Row>>>()
        for (kind in WidgetSnapshot.Kind.entries) {
            if (left <= 0) break
            val items = rows.filter { it.kind == kind }.take(left)
            if (items.isEmpty()) continue
            left -= items.size
            out += kind to items
        }
        return out
    }

    /** The row the 2x2 size shows: the first ask, else the first row. */
    fun top(rows: List<WidgetSnapshot.Row>): WidgetSnapshot.Row? =
        rows.firstOrNull { it.kind == WidgetSnapshot.Kind.NEEDS_YOU } ?: rows.firstOrNull()
}
