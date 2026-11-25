package com.openmausbot.companion.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The line under the name of a picker row. */
enum class WorkspaceStatus { NONE, OPEN, WAITING, UNAVAILABLE }

/** One row of the picker. The picker is read-only: a row shows the state of a workspace and offers no action. */
data class WorkspaceRow(
    val workspace: PhoneWorkspace,
    val status: WorkspaceStatus,
)

object WorkspacePicker {
    fun currentId(workspaces: List<PhoneWorkspace>): String? = workspaces.firstOrNull { it.current }?.id

    fun isWaiting(workspaces: List<PhoneWorkspace>): Boolean =
        workspaces.any { it.availability == WorkspaceAvailability.WAITING }

    /**
     * The rows of the picker. No row switches or cancels anything: a switch needs the guarded flow of the phone session
     * and an authenticated transport (seira#540, seira#546), and a cancel needs an explicit cancel bound to the pending
     * operation. Until then the picker only shows what the computer lists.
     */
    fun rows(workspaces: List<PhoneWorkspace>): List<WorkspaceRow> =
        WorkspacePolicy.normalized(workspaces).map { workspace ->
            val status = when {
                workspace.current -> WorkspaceStatus.OPEN
                workspace.availability == WorkspaceAvailability.UNAVAILABLE -> WorkspaceStatus.UNAVAILABLE
                workspace.availability == WorkspaceAvailability.WAITING -> WorkspaceStatus.WAITING
                else -> WorkspaceStatus.NONE
            }
            WorkspaceRow(workspace, status)
        }

    /** The name a row shows next to "Waiting for"; null when the computer named nobody. */
    fun waitingFor(workspace: PhoneWorkspace): String? = workspace.waitingFor?.trim()?.takeIf { it.isNotEmpty() }
}

/** What the picker model reads from the session. Every call names the pairing it is for. */
interface WorkspaceSource {
    /**
     * The list, or null when the computer offers no picker (404, or 403 for a pairing that may not read it). It throws
     * when [pairingId] is not the active pairing, before or after the read: a list of another computer is never returned.
     */
    suspend fun workspaces(pairingId: String): List<PhoneWorkspace>?

    /** The computer now serves another workspace: drop what the old one showed and read the new one. A stale pairing does nothing. */
    suspend fun workspaceChanged(pairingId: String)
}

data class WorkspacePickerState(
    val workspaces: List<PhoneWorkspace> = emptyList(),
) {
    /** One entry, or none, gets no picker. */
    val visible: Boolean get() = WorkspacePolicy.showsPicker(workspaces)
    val rows: List<WorkspaceRow> get() = WorkspacePicker.rows(workspaces)
    val current: PhoneWorkspace? get() = WorkspacePolicy.normalized(workspaces).firstOrNull { it.current }

    /** True while a switch waits, so the sheet reads the list again until it ends. */
    val polling: Boolean get() = visible && WorkspacePicker.isWaiting(workspaces)
}

/**
 * The read-only state of the workspace picker for one pairing. A model never outlives its pairing: the screen makes a new
 * one when the pairing changes, and a model that is still alive drops every answer that arrives after its pairing is no
 * longer the active one ([activePairing]). It has no action: it never asks the computer to switch, open or cancel.
 */
class WorkspacePickerModel(
    private val pairingId: String,
    private val source: WorkspaceSource,
    private val activePairing: () -> String?,
) {
    private val _state = MutableStateFlow(WorkspacePickerState())
    val state: StateFlow<WorkspacePickerState> = _state.asStateFlow()

    private val current: Boolean get() = activePairing() == pairingId

    /** Reads the list. A read that fails keeps the last list of this pairing; an answer for another pairing is dropped. */
    suspend fun refresh() {
        if (!current) return
        val list = try {
            source.workspaces(pairingId)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            return
        }
        if (!current) return
        adopt(list.orEmpty())
    }

    private suspend fun adopt(list: List<PhoneWorkspace>) {
        val normalized = WorkspacePolicy.normalized(list)
        val before = WorkspacePicker.currentId(_state.value.workspaces)
        val after = WorkspacePicker.currentId(normalized)
        _state.value = WorkspacePickerState(normalized)
        if (before != null && after != null && before != after && current) source.workspaceChanged(pairingId)
    }
}
