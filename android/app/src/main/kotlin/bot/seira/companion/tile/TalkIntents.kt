package bot.seira.companion.tile

import android.content.Context
import android.content.Intent
import bot.seira.companion.MainActivity
import bot.seira.companion.core.Bot
import bot.seira.companion.core.Session
import bot.seira.companion.shortcuts.talkChief
import bot.seira.companion.ui.LiveCallUi
import bot.seira.companion.ui.PendingThreadNavigation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** The request that starts a call with the chief of staff, from the tile or a launcher shortcut. */
object TalkIntents {
    const val ACTION_TALK = "bot.seira.companion.action.TALK"
    const val CHIEF_WAIT_MS = 10_000L
    const val REQUEST_TTL_MS = 15_000L

    fun intent(context: Context): Intent =
        Intent(context, MainActivity::class.java).apply {
            action = ACTION_TALK
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

    /** The chief of staff once the roster is known; null when nothing is paired or no chief exists. */
    suspend fun awaitChief(session: Session, waitMs: Long = CHIEF_WAIT_MS): Bot? {
        session.awaitRestored()
        if (session.connection.value == null) return null
        return withTimeoutOrNull(waitMs) {
            session.state.map { it.talkChief() }.first { it != null }
        }
    }
}

/** Turns a TALK intent into a call request for the chief of staff and opens that chat. */
class TalkRequestHandler(
    private val session: Session,
    private val callUi: LiveCallUi,
    private val navigation: PendingThreadNavigation,
    private val scope: CoroutineScope,
    private val chiefWaitMs: Long = TalkIntents.CHIEF_WAIT_MS,
    private val requestTtlMs: Long = TalkIntents.REQUEST_TTL_MS,
) {
    fun handle(): Job = scope.launch {
        val chief = TalkIntents.awaitChief(session, chiefWaitMs) ?: return@launch
        callUi.request(chief.id)
        navigation.offer(chief.id, chief.threadId, fresh = true)
        launch {
            delay(requestTtlMs)
            callUi.consume(chief.id)
        }
    }
}
