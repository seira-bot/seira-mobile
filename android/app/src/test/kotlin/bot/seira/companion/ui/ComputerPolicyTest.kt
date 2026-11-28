package bot.seira.companion.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** What a still frame is claiming — the rules from `ios/App/ComputerView.swift`. */
class ComputerPolicyTest {

    @Test
    fun `the status label says whether the picture is still arriving`() {
        assertEquals("Preview", ComputerPolicy.statusLabel(bot(id = "b", busy = true)))
        assertEquals("Idle", ComputerPolicy.statusLabel(bot(id = "b", busy = false)))
        assertEquals("Idle", ComputerPolicy.statusLabel(bot(id = "b", busy = null)))
    }

    @Test
    fun `an idle bot explains why there is nothing to watch`() {
        assertTrue(ComputerPolicy.explainsIdle(bot(id = "b", busy = false)))
        assertFalse(ComputerPolicy.explainsIdle(bot(id = "b", busy = true)))
        assertEquals("Nothing to show yet", ComputerPolicy.waitingHeadline(bot(id = "b")))
        assertEquals(
            "Waiting for a frame…",
            ComputerPolicy.waitingHeadline(bot(id = "b", busy = true)),
        )
    }
}

/**
 * Frames are hundreds of kilobytes each, so the watcher count decides whether the
 * stream carries them at all. A count that drifts either pulls screenshots
 * forever or turns them off under an open screen.
 */
class ScreenWatchTest {

    @Test
    fun `watching then leaving starts and stops once`() {
        val started = mutableListOf<String>()
        val stopped = mutableListOf<String>()
        val watch = ScreenWatch(start = { started += it }, stop = { stopped += it })

        watch.watch("bot-1")
        watch.release()
        watch.release()

        assertEquals(listOf("bot-1"), started)
        assertEquals(listOf("bot-1"), stopped)
    }

    @Test
    fun `releasing without watching stops nothing`() {
        val stopped = mutableListOf<String>()
        ScreenWatch(start = {}, stop = { stopped += it }).release()
        assertEquals(emptyList(), stopped)
    }

    @Test
    fun `watching the same bot twice does not double-count`() {
        val started = mutableListOf<String>()
        val stopped = mutableListOf<String>()
        val watch = ScreenWatch(start = { started += it }, stop = { stopped += it })

        watch.watch("bot-1")
        watch.watch("bot-1")

        assertEquals(listOf("bot-1"), started)
        assertEquals(emptyList(), stopped)
    }

    @Test
    fun `switching bots releases the first before taking the second`() {
        val calls = mutableListOf<String>()
        val watch = ScreenWatch(start = { calls += "start:$it" }, stop = { calls += "stop:$it" })

        watch.watch("bot-1")
        watch.watch("bot-2")
        watch.release()

        assertEquals(
            listOf("start:bot-1", "stop:bot-1", "start:bot-2", "stop:bot-2"),
            calls,
        )
    }

    @Test
    fun `every start is matched by exactly one stop`() {
        var balance = 0
        val watch = ScreenWatch(start = { balance += 1 }, stop = { balance -= 1 })

        watch.watch("bot-1")
        watch.watch("bot-2")
        watch.watch("bot-2")
        watch.watch("bot-3")
        watch.release()
        watch.release()

        assertEquals(0, balance)
    }
}
