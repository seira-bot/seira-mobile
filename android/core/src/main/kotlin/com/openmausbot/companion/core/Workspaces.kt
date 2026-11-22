package com.openmausbot.companion.core

import kotlinx.serialization.Serializable

/** Whether a workspace can be opened from the phone right now. */
enum class WorkspaceAvailability { READY, WAITING, UNAVAILABLE }

/**
 * One workspace of the computer a phone is paired with: the rail of the desktop app, as the
 * phone's `GET /api/phones/self/workspaces` lists it. The route is not in `seira-bot/seira` yet,
 * so this shape follows the request for it and moves to the server's fixture once that lands.
 *
 * [state] stays the wire string: a state this build has never heard of reads as unavailable
 * through [availability] instead of failing the whole list.
 */
@Serializable
data class PhoneWorkspace(
    val id: String,
    val name: String? = null,
    val color: String? = null,
    val current: Boolean = false,
    val state: String = "ready",
    val waitingFor: String? = null,
) {
    val availability: WorkspaceAvailability
        get() = when (state) {
            "ready" -> WorkspaceAvailability.READY
            "waiting" -> WorkspaceAvailability.WAITING
            else -> WorkspaceAvailability.UNAVAILABLE
        }
}

@Serializable
data class PhoneWorkspaceList(val workspaces: List<PhoneWorkspace> = emptyList())

/** What the phone shows and offers for the workspaces a computer lists. */
object WorkspacePolicy {
    const val UNNAMED = "This computer"

    /**
     * The list as the picker may trust it: a blank or repeated id is dropped, and only the first
     * workspace marked current stays current, so two open workspaces can never both look open.
     */
    fun normalized(workspaces: List<PhoneWorkspace>): List<PhoneWorkspace> {
        val seen = mutableSetOf<String>()
        var currentSeen = false
        return workspaces.mapNotNull { workspace ->
            if (workspace.id.isBlank() || !seen.add(workspace.id)) return@mapNotNull null
            val current = workspace.current && !currentSeen
            if (current) currentSeen = true
            if (current == workspace.current) workspace else workspace.copy(current = current)
        }
    }

    /** A computer with one workspace, or one that does not list any, gets no picker. */
    fun showsPicker(workspaces: List<PhoneWorkspace>): Boolean = normalized(workspaces).size > 1

    fun displayName(workspace: PhoneWorkspace): String = workspace.name?.trim()?.takeIf { it.isNotEmpty() } ?: UNNAMED

    /** The second line of a picker row; null when the row has nothing to add. */
    fun statusLine(workspace: PhoneWorkspace): String? = when (workspace.availability) {
        WorkspaceAvailability.WAITING ->
            workspace.waitingFor?.trim()?.takeIf { it.isNotEmpty() }
                ?.let { "Waiting for $it to finish" }
                ?: "Waiting for work to finish"
        WorkspaceAvailability.UNAVAILABLE -> "Not available"
        WorkspaceAvailability.READY -> if (workspace.current) "Open" else null
    }

    /** The open workspace has nothing to switch to, and one that is not available cannot be opened. */
    fun canSwitch(workspace: PhoneWorkspace): Boolean =
        !workspace.current && workspace.availability != WorkspaceAvailability.UNAVAILABLE
}
