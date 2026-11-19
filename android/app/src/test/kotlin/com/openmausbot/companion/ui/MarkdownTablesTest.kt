package com.openmausbot.companion.ui

import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Which pipe blocks become tables, and how a table in a reply reads and lines up. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MarkdownTablesTest {

    @get:Rule
    val compose = createComposeRule()

    private val reply = """
        Dina Pratama menunggu dua pembayaran.

        | Invoice | Pelanggan | Jumlah |
        | --- | --- | ---: |
        | INV-1182 | Toko Sinar Jaya | 2.150.000 |
        | INV-1190 | Toko Sinar Jaya | 2.100.000 |

        Kirim pengingat hari ini.
    """.trimIndent()

    @Test
    fun `a strict table between two paragraphs becomes a table and keeps both sentences`() {
        val parts = MarkdownTables.split(reply)
        assertEquals(3, parts.size)
        assertEquals("Dina Pratama menunggu dua pembayaran.\n", (parts[0] as MarkdownSegment.Prose).source)
        val table = parts[1] as MarkdownSegment.Table
        assertEquals(listOf("Invoice", "Pelanggan", "Jumlah"), table.headers)
        assertEquals(2, table.rows.size)
        assertEquals(listOf("INV-1190", "Toko Sinar Jaya", "2.100.000"), table.rows[1])
        assertTrue((parts[2] as MarkdownSegment.Prose).source.contains("Kirim pengingat"))
    }

    @Test
    fun `number columns line up to the end and text columns do not`() {
        val table = MarkdownTables.split(reply)[1] as MarkdownSegment.Table
        assertEquals(setOf(2), table.endAligned)
    }

    @Test
    fun `a ragged block stays text`() {
        val ragged = "Intro\n\n| a | b |\n| --- | --- |\n| 1 | 2 | 3 |\n\nEnd"
        assertEquals(listOf(MarkdownSegment.Prose(ragged)), MarkdownTables.split(ragged))
    }

    @Test
    fun `a block without a separator line stays text`() {
        val loose = "| a | b |\n| 1 | 2 |"
        assertEquals(listOf(MarkdownSegment.Prose(loose)), MarkdownTables.split(loose))
    }

    @Test
    fun `a table glued to a sentence stays text`() {
        val glued = "Here it is:\n| a | b |\n| --- | --- |\n| 1 | 2 |"
        assertEquals(listOf(MarkdownSegment.Prose(glued)), MarkdownTables.split(glued))
    }

    @Test
    fun `a table inside a code fence stays text`() {
        val fenced = "```\n\n| a | b |\n| --- | --- |\n| 1 | 2 |\n\n```"
        assertEquals(listOf(MarkdownSegment.Prose(fenced)), MarkdownTables.split(fenced))
    }

    @Test
    fun `an escaped pipe stays inside its cell`() {
        val table = MarkdownTables.split("| a | b |\n| --- | --- |\n| x \\| y | 2 |")[0] as MarkdownSegment.Table
        assertEquals(listOf("x | y", "2"), table.rows[0])
    }

    private fun spoken(node: SemanticsNode): List<String> = buildList {
        node.config.getOrNull(SemanticsProperties.Text)?.forEach { add(it.text) }
        node.children.forEach { addAll(spoken(it)) }
    }

    @Test
    fun `a screen reader reads the table row by row`() {
        compose.setContent { CompanionTheme(darkTheme = false) { MarkdownText(reply) } }
        val read = spoken(compose.onRoot().fetchSemanticsNode())
        assertEquals(
            listOf(
                "Dina Pratama menunggu dua pembayaran.",
                "Invoice", "Pelanggan", "Jumlah",
                "INV-1182", "Toko Sinar Jaya", "2.150.000",
                "INV-1190", "Toko Sinar Jaya", "2.100.000",
                "Kirim pengingat hari ini.",
            ),
            read.map { it.trim() },
        )
    }

    @Test
    fun `the amount column ends at the same edge in every row`() {
        compose.setContent { CompanionTheme(darkTheme = false) { MarkdownText(reply) } }
        val a = compose.onNodeWithText("2.150.000").getBoundsInRoot()
        val b = compose.onNodeWithText("2.100.000").getBoundsInRoot()
        assertTrue(kotlin.math.abs((a.right - b.right).value) < 0.5f, "right edges ${a.right} and ${b.right}")
        val head = compose.onNodeWithText("Jumlah").getBoundsInRoot()
        assertTrue(kotlin.math.abs((a.right - head.right).value) < 0.5f, "header is not at the same end")
    }

    @Test
    fun `a ragged block is shown as text in the reply`() {
        val ragged = "Intro\n\n| a | b |\n| --- | --- |\n| 1 | 2 | 3 |\n\nEnd"
        compose.setContent { CompanionTheme(darkTheme = false) { MarkdownText(ragged) } }
        assertFalse(spoken(compose.onRoot().fetchSemanticsNode()).any { it.trim() == "a" })
    }
}
