package bot.seira.companion.glance

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.graphics.Bitmap
import bot.seira.companion.core.GlanceAsk
import bot.seira.companion.core.GlanceAskKind
import bot.seira.companion.core.GlanceBot
import bot.seira.companion.core.GlanceBudget
import bot.seira.companion.core.GlanceEntry
import bot.seira.companion.core.GlanceState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GlanceNotificationTest {
    private val context: Application = RuntimeEnvironment.getApplication()
    private val budget = GlanceBudget(lineChars = 30, lines = 6)

    @Test
    fun `a working bot shows its avatar, name and own line in the Glance channel`() {
        val avatar = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        val notification = build(entry(GlanceState.WORKING, line = "Checking the login form"), avatar = avatar)

        assertEquals("Nara", title(notification))
        assertEquals("Checking the login form", text(notification))
        assertEquals("glance", notification.channelId)
        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertNotNull(notification.getLargeIcon())
        assertEquals(Notification.CATEGORY_PROGRESS, notification.category)
        assertEquals(listOf("Open chat"), actionTitles(notification))
    }

    @Test
    fun `a working bot without a line shows the word Working`() {
        assertEquals("Working", text(build(entry(GlanceState.WORKING))))
    }

    @Test
    fun `every state has its own word`() {
        assertEquals("Needs you", text(build(entry(GlanceState.NEEDS))))
        assertEquals("Has a question", text(build(entry(GlanceState.QUESTION, ask = ask(GlanceAskKind.QUESTION, "")))))
        assertEquals("Failed", text(build(entry(GlanceState.FAILED))))
        assertEquals("Finished", text(build(entry(GlanceState.DONE))))
        assertEquals(Notification.CATEGORY_STATUS, build(entry(GlanceState.DONE)).category)
    }

    @Test
    fun `an approval whose detail fits shows Deny, Allow once and Open chat`() {
        val notification = build(entry(GlanceState.NEEDS, ask = ask(GlanceAskKind.APPROVAL, "npm test -- --filter login")))

        assertEquals(listOf("Deny", "Allow once", "Open chat"), actionTitles(notification))
        assertEquals("Approval needed\nnpm test -- --filter login", bigText(notification))
    }

    @Test
    fun `an approval without a detail shows Deny and Open chat`() {
        val notification = build(entry(GlanceState.NEEDS, ask = ask(GlanceAskKind.APPROVAL, "")))

        assertEquals(listOf("Deny", "Open chat"), actionTitles(notification))
        assertEquals("Approval needed", bigText(notification))
    }

    @Test
    fun `an approval whose detail does not fit whole shows Deny and Open chat and no detail`() {
        val detail = "rm " + "x".repeat(300)
        val notification = build(entry(GlanceState.NEEDS, ask = ask(GlanceAskKind.APPROVAL, detail)))

        assertEquals(listOf("Deny", "Open chat"), actionTitles(notification))
        assertFalse(bigText(notification).contains("xxx"))
    }

    @Test
    fun `a text that passes the char budget but lays out in more than six lines gets no Allow once`() {
        val ask = ask(GlanceAskKind.APPROVAL, (1..7).joinToString("\n") { "line$it" })
        val wide = GlanceBudget(lineChars = 1000, lines = 100)

        assertTrue(ask.canAllow(wide))
        assertFalse(GlanceNotification.canAllow(context, ask, wide, widthPx = 2000))
        assertTrue(GlanceNotification.canAllow(context, ask(GlanceAskKind.APPROVAL, "npm test"), wide, widthPx = 2000))
    }

    @Test
    fun `a question or a review card offers only Open chat`() {
        assertEquals(
            listOf("Open chat"),
            actionTitles(build(entry(GlanceState.QUESTION, ask = ask(GlanceAskKind.QUESTION, "")))),
        )
        assertEquals(
            listOf("Open chat"),
            actionTitles(build(entry(GlanceState.NEEDS, ask = ask(GlanceAskKind.REVIEW, "")))),
        )
    }

    @Test
    fun `a failed send keeps Open chat only and says so`() {
        val notification = build(
            entry(GlanceState.NEEDS, ask = ask(GlanceAskKind.APPROVAL, "npm test")),
            couldNotSend = true,
        )

        assertEquals("Could not send. Open the chat to answer.", text(notification))
        assertEquals(listOf("Open chat"), actionTitles(notification))
        assertFalse(bigText(notification).contains("npm test"))
    }

    @Test
    fun `no state shows a number or a command outside the approval detail`() {
        val states = listOf(
            entry(GlanceState.WORKING, line = "Reading the README"),
            entry(GlanceState.QUESTION, ask = ask(GlanceAskKind.QUESTION, "")),
            entry(GlanceState.FAILED),
            entry(GlanceState.DONE),
            entry(GlanceState.NEEDS, ask = ask(GlanceAskKind.REVIEW, "")),
        )
        for (entry in states) {
            val notification = build(entry)
            assertEquals(0, notification.number)
            val everything = listOf(title(notification), text(notification), bigText(notification)).joinToString(" ")
            assertFalse(everything.contains("Bash"), everything)
            assertFalse(everything.contains("npm"), everything)
        }
        assertEquals("Reading the README", text(build(states.first())))
    }

    @Test
    fun `Allow once needs the phone unlocked and Deny does not`() {
        val notification = build(entry(GlanceState.NEEDS, ask = ask(GlanceAskKind.APPROVAL, "npm test")))
        val deny = notification.actions.first { it.title.toString() == "Deny" }
        val allow = notification.actions.first { it.title.toString() == "Allow once" }

        assertFalse(deny.isAuthenticationRequired)
        assertTrue(allow.isAuthenticationRequired)
        val intent = shadowOf(allow.actionIntent).savedIntent
        assertEquals(GlanceActions.Choice.ALLOW.action, intent.action)
        assertEquals(GlanceActions.Request(GlanceActions.Choice.ALLOW, "bot-1", "thread-1", "request-1"), GlanceActions.request(intent))
        assertNotEqualsData(deny, allow)
    }

    @Test
    fun `the channel is quiet and shows no badge`() {
        build(entry(GlanceState.WORKING))
        val channel = context.getSystemService(NotificationManager::class.java).getNotificationChannel("glance")

        assertNotNull(channel)
        assertEquals(NotificationManager.IMPORTANCE_LOW, channel.importance)
        assertFalse(channel.canShowBadge())
        assertFalse(channel.shouldVibrate())
    }

    @Test
    @Config(sdk = [35])
    fun `on Android 16 the notification asks to be a Live Update`() {
        val notification = GlanceNotification.build(
            context, entry(GlanceState.WORKING, line = "Reading"), null, budget, false, promotionSdk = 36,
        )

        assertTrue(notification.extras.getBoolean("android.requestPromotedOngoing"))
        assertEquals("Working", notification.extras.getCharSequence("android.shortCriticalText").toString())
        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
    }

    @Test
    @Config(sdk = [34])
    fun `before Android 16 no promotion is requested`() {
        val notification = build(entry(GlanceState.WORKING, line = "Reading"))

        assertFalse(notification.extras.getBoolean("android.requestPromotedOngoing"))
    }

    private fun assertNotEqualsData(a: Notification.Action, b: Notification.Action) {
        assertFalse(shadowOf(a.actionIntent).savedIntent.data == shadowOf(b.actionIntent).savedIntent.data)
    }

    private fun build(entry: GlanceEntry, avatar: Bitmap? = null, couldNotSend: Boolean = false): Notification =
        GlanceNotification.build(context, entry, avatar, budget, couldNotSend)

    private fun entry(state: GlanceState, line: String = "", ask: GlanceAsk? = null) =
        GlanceEntry(GlanceBot("bot-1", "thread-1", "Nara", null, state, line, ask), 0L)

    private fun ask(kind: GlanceAskKind, detail: String) =
        GlanceAsk(kind, "request-1", "thread-1", "Approval needed", detail, 1.0)

    private fun clean(value: CharSequence?): String = value?.toString().orEmpty().replace("‎", "")
    private fun title(n: Notification) = clean(n.extras.getCharSequence(Notification.EXTRA_TITLE))
    private fun text(n: Notification) = clean(n.extras.getCharSequence(Notification.EXTRA_TEXT))
    private fun bigText(n: Notification) = clean(n.extras.getCharSequence(Notification.EXTRA_BIG_TEXT))
    private fun actionTitles(n: Notification) = n.actions.orEmpty().map { it.title.toString() }
}
