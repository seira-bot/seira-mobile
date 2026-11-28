package bot.seira.companion.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import bot.seira.companion.ui.theme.Seira
import bot.seira.companion.ui.theme.SeiraType

/** A reply, cut into prose and strict pipe tables. */
internal sealed interface MarkdownSegment {
    data class Prose(val source: String) : MarkdownSegment

    /** [endAligned] holds the column indexes that line up to the end (numbers). */
    data class Table(
        val headers: List<String>,
        val rows: List<List<String>>,
        val endAligned: Set<Int>,
    ) : MarkdownSegment
}

/**
 * Finds the pipe tables that are safe to draw as tables.
 *
 * Strict on purpose. A block is a table only when it stands alone between blank
 * lines (or the ends of the reply), has a header line, a separator line with the
 * same number of columns, and at least one row where every row has that same
 * number of columns. Anything else stays text, so a half-written or ragged block
 * is never redrawn as something it is not. Fenced code is never searched.
 */
internal object MarkdownTables {
    private val separatorCell = Regex("^:?-{3,}:?$")
    private val numberCell = Regex(
        "^[+-]?\\s*(?:rp\\.?|idr|usd|\\$|€|£)?\\s*[+-]?\\d[\\d.,\\s]*%?$",
        RegexOption.IGNORE_CASE,
    )

    fun split(source: String): List<MarkdownSegment> {
        val lines = source.replace("\r\n", "\n").replace("\r", "\n").split('\n')
        val out = mutableListOf<MarkdownSegment>()
        val prose = mutableListOf<String>()
        fun flush() {
            if (prose.isNotEmpty()) out += MarkdownSegment.Prose(prose.joinToString("\n"))
            prose.clear()
        }
        var fenced = false
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trim()
            if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) fenced = !fenced
            if (!fenced && trimmed.startsWith("|") && (i == 0 || lines[i - 1].isBlank())) {
                val parsed = tableAt(lines, i)
                if (parsed != null) {
                    flush()
                    out += parsed.first
                    i = parsed.second
                    continue
                }
            }
            prose += line
            i++
        }
        flush()
        return if (out.none { it is MarkdownSegment.Table }) listOf(MarkdownSegment.Prose(source)) else out
    }

    /** The table that starts at [start] and the index of the line after it, or null. */
    private fun tableAt(lines: List<String>, start: Int): Pair<MarkdownSegment.Table, Int>? {
        val headers = cells(lines[start]) ?: return null
        if (headers.size < 2) return null
        val separator = cells(lines.getOrNull(start + 1) ?: return null) ?: return null
        if (separator.size != headers.size || !separator.all { separatorCell.matches(it) }) return null
        val rows = mutableListOf<List<String>>()
        var j = start + 2
        while (j < lines.size && lines[j].isNotBlank()) {
            val row = cells(lines[j]) ?: return null
            if (row.size != headers.size) return null
            rows += row
            j++
        }
        if (rows.isEmpty()) return null
        val end = headers.indices.filter { column ->
            val filled = rows.map { it[column] }.filter { it.isNotEmpty() }
            (separator[column].endsWith(":") && !separator[column].startsWith(":")) ||
                (filled.isNotEmpty() && filled.all { numberCell.matches(it) })
        }.toSet()
        return MarkdownSegment.Table(headers, rows, end) to j
    }

    /** The cells of one `| a | b |` line, or null when it does not start and end with a pipe. */
    private fun cells(line: String): List<String>? {
        val t = line.trim()
        if (t.length < 2 || !t.startsWith("|") || !t.endsWith("|") || t.endsWith("\\|")) return null
        val body = t.substring(1, t.length - 1)
        val out = mutableListOf<String>()
        val cell = StringBuilder()
        var k = 0
        while (k < body.length) {
            val c = body[k]
            if (c == '\\' && k + 1 < body.length && body[k + 1] == '|') {
                cell.append('|')
                k += 2
                continue
            }
            if (c == '|') {
                out += cell.toString().trim()
                cell.clear()
            } else {
                cell.append(c)
            }
            k++
        }
        out += cell.toString().trim()
        return out
    }
}

/**
 * A table inside a reply, in the card style of the design: a hairline border,
 * 18 dp corners, a hairline above every row, and numbers lined up to the end.
 * A wide table scrolls sideways inside its own border. The cells are emitted
 * row by row, so a screen reader walks the table the way it is read.
 */
@Composable
internal fun MarkdownTableView(table: MarkdownSegment.Table, modifier: Modifier = Modifier) {
    val colors = Seira.colors
    val shape = RoundedCornerShape(18.dp)
    val scroll = rememberScrollState()
    val columns = table.headers.size
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, colors.hairline, shape),
    ) {
        Layout(
            modifier = Modifier
                .horizontalScroll(scroll)
                .widthIn(min = maxWidth),
            content = {
                table.headers.forEach { text ->
                    Text(
                        text = plain(text),
                        style = SeiraType.caption.copy(fontWeight = FontWeight.Medium),
                        color = colors.ink3,
                        softWrap = false,
                    )
                }
                table.rows.forEach { row ->
                    row.forEachIndexed { column, text ->
                        Text(
                            text = plain(text),
                            style = SeiraType.callout,
                            color = if (column == 0 && column !in table.endAligned) colors.ink2 else colors.ink,
                            softWrap = false,
                        )
                    }
                }
                // One rule above each body row, after the cells so it is never read.
                repeat(table.rows.size) { HorizontalDivider(color = colors.hairline) }
            },
            measurePolicy = remember(columns, table.endAligned) {
                markdownTablePolicy(columns, table.endAligned)
            },
        )
    }
}

private fun plain(text: String): String =
    InlineMarkdown.parse(text).joinToString("") { it.text }

private val TABLE_EDGE = 14.dp
private val TABLE_GAP = 16.dp
private val HEADER_PAD = 8.dp
private val ROW_PAD = 10.dp

/**
 * Children: the header cells, the body cells row by row, then one rule for each
 * row. Columns are as wide as their widest cell; spare width goes to the widest
 * text column so numbers stay at the end.
 */
private fun markdownTablePolicy(columns: Int, endAligned: Set<Int>): MeasurePolicy =
    MeasurePolicy { measurables, constraints ->
        val rowCount = (measurables.size + 1) / (columns + 1) // header and body rows; one rule for each row after the header
        val cellCount = rowCount * columns
        val unbounded = Constraints()
        val cells = List(cellCount) { measurables[it].measure(unbounded) }
        val widths = IntArray(columns) { c -> (0 until rowCount).maxOf { cells[it * columns + c].width } }
        val edge = TABLE_EDGE.roundToPx()
        val gap = TABLE_GAP.roundToPx()
        var natural = edge * 2 + gap * (columns - 1) + widths.sum()
        if (constraints.minWidth > natural) {
            val grow = (0 until columns).filter { it !in endAligned }.maxByOrNull { widths[it] }
                ?: (0 until columns).maxByOrNull { widths[it] }!!
            widths[grow] += constraints.minWidth - natural
            natural = constraints.minWidth
        }
        val x = IntArray(columns)
        var pen = edge
        for (c in 0 until columns) {
            x[c] = pen
            pen += widths[c] + gap
        }
        val pads = IntArray(rowCount) { (if (it == 0) HEADER_PAD else ROW_PAD).roundToPx() }
        val tops = IntArray(rowCount)
        var y = 0
        for (r in 0 until rowCount) {
            tops[r] = y
            y += pads[r] * 2 + (0 until columns).maxOf { cells[r * columns + it].height }
        }
        val rules = List(rowCount - 1) {
            measurables[cellCount + it].measure(Constraints.fixedWidth(natural))
        }
        layout(constraints.constrainWidth(natural), constraints.constrainHeight(y)) {
            for (r in 0 until rowCount) {
                if (r > 0) rules[r - 1].placeRelative(0, tops[r])
                for (c in 0 until columns) {
                    val cell = cells[r * columns + c]
                    val px = if (c in endAligned) x[c] + widths[c] - cell.width else x[c]
                    cell.placeRelative(px, tops[r] + pads[r])
                }
            }
        }
    }
