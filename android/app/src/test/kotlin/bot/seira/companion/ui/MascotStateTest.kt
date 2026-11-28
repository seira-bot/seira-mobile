package bot.seira.companion.ui

import bot.seira.companion.core.Chat
import bot.seira.companion.core.CompanionState
import bot.seira.companion.core.Message
import bot.seira.companion.core.OptionCard
import bot.seira.companion.core.ToolActivity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Which face a bot wears, in the desktop's order: a pinned expression, then a
 * failure, then work, then unread, then a question, then its role. The order is
 * the point — it is what makes "this one stopped and needs you" louder than "this
 * one is busy", so each rung is tested against the one below it.
 *
 * Ported alongside `ios/App/MascotState.swift`; a divergence here is a bot wearing
 * one face on the laptop and another on the phone.
 */
class MascotStateTest {
    private val idle = bot(title = "", name = "Bot")

    @Test
    fun `a pinned expression wins over everything the bot is doing`() {
        val pinned = idle.copy(
            mascotExpression = "celebrate",
            busy = true,
            unread = true,
        )
        assertEquals(CharacterMood.CELEBRATE, CharacterMood.forBot(pinned, failedActivity))
    }

    @Test
    fun `the desktop's legacy expression names still resolve`() {
        assertEquals(CharacterMood.IDLE, CharacterMood.normalize("deadpan"))
        assertEquals(CharacterMood.HAPPY, CharacterMood.normalize("friendly"))
        assertEquals(CharacterMood.WORKING, CharacterMood.normalize("focused"))
        assertEquals(CharacterMood.THINKING, CharacterMood.normalize("thinking"))
        assertEquals(CharacterMood.EXCITED, CharacterMood.normalize("excited"))
        assertEquals(CharacterMood.DROWSY, CharacterMood.normalize("sleepy"))
        assertEquals(CharacterMood.SURPRISED, CharacterMood.normalize("surprised"))
        assertEquals(CharacterMood.SUSPICIOUS, CharacterMood.normalize("skeptical"))
        assertEquals(CharacterMood.SCARED, CharacterMood.normalize("worried"))
        assertEquals(CharacterMood.PLAYFUL, CharacterMood.normalize("mischievous"))
    }

    @Test
    fun `every current expression name resolves to itself`() {
        for (state in CharacterMood.entries) {
            assertEquals(state, CharacterMood.normalize(state.id), state.id)
        }
    }

    @Test
    fun `an unknown or missing expression is not a pin`() {
        assertNull(CharacterMood.normalize(null))
        assertNull(CharacterMood.normalize(""))
        assertNull(CharacterMood.normalize("smouldering"))
        // and so the bot falls through to the rest of the ladder
        assertEquals(
            CharacterMood.WORKING,
            CharacterMood.forBot(idle.copy(mascotExpression = "smouldering", busy = true), null),
        )
    }

    @Test
    fun `a failed tool beats being busy`() {
        val busy = idle.copy(busy = true, unread = true)
        assertEquals(CharacterMood.ALERTING, CharacterMood.forBot(busy, failedActivity))
    }

    @Test
    fun `an activity that did not fail is not an alert`() {
        val ok = message(Message.Kind.ACTIVITY, tool = ToolActivity(name = "grep", ok = true))
        val unknown = message(Message.Kind.ACTIVITY, tool = ToolActivity(name = "grep"))
        assertEquals(CharacterMood.IDLE, CharacterMood.forBot(idle, ok))
        assertEquals(CharacterMood.IDLE, CharacterMood.forBot(idle, unknown))
    }

    @Test
    fun `a failure only counts on an activity`() {
        val text = message(Message.Kind.TEXT, tool = ToolActivity(name = "grep", ok = false))
        assertEquals(CharacterMood.IDLE, CharacterMood.forBot(idle, text))
    }

    @Test
    fun `busy beats unread`() {
        assertEquals(CharacterMood.WORKING, CharacterMood.forBot(idle.copy(busy = true, unread = true), null))
    }

    @Test
    fun `unread beats a question waiting on you`() {
        assertEquals(CharacterMood.NOTIFYING, CharacterMood.forBot(idle.copy(unread = true), optionsCard))
    }

    @Test
    fun `a question waiting on you beats the bot's role`() {
        val researcher = bot(title = "research", name = "Bot")
        assertEquals(CharacterMood.SEARCHING, CharacterMood.forBot(researcher, null))
        assertEquals(CharacterMood.CURIOUS, CharacterMood.forBot(researcher, optionsCard))
    }

    @Test
    fun `the role is read from name, title and description alike`() {
        assertEquals(CharacterMood.WORKING, CharacterMood.forBot(idle.copy(name = "Debug"), null))
        assertEquals(CharacterMood.WORKING, CharacterMood.forBot(idle.copy(title = "engineer"), null))
        assertEquals(CharacterMood.WORKING, CharacterMood.forBot(idle.copy(description = "writes software"), null))
    }

    @Test
    fun `each role wears the desktop's face for it`() {
        assertEquals(CharacterMood.WORKING, roleFace("engineering"))
        assertEquals(CharacterMood.SEARCHING, roleFace("investigate"))
        assertEquals(CharacterMood.EXCITED, roleFace("campaign"))
        assertEquals(CharacterMood.DROWSY, roleFace("overnight"))
        assertEquals(CharacterMood.RADAR, roleFace("uptime"))
        assertEquals(CharacterMood.SUSPICIOUS, roleFace("qa"))
        assertEquals(CharacterMood.SCARED, roleFace("compliance"))
        assertEquals(CharacterMood.PLAYFUL, roleFace("illustration"))
        assertEquals(CharacterMood.HAPPY, roleFace("onboarding"))
    }

    @Test
    fun `the first matching role wins, in the desktop's order`() {
        // "security" is checked before "design", and "code" before either
        assertEquals(CharacterMood.SCARED, roleFace("security design"))
        assertEquals(CharacterMood.WORKING, roleFace("code security design"))
    }

    @Test
    fun `a role matches whole words only`() {
        assertEquals(CharacterMood.IDLE, roleFace("codebase"))
        assertEquals(CharacterMood.IDLE, roleFace("aqua"))
        assertEquals(CharacterMood.SUSPICIOUS, roleFace("runs qa, mostly"))
        assertEquals(CharacterMood.DROWSY, roleFace("long-running errands"))
    }

    @Test
    fun `a bot with nothing to go on is idle`() {
        assertEquals(CharacterMood.IDLE, CharacterMood.forBot(idle, null))
    }

    @Test
    fun `a room always looks happy`() {
        val state = CompanionState(rooms = listOf(room()))
        assertEquals(CharacterMood.HAPPY, CharacterMood.forChat(Chat.RoomChat(room()), state))
    }

    @Test
    fun `a chat is resolved from its last visible message`() {
        val waiting = idle.copy(id = "bot-1")
        val state = CompanionState(
            bots = listOf(waiting),
            messages = mapOf(waiting.threadId to listOf(message(Message.Kind.TEXT), optionsCard)),
        )
        assertEquals(CharacterMood.CURIOUS, CharacterMood.forChat(Chat.BotChat(waiting), state))
    }

    @Test
    fun `a chat with no transcript still resolves`() {
        val state = CompanionState(bots = listOf(idle))
        assertEquals(CharacterMood.IDLE, CharacterMood.forChat(Chat.BotChat(idle), state))
    }

    private fun roleFace(description: String): CharacterMood =
        CharacterMood.forBot(idle.copy(description = description), null)

    private val failedActivity = message(
        Message.Kind.ACTIVITY,
        tool = ToolActivity(name = "shell", ok = false),
    )

    private val optionsCard = message(
        Message.Kind.OPTIONS,
        card = OptionCard(title = "Deploy?", subtitle = "", options = listOf("Yes"), requestId = "r1"),
    )

    private fun message(
        kind: Message.Kind,
        tool: ToolActivity? = null,
        card: OptionCard? = null,
    ) = Message(
        id = "m-${kind.name}-${tool?.ok}",
        role = Message.Role.BOT,
        kind = kind,
        at = 0.0,
        tool = tool,
        card = card,
    )
}
