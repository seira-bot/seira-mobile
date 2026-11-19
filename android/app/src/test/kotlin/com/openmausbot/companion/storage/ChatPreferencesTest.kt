package com.openmausbot.companion.storage

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.openmausbot.companion.core.Chat
import com.openmausbot.companion.core.BotTask
import com.openmausbot.companion.core.forTask
import com.openmausbot.companion.ui.bot
import com.openmausbot.companion.ui.room
import com.openmausbot.companion.core.ActivityDetail
import com.openmausbot.companion.core.QuickReply
import com.openmausbot.companion.core.RosterDensity
import com.openmausbot.companion.ui.theme.ThemeMode
import com.openmausbot.companion.ui.cssHexToArgb
import kotlin.test.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Local chat choices survive the Activity/process boundary and stay non-secret. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChatPreferencesTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    private fun store(name: String): ChatPreferences = ChatPreferences(
        context.getSharedPreferences(name, Context.MODE_PRIVATE),
    )

    @Test
    fun `activity detail survives a new preferences instance`() {
        val name = "chat-activity-detail"
        store(name).setActivityDetail(ActivityDetail.HIDDEN)

        assertEquals(ActivityDetail.HIDDEN, store(name).activityDetail.value)
    }

    @Test
    fun `live captions are on until the person turns them off`() {
        assertEquals(true, store("chat-captions-fresh").liveCaptions.value)
    }

    @Test
    fun `live captions off survives a new preferences instance`() {
        val name = "chat-captions-saved"
        store(name).setLiveCaptions(false)

        assertEquals(false, store(name).liveCaptions.value)
    }

    @Test
    fun `a fresh install lists the roster compactly`() {
        assertEquals(RosterDensity.COMPACT, store("chat-density-fresh").rosterDensity.value)
    }

    @Test
    fun `the list density survives a new preferences instance`() {
        val name = "chat-density-saved"
        store(name).setRosterDensity(RosterDensity.COMFORTABLE)

        assertEquals(RosterDensity.COMFORTABLE, store(name).rosterDensity.value)
        store(name).setRosterDensity(RosterDensity.COMPACT)
        assertEquals(RosterDensity.COMPACT, store(name).rosterDensity.value)
    }

    @Test
    fun `a stored density this build cannot read falls back to compact`() {
        // The desktop's third density is avatars only; a phone has no such mode.
        val name = "chat-density-unreadable"
        context.getSharedPreferences(name, Context.MODE_PRIVATE).edit()
            .putString("companion.prefs.rosterDensity", "icons").commit()

        assertEquals(RosterDensity.COMPACT, store(name).rosterDensity.value)
    }

    @Test
    fun `an intentionally empty quick reply list stays empty after relaunch`() {
        val name = "chat-empty-quick-replies"
        store(name).setQuickReplies(emptyList())

        assertEquals(emptyList(), store(name).quickReplies.value)
    }

    @Test
    fun `custom quick replies persist in their selected order`() {
        val name = "chat-custom-quick-replies"
        val replies = listOf(
            QuickReply(id = "deploy", title = "Deploy", prompt = "Deploy staging", icon = "send"),
            QuickReply(id = "logs", title = "Logs", prompt = "Show logs", icon = "document"),
        )
        store(name).setQuickReplies(replies)

        assertEquals(replies, store(name).quickReplies.value)
    }

    @Test
    fun `theme mode defaults to auto and survives relaunch`() {
        val name = "chat-theme-mode"
        context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
        assertEquals(ThemeMode.AUTO, store(name).themeMode.value)
        store(name).setThemeMode(ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, store(name).themeMode.value)
        store(name).setThemeMode(ThemeMode.LIGHT)
        assertEquals(ThemeMode.LIGHT, store(name).themeMode.value)
    }

    @Test
    fun `an unreadable theme mode reads as auto`() {
        val name = "chat-theme-mode-bad"
        context.getSharedPreferences(name, Context.MODE_PRIVATE).edit()
            .putString("companion.prefs.themeMode", "sepia").commit()

        assertEquals(ThemeMode.AUTO, store(name).themeMode.value)
    }

    @Test
    fun `theme offers light, dark and auto`() {
        assertEquals(listOf("light", "dark", "auto"), ThemeMode.entries.map(ThemeMode::wireValue))
    }

    @Test
    fun `CSS RGBA token keeps its alpha in Compose`() {
        assertEquals(0x99FCFCFCL, cssHexToArgb("FCFCFC99"))
        assertEquals(0x99FCFCFC.toInt(), Color(cssHexToArgb("FCFCFC99")).toArgb())
    }

    @Test
    fun `last share destination is remembered per computer`() {
        val name = "chat-share-destination"
        val prefs = store(name)
        assertEquals(null, prefs.lastShareDestination("air"))
        prefs.setLastShareDestination("air", "bot:vision")
        prefs.setLastShareDestination("pro", "channel:team")
        assertEquals("bot:vision", store(name).lastShareDestination("air"))
        assertEquals("channel:team", store(name).lastShareDestination("pro"))
    }
    @Test
    fun `last bot thread survives relaunch and is isolated per computer and bot`() {
        val name = "chat-last-thread"
        val bot = bot().copy(tasks = listOf(BotTask(threadId = "chosen", title = "Chosen", createdAt = 0.0)))
        val chat = Chat.BotChat(bot)
        store(name).rememberThread(Chat.BotChat(bot.forTask("chosen")!!), "computer-one")
        val restored = store(name)
        assertEquals("chosen", restored.restoringThread(chat, "computer-one").threadId)
        assertEquals(chat.threadId, restored.restoringThread(chat, "computer-two").threadId)
        assertEquals(chat.threadId, restored.restoringThread(Chat.BotChat(bot.copy(id = "other")), "computer-one").threadId)
        assertEquals(chat.threadId, restored.restoringThread(chat, null).threadId)
    }

    @Test
    fun `deleted threads fall back and room navigation remains shared`() {
        val prefs = store("chat-deleted-thread")
        val bot = bot().copy(tasks = listOf(BotTask(threadId = "deleted", title = "Deleted", createdAt = 0.0)))
        prefs.rememberThread(Chat.BotChat(bot.forTask("deleted")!!), "computer")
        assertEquals(bot.threadId, prefs.restoringThread(Chat.BotChat(bot.copy(tasks = listOf(BotTask(threadId = bot.threadId, title = "Default", createdAt = 0.0)))), "computer").threadId)
        val room = Chat.RoomChat(room())
        prefs.rememberThread(room, "computer")
        assertEquals(room, prefs.restoringThread(room, "computer"))
    }

}
