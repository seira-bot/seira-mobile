package com.openmausbot.companion.widget

import com.openmausbot.companion.core.ActivityDetail
import com.openmausbot.companion.core.Session
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch

/**
 * Keeps the widget snapshot in step with the session: it writes the updates,
 * draws the faces, asks the widget to redraw, and clears everything on unpair.
 *
 * State arrives in [windowMs] windows. A working row whose line alone changed
 * republishes at most every 30 seconds, so a streaming turn does not redraw the
 * home screen on each batch.
 */
class WidgetSync(
    private val session: Session,
    private val store: WidgetSnapshotStore,
    private val detail: StateFlow<ActivityDetail>,
    private val faces: (List<WidgetSnapshot.Row>) -> Unit,
    private val refresh: suspend () -> Unit,
    private val freshness: WidgetFreshnessScheduler,
    private val clock: () -> Long = System::currentTimeMillis,
    private val windowMs: Long = WINDOW_MS,
) {
    private var lastWritten: WidgetSnapshot? = null
    private var sinceClock = WidgetSinceClock(store.read())

    @OptIn(FlowPreview::class)
    suspend fun run() = coroutineScope {
        launch {
            session.restoreState
                .map { it is Session.RestoreState.Unpaired }
                .distinctUntilChanged()
                .collect { unpaired -> if (unpaired) clear() }
        }
        launch {
            combine(
                session.connection.map { it?.id }.distinctUntilChanged(),
                session.state,
                detail,
            ) { id, state, detail -> Triple(id, state, detail) }
                .sample(windowMs)
                .collect { (id, state, detail) ->
                    if (id == null) return@collect
                    publish(state.widgetSnapshot(id, detail, clock(), sinceClock))
                }
        }
    }

    internal suspend fun publish(snapshot: WidgetSnapshot) {
        if (!snapshot.shouldReplace(lastWritten)) return
        try {
            faces(snapshot.rows)
            store.write(snapshot)
        } catch (_: Exception) {
            return
        }
        lastWritten = snapshot
        try {
            freshness.schedule(snapshot.writtenAt, clock())
        } catch (_: Exception) {
        }
        redraw()
    }

    private suspend fun clear() {
        lastWritten = null
        sinceClock = WidgetSinceClock()
        store.remove()
        redraw()
    }

    private suspend fun redraw() {
        try {
            refresh()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
        }
    }

    companion object {
        const val WINDOW_MS = 400L
    }
}
