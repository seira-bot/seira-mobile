package bot.seira.companion.glance

import android.content.Context
import android.graphics.Bitmap
import androidx.core.app.NotificationManagerCompat
import bot.seira.companion.core.Bot
import bot.seira.companion.core.CompanionState
import bot.seira.companion.core.Glance
import bot.seira.companion.core.GlanceBot
import bot.seira.companion.core.GlanceBudget
import bot.seira.companion.core.GlanceMemory
import bot.seira.companion.core.Session
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Keeps one Glance notification in step with the phone's own state. */
class GlanceNotifier(
    context: Context,
    private val state: StateFlow<CompanionState>,
    private val status: StateFlow<Session.Status>,
    private val avatarFor: suspend (Bot) -> Bitmap?,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val appContext = context.applicationContext
    private val manager = NotificationManagerCompat.from(appContext)
    private val failedRequests = MutableStateFlow<Set<String>>(emptySet())
    private var memory = GlanceMemory()

    /** What the shown notification was built from. [UNKNOWN] makes the first cancel run. */
    private var shown: Any? = UNKNOWN
    private var postedAt: Long? = null

    fun budget(): GlanceBudget = GlanceNotification.budget(appContext.resources.configuration.fontScale)

    fun markFailed(requestId: String) {
        failedRequests.value = failedRequests.value + requestId
    }

    fun start(scope: CoroutineScope) {
        scope.launch {
            val changes: Flow<Triple<CompanionState, Session.Status, Set<String>>> =
                combine(state, status, failedRequests) { s, st, failed -> Triple(s, st, failed) }
            changes.collectLatest { (current, currentStatus, failed) ->
                var next: Long?
                do {
                    next = refresh(current, currentStatus, failed)
                    if (next != null) delay((next - now()).coerceAtLeast(0))
                } while (next != null)
            }
        }
    }

    private suspend fun refresh(current: CompanionState, currentStatus: Session.Status, failed: Set<String>): Long? {
        if (currentStatus !is Session.Status.Live || !manager.areNotificationsEnabled()) {
            memory = GlanceMemory()
            failedRequests.value = emptySet()
            clear()
            return null
        }
        val bots = Glance.bots(current)
        val waiting = bots.mapNotNull { it.ask?.requestId }.toSet()
        if (failed.any { it !in waiting }) failedRequests.value = failed.intersect(waiting)
        val (nextMemory, view) = Glance.step(memory, bots, now())
        memory = nextMemory
        val focus = view.focus
        if (focus == null) {
            clear()
            return view.nextAt
        }
        val budget = budget()
        val couldNotSend = focus.bot.ask?.requestId in failed
        val avatar = current.bot(focus.bot.botId)?.let { withTimeoutOrNull(AVATAR_WAIT_MS) { avatarFor(it) } }
        val key = Content(focus.bot, budget, couldNotSend, avatar?.let(System::identityHashCode))
        if (key != shown) {
            val previous = shown as? Content
            val postedAt = postedAt
            if (previous != null && postedAt != null && previous == key.copy(bot = key.bot.copy(line = previous.bot.line))) {
                val wait = postedAt + LINE_UPDATE_MS - now()
                if (wait > 0) delay(wait)
            }
            val notification = GlanceNotification.build(appContext, focus, avatar, budget, couldNotSend)
            try {
                manager.notify(GlanceNotification.TAG, GlanceNotification.NOTIFICATION_ID, notification)
                shown = key
                this.postedAt = now()
            } catch (_: SecurityException) {
                shown = UNKNOWN
            }
        }
        return view.nextAt
    }

    private fun clear() {
        if (shown == null) return
        manager.cancel(GlanceNotification.TAG, GlanceNotification.NOTIFICATION_ID)
        shown = null
    }

    private data class Content(
        val bot: GlanceBot,
        val budget: GlanceBudget,
        val couldNotSend: Boolean,
        val avatar: Int?,
    )

    companion object {
        private const val AVATAR_WAIT_MS = 1_500L

        /** Android 8 and later drops notification updates past about 5 a second, and a dropped last update would leave a stale line. */
        const val LINE_UPDATE_MS = 1_000L
        private val UNKNOWN = Any()
    }
}
