package com.openmausbot.companion.widget

import com.openmausbot.companion.widget.WidgetFixture.NOW
import com.openmausbot.companion.widget.WidgetFixture.row
import com.openmausbot.companion.widget.WidgetFixture.snapshot
import com.openmausbot.companion.widget.WidgetFixture.working
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WidgetSnapshotTest {
    private val minute = 60_000L

    @Test
    fun `an unchanged payload renews at most once a minute`() {
        val held = snapshot()
        assertFalse(snapshot(writtenAt = NOW + 59_000).shouldReplace(held))
        assertTrue(snapshot(writtenAt = NOW + minute).shouldReplace(held))
        assertTrue(snapshot().shouldReplace(null))
    }

    @Test
    fun `a change of anything but a working line publishes at once`() {
        val held = snapshot(listOf(row(), working()))
        assertTrue(snapshot(listOf(row(line = "rm -rf"), working()), NOW + 1).shouldReplace(held))
        assertTrue(snapshot(listOf(row(), working(threadId = "thread-3")), NOW + 1).shouldReplace(held))
        assertTrue(snapshot(listOf(row()), NOW + 1).shouldReplace(held))
        assertTrue(snapshot(listOf(row(name = "Maren"), working()), NOW + 1).shouldReplace(held))
        assertTrue(snapshot(listOf(row(), working()), NOW + 1, connectionId = "computer-2").shouldReplace(held))
    }

    @Test
    fun `a working line alone waits thirty seconds`() {
        val held = snapshot(listOf(working(line = "Reading")))
        val next = { at: Long -> snapshot(listOf(working(line = "Writing")), at) }
        assertFalse(next(NOW + 29_000).shouldReplace(held))
        assertTrue(next(NOW + 30_000).shouldReplace(held))
        assertTrue(next(NOW + 1).shouldReplace(held, workingLineIntervalMs = 0))
    }

    @Test
    fun `a clock that went backwards publishes`() {
        assertTrue(snapshot(writtenAt = NOW - 1).shouldReplace(snapshot()))
    }

    @Test
    fun `the since clock holds while the kind holds and restarts when it changes`() {
        val clock = WidgetSinceClock()
        assertEquals(NOW, clock.stamp("bot-1", "t", WidgetSnapshot.Kind.WORKING, NOW))
        assertEquals(NOW, clock.stamp("bot-1", "t", WidgetSnapshot.Kind.WORKING, NOW + minute))
        assertEquals(NOW + 2 * minute, clock.stamp("bot-1", "t", WidgetSnapshot.Kind.NEEDS_YOU, NOW + 2 * minute))
    }

    @Test
    fun `the since clock forgets departed chats and seeds from the last snapshot`() {
        val seeded = WidgetSinceClock(snapshot(listOf(row(threadId = "t", kind = WidgetSnapshot.Kind.WORKING, since = NOW), row(threadId = "u", since = null))))
        assertEquals(NOW, seeded.stamp("bot-1", "t", WidgetSnapshot.Kind.WORKING, NOW + minute))
        assertEquals(NOW + minute, seeded.stamp("bot-1", "u", WidgetSnapshot.Kind.NEEDS_YOU, NOW + minute))
        seeded.forgetAbsentFrom(emptyList())
        assertEquals(NOW + 3 * minute, seeded.stamp("bot-1", "t", WidgetSnapshot.Kind.WORKING, NOW + 3 * minute))
    }

    @Test
    fun `the store round trips replaces and removes`() {
        val dir = File.createTempFile("widget", "").apply { delete() }
        try {
            val store = WidgetSnapshotStore(dir)
            assertNull(store.read())
            store.write(snapshot())
            assertEquals(snapshot(), store.read())
            val next = snapshot(listOf(working()), NOW + 1)
            store.write(next)
            assertEquals(next, store.read())
            assertEquals(listOf(WidgetSnapshotStore.FILE_NAME), dir.list().orEmpty().toList())
            store.remove()
            assertNull(store.read())
            store.remove()
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `an undecodable file reads as null and stays on disk`() {
        val dir = File.createTempFile("widget", "").apply { delete() }
        try {
            val store = WidgetSnapshotStore(dir)
            dir.mkdirs()
            store.file.writeText("{not json")
            assertNull(store.read())
            assertTrue(store.file.isFile)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `a snapshot written before a field existed still decodes`() {
        val dir = File.createTempFile("widget", "").apply { delete() }
        try {
            val store = WidgetSnapshotStore(dir)
            dir.mkdirs()
            store.file.writeText(
                """{"writtenAt":1,"connectionId":"c","rows":[{"botId":"b","threadId":"t","name":"n","color":"green","lookKey":"k","kind":"working","line":"l","future":1}]}""",
            )
            val read = assertNotNull(store.read())
            assertEquals(WidgetSnapshot.Kind.WORKING, read.rows.single().kind)
            assertNull(read.rows.single().since)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `the digest spends its budget top down by priority`() {
        val rows = List(5) { row(threadId = "a$it") } + List(5) { working("w$it") }
        val sections = WidgetPresentation.sections(rows)
        assertEquals(listOf(WidgetSnapshot.Kind.NEEDS_YOU, WidgetSnapshot.Kind.WORKING), sections.map { it.first })
        assertEquals(8, sections.sumOf { it.second.size })
        assertEquals(3, sections[1].second.size)
    }

    @Test
    fun `the view tells unpaired quiet and stale apart`() {
        assertEquals(WidgetView.Unpaired, WidgetPresentation.view(null, NOW))
        assertEquals(WidgetView.Quiet(null), WidgetPresentation.view(snapshot(emptyList()), NOW + 14 * minute))
        assertEquals(WidgetView.Quiet(NOW), WidgetPresentation.view(snapshot(emptyList()), NOW + 15 * minute))
        val fresh = WidgetPresentation.view(snapshot(), NOW + 14 * minute) as WidgetView.Active
        assertNull(fresh.asOf)
        val stale = WidgetPresentation.view(snapshot(), NOW + 16 * minute) as WidgetView.Active
        assertEquals(NOW, stale.asOf)
    }
}
