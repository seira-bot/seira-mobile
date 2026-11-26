package com.openmausbot.companion.ui.chat.composer

import com.openmausbot.companion.R
import com.openmausbot.companion.core.ApprovalMode
import com.openmausbot.companion.core.ApprovalModeRefusal
import com.openmausbot.companion.core.ApprovalModeResult
import com.openmausbot.companion.core.Chat
import com.openmausbot.companion.core.GoalRunCard
import com.openmausbot.companion.core.Message
import com.openmausbot.companion.core.QueuedSend
import com.openmausbot.companion.core.SteerResult
import com.openmausbot.companion.ui.ChatActionId
import com.openmausbot.companion.ui.bot
import com.openmausbot.companion.ui.room
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/** AC-19: the round button. */
class ComposerRoundTest {
    @Test
    fun `an empty field over an idle bot starts the call`() {
        val button = ComposerRound.of(hasContent = false, working = false, callAvailable = true)
        assertEquals(RoundButton.CALL, button)
        assertTrue(ComposerRound.enabled(button, hasContent = false))
    }

    @Test
    fun `text sends`() {
        assertEquals(RoundButton.SEND, ComposerRound.of(hasContent = true, working = false, callAvailable = true))
    }

    @Test
    fun `a working bot with an empty field gets Stop`() {
        val button = ComposerRound.of(hasContent = false, working = true, callAvailable = true)
        assertEquals(RoundButton.STOP, button)
        assertTrue(ComposerRound.enabled(button, hasContent = false))
    }

    @Test
    fun `a line typed while the bot works still sends`() {
        assertEquals(RoundButton.SEND, ComposerRound.of(hasContent = true, working = true, callAvailable = true))
    }

    @Test
    fun `without a call the empty button is a disabled Send`() {
        val button = ComposerRound.of(hasContent = false, working = false, callAvailable = false)
        assertEquals(RoundButton.SEND, button)
        assertFalse(ComposerRound.enabled(button, hasContent = false))
    }
}

/** AC-15, AC-16: the mode chip and its sheet. */
class ModeChipTest {
    @Test
    fun `a bot chat shows the chip and a group does not`() {
        assertTrue(ModeChip.visible(Chat.BotChat(bot())))
        assertFalse(ModeChip.visible(Chat.RoomChat(room())))
    }

    @Test
    fun `the sheet lists the four modes a phone can set`() {
        assertEquals(
            listOf(ApprovalMode.ASK, ApprovalMode.EDITS, ApprovalMode.AUTO, ApprovalMode.FULL),
            ModeChip.options(ApprovalMode.ASK),
        )
        assertEquals(ModeChip.options(null), ModeChip.options(ApprovalMode.FULL))
    }

    @Test
    fun `Custom is listed only while it is the current mode`() {
        assertEquals(ApprovalMode.CUSTOM, ModeChip.options(ApprovalMode.CUSTOM).last())
        assertFalse(ApprovalMode.CUSTOM.phoneCanSet)
    }

    @Test
    fun `picking a mode sends that mode`() = runTest {
        val sent = mutableListOf<Pair<ApprovalMode, Boolean>>()
        val outcome = ModeChange.request(ApprovalMode.ASK, ApprovalMode.EDITS, acknowledged = false) { mode, ack ->
            sent += mode to ack
            ApprovalModeResult.Changed(bot(), mode)
        }
        assertEquals(listOf(ApprovalMode.EDITS to false), sent)
        assertEquals(ModeChangeOutcome.Changed(ApprovalMode.EDITS), outcome)
    }

    @Test
    fun `a refusal keeps the old mode and gives one plain reason`() = runTest {
        val refusals = mapOf(
            ApprovalModeRefusal.FullNotAllowedHere("x") to R.string.composer_mode_refused_full,
            ApprovalModeRefusal.LeaveCustomOnDesktop("x") to R.string.composer_mode_refused_leave_custom,
            ApprovalModeRefusal.CustomOnDesktop("x") to R.string.composer_mode_refused_custom,
            ApprovalModeRefusal.OwnerOnly("x") to R.string.composer_mode_refused_owner,
            ApprovalModeRefusal.ProviderUnsupported("x") to R.string.composer_mode_refused_provider,
            ApprovalModeRefusal.StillConfirming("x") to R.string.composer_mode_refused_confirming,
            ApprovalModeRefusal.Other("x") to R.string.composer_mode_refused_other,
        )
        refusals.forEach { (refusal, reason) ->
            val outcome = ModeChange.request(ApprovalMode.ASK, ApprovalMode.FULL, acknowledged = false) { _, _ ->
                ApprovalModeResult.Refused(refusal)
            }
            assertEquals(ModeChangeOutcome.Refused(ApprovalMode.ASK, reason), outcome)
        }
    }

    @Test
    fun `Auto on the computer asks first and then sends the acknowledgement`() = runTest {
        val sent = mutableListOf<Boolean>()
        val set: suspend (ApprovalMode, Boolean) -> ApprovalModeResult = { mode, ack ->
            sent += ack
            if (ack) ApprovalModeResult.Changed(bot(), mode)
            else ApprovalModeResult.Refused(ApprovalModeRefusal.LocalAutoNeedsAcknowledgement("x"))
        }
        val first = ModeChange.request(ApprovalMode.ASK, ApprovalMode.AUTO, acknowledged = false, set = set)
        assertEquals(ModeChangeOutcome.NeedsConfirmation(ApprovalMode.AUTO), first)
        val second = ModeChange.request(ApprovalMode.ASK, ApprovalMode.AUTO, acknowledged = true, set = set)
        assertEquals(ModeChangeOutcome.Changed(ApprovalMode.AUTO), second)
        assertEquals(listOf(false, true), sent)
    }

    @Test
    fun `Custom is never sent from the phone`() = runTest {
        var calls = 0
        val outcome = ModeChange.request(ApprovalMode.ASK, ApprovalMode.CUSTOM, acknowledged = false) { mode, _ ->
            calls++
            ApprovalModeResult.Changed(bot(), mode)
        }
        assertEquals(0, calls)
        assertEquals(ModeChangeOutcome.Refused(ApprovalMode.ASK, R.string.composer_mode_refused_custom), outcome)
    }
}

/** AC-17: Steer goes to the steer call, never to interrupt. */
class QueuedSteeringTest {
    private val send = QueuedSend("q1", "and add a summary")
    private val chat = Chat.BotChat(bot())

    @Test
    fun `Steer asks the computer to steer exactly this message`() = runTest {
        val asked = mutableListOf<Pair<QueuedSend, Chat>>()
        val outcome = QueuedSteering.steer(send, chat) { s, c ->
            asked += s to c
            SteerResult.Steered(listOf("q1"))
        }
        assertEquals(listOf(send to chat), asked)
        assertEquals(SteerOutcome.DONE, outcome)
    }

    @Test
    fun `a message that already left the queue counts as done`() = runTest {
        assertEquals(SteerOutcome.DONE, QueuedSteering.steer(send, chat) { _, _ -> SteerResult.Gone })
    }

    @Test
    fun `a message the turn cannot take yet stays queued`() = runTest {
        assertEquals(
            SteerOutcome.WAITING,
            QueuedSteering.steer(send, chat) { _, _ -> SteerResult.StillQueued("images") },
        )
    }

    @Test
    fun `no answer is a failure`() = runTest {
        assertEquals(SteerOutcome.FAILED, QueuedSteering.steer(send, chat) { _, _ -> null })
    }

    @Test
    fun `a group can steer too`() = runTest {
        val outcome = QueuedSteering.steer(send, Chat.RoomChat(room())) { _, _ -> SteerResult.Steered(emptyList()) }
        assertEquals(SteerOutcome.DONE, outcome)
    }
}

class GoalChipTest {
    private fun run(id: String, status: String, goal: String) =
        Message(id, Message.Role.BOT, Message.Kind.GOAL_RUN, 1.0, goalRun = GoalRunCard(runId = id, goal = goal, status = status))

    @Test
    fun `a running goal shows its words`() {
        assertEquals("Ship the report", GoalChip.text(listOf(run("a", "working", "Ship the report"))))
    }

    @Test
    fun `a finished goal shows nothing`() {
        assertNull(GoalChip.text(listOf(run("a", "completed", "Ship the report"))))
        assertNull(GoalChip.text(emptyList()))
    }

    @Test
    fun `a long goal is cut`() {
        val text = GoalChip.text(listOf(run("a", "working", "x".repeat(200))))
        assertTrue(text!!.length <= 36)
    }
}

/** AC-18: the four message commands. */
class ComposerCommandsTest {
    @Test
    fun `each command has a plain description and inserts its own name`() {
        listOf(
            ComposerCommands.GOAL,
            ComposerCommands.LEARN,
            ComposerCommands.SETUP,
            ComposerCommands.BURST,
        ).forEach {
            assertTrue(it.description.isNotBlank())
            assertEquals(com.openmausbot.companion.ui.SlashEffect.Insert(it.title + " "), it.effect)
        }
    }
}

class ComposerPlusTest {
    private fun ids(chat: Chat, busy: Boolean = false) =
        ComposerPlus.actions(chat, hasPendingApproval = false, canAddAttachment = true, hasCamera = false, offersSchedule = chat is Chat.BotChat)
            .map { it.id }

    @Test
    fun `a bot gets only actions that exist, in the sheet's order`() {
        assertEquals(
            listOf(
                ChatActionId.PHOTOS,
                ChatActionId.FILES,
                ChatActionId.NEW_TASK,
                ChatActionId.TASKS,
                ChatActionId.SETTINGS,
                ChatActionId.SCHEDULE,
                ChatActionId.WATCH_COMPUTER,
                ChatActionId.WORK_UNTIL_DONE,
                ChatActionId.SHARE_MARKDOWN,
                ChatActionId.SHARE_JSON,
            ).sortedBy { it.name },
            ids(Chat.BotChat(bot())).sortedBy { it.name },
        )
    }

    @Test
    fun `the call and Stop live on the round button, not in the sheet`() {
        val busyBot = Chat.BotChat(bot(busy = true))
        val actions = ids(busyBot)
        assertFalse(ChatActionId.LIVE_CALL in actions)
        assertFalse(ChatActionId.INTERRUPT in actions)
    }

    @Test
    fun `there is no Teach a task`() {
        val titles = ComposerPlus.actions(
            Chat.BotChat(bot()),
            hasPendingApproval = false,
            canAddAttachment = true,
            hasCamera = true,
            offersSchedule = true,
        ).flatMap { listOf(it.title, it.subtitle) }
        assertTrue(titles.none { it.contains("Teach", ignoreCase = true) })
    }

    @Test
    fun `Work until done is offered and fills the field`() {
        assertTrue(ChatActionId.WORK_UNTIL_DONE in ids(Chat.BotChat(bot())))
    }
}
