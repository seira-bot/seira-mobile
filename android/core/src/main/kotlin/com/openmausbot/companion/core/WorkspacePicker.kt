package com.openmausbot.companion.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** What the person asks the computer to do from one picker row. */
enum class WorkspaceAction {
    /** Open a workspace that is ready. */
    OPEN,

    /** Open a workspace that waits, without waiting for the bot to finish. */
    OPEN_NOW,

    /** Stop a switch that waits and stay on the open workspace. */
    CANCEL_WAIT,
}

/** The line under the name of a picker row. */
enum class WorkspaceStatus { NONE, OPEN, WAITING, UNAVAILABLE, PAIR_AGAIN }

/**
 * One row of the picker. [tap] is what the whole row does and [button] is what its own button does; both are null
 * when the row offers nothing.
 */
data class WorkspaceRow(
    val workspace: PhoneWorkspace,
    val status: WorkspaceStatus,
    val tap: WorkspaceAction?,
    val button: WorkspaceAction?,
) {
    fun offers(action: WorkspaceAction): Boolean = tap == action || button == action
}

/** Why the last request did not do what the person asked. */
enum class WorkspaceNotice {
    /** 403: the owner did not let this phone open other workspaces. */
    TURNED_OFF,

    /** 409: the workspace cannot be opened now. */
    UNAVAILABLE,

    /** 404: the workspace is not on the computer any more. */
    GONE,

    /** The computer did not answer, or answered with an error this build has no words for. */
    FAILED,

    /** The phone cannot prove that the computer it reaches after the switch is the one it paired with. */
    NEEDS_PAIRING,
}

object WorkspacePicker {
    /**
     * Whether this phone may ask the computer to open another workspace. A pairing that pins the identity of the computer
     * would meet a different identity after the switch, and the phone has no proof yet for that (seira#540). Such a pairing
     * never starts a switch and never moves its pin: the picker shows the pairing again instead.
     */
    fun canOpen(connection: Connection?): Boolean = connection != null && !connection.pairedWithServer

    fun currentId(workspaces: List<PhoneWorkspace>): String? = workspaces.firstOrNull { it.current }?.id

    fun isWaiting(workspaces: List<PhoneWorkspace>): Boolean =
        workspaces.any { it.availability == WorkspaceAvailability.WAITING }

    /** The rows of the picker. With [canOpen] false, no row starts a switch. */
    fun rows(workspaces: List<PhoneWorkspace>, canOpen: Boolean): List<WorkspaceRow> {
        val list = WorkspacePolicy.normalized(workspaces)
        val waiting = isWaiting(list)
        return list.map { workspace ->
            when {
                workspace.current ->
                    WorkspaceRow(
                        workspace,
                        WorkspaceStatus.OPEN,
                        tap = null,
                        button = if (waiting) WorkspaceAction.CANCEL_WAIT else null,
                    )
                workspace.availability == WorkspaceAvailability.UNAVAILABLE ->
                    WorkspaceRow(workspace, WorkspaceStatus.UNAVAILABLE, tap = null, button = null)
                workspace.availability == WorkspaceAvailability.WAITING ->
                    WorkspaceRow(
                        workspace,
                        WorkspaceStatus.WAITING,
                        tap = null,
                        button = if (canOpen) WorkspaceAction.OPEN_NOW else null,
                    )
                canOpen -> WorkspaceRow(workspace, WorkspaceStatus.NONE, tap = WorkspaceAction.OPEN, button = null)
                else -> WorkspaceRow(workspace, WorkspaceStatus.PAIR_AGAIN, tap = null, button = null)
            }
        }
    }

    /** The name a row shows next to "Waiting for"; null when the computer named nobody. */
    fun waitingFor(workspace: PhoneWorkspace): String? = workspace.waitingFor?.trim()?.takeIf { it.isNotEmpty() }

    fun notice(error: APIError.Status): WorkspaceNotice = when (error.code) {
        403 -> WorkspaceNotice.TURNED_OFF
        409 -> WorkspaceNotice.UNAVAILABLE
        404 -> WorkspaceNotice.GONE
        else -> WorkspaceNotice.FAILED
    }
}

/** What the picker model reads from and asks of the session. */
interface WorkspaceSource {
    /** The list, or null when the computer offers no picker (404, or 403 for a pairing that may not read it). */
    suspend fun workspaces(): List<PhoneWorkspace>?

    suspend fun requestOpen(id: String, now: Boolean): List<PhoneWorkspace>

    /** The computer now serves another workspace: drop what the old one showed and read the new one. */
    suspend fun workspaceChanged()
}

data class WorkspacePickerState(
    val workspaces: List<PhoneWorkspace> = emptyList(),
    val canOpen: Boolean = false,
    /** The workspace a request is out for. */
    val busyId: String? = null,
    val notice: WorkspaceNotice? = null,
) {
    /** One entry, or none, gets no picker. */
    val visible: Boolean get() = WorkspacePolicy.showsPicker(workspaces)
    val rows: List<WorkspaceRow> get() = WorkspacePicker.rows(workspaces, canOpen)
    val current: PhoneWorkspace? get() = WorkspacePolicy.normalized(workspaces).firstOrNull { it.current }

    /** True while a switch waits, so the sheet reads the list again until it ends. */
    val polling: Boolean get() = visible && WorkspacePicker.isWaiting(workspaces)
}

/**
 * The state of the workspace picker. It never touches the pin of a pairing: [WorkspacePicker.canOpen] decides if a row may
 * start a switch, and a row that may not shows the pairing again.
 */
class WorkspacePickerModel(
    private val source: WorkspaceSource,
    private val canOpen: () -> Boolean,
) {
    private val _state = MutableStateFlow(WorkspacePickerState())
    val state: StateFlow<WorkspacePickerState> = _state.asStateFlow()

    fun dismissNotice() = _state.update { it.copy(notice = null) }

    /** Reads the list. A list read that fails keeps the last list: the person is still on the same workspace. */
    suspend fun refresh() {
        val list = try {
            source.workspaces()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            return
        }
        adopt(list.orEmpty())
    }

    suspend fun perform(action: WorkspaceAction, id: String) {
        val snapshot = _state.value
        if (snapshot.busyId != null) return
        val row = snapshot.rows.firstOrNull { it.workspace.id == id }
        if (row == null || !row.offers(action)) {
            if (row?.status == WorkspaceStatus.PAIR_AGAIN) _state.update { it.copy(notice = WorkspaceNotice.NEEDS_PAIRING) }
            return
        }
        _state.update { it.copy(busyId = id, notice = null) }
        try {
            adopt(source.requestOpen(id, now = action == WorkspaceAction.OPEN_NOW))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: APIError.Status) {
            _state.update { it.copy(notice = WorkspacePicker.notice(error)) }
            refresh()
        } catch (_: Exception) {
            _state.update { it.copy(notice = WorkspaceNotice.FAILED) }
        } finally {
            _state.update { it.copy(busyId = null) }
        }
    }

    private suspend fun adopt(list: List<PhoneWorkspace>) {
        val normalized = WorkspacePolicy.normalized(list)
        val before = WorkspacePicker.currentId(_state.value.workspaces)
        val after = WorkspacePicker.currentId(normalized)
        _state.update { it.copy(workspaces = normalized, canOpen = canOpen()) }
        if (before != null && after != null && before != after) source.workspaceChanged()
    }
}
