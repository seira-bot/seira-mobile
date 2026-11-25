package com.openmausbot.companion.core

import kotlinx.serialization.Serializable

/** Whether a workspace can be opened from the phone right now. */
enum class WorkspaceAvailability { READY, WAITING, UNAVAILABLE }

/**
 * One workspace of the computer a phone is paired with: the rail of the desktop app, as the
 * phone's `GET /api/phones/self/workspaces` lists it. The shape is `PhoneWorkspace` of the
 * server's phone protocol.
 *
 * [state] stays the wire string: a state this build has never heard of reads as unavailable
 * through [availability] instead of failing the whole list.
 */
@Serializable
data class PhoneWorkspace(
    val id: String,
    /** The identity the environment of this workspace shows as its own. Absent for a workspace that never ran. */
    val environmentId: String? = null,
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

/**
 * One switch the computer accepted (`PhoneWorkspaceSwitch` of the phone protocol). [state] stays the wire string: a
 * state this build does not know reads as pending, so the phone keeps waiting and never reports an unknown result.
 */
@Serializable
data class PhoneWorkspaceSwitch(
    val id: String,
    val workspaceId: String,
    val environmentId: String? = null,
    val state: String = "pending",
) {
    val isDone: Boolean get() = state == "done"
    val isEnded: Boolean get() = state == "failed" || state == "cancelled" || state == "superseded"
}

/** The answer to a switch request: the list a moment later, and the switch when the computer started or hurried one. */
@Serializable
data class PhoneWorkspaceSwitchResponse(
    val workspaces: List<PhoneWorkspace> = emptyList(),
    val switch: PhoneWorkspaceSwitch? = null,
)

@Serializable
data class PhoneWorkspaceSwitchStatus(val switch: PhoneWorkspaceSwitch)

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

/** What a switch the person asked for on this phone came to. Only [Switched] changes what the phone trusts. */
sealed interface WorkspaceSwitchResult {
    /** The computer started it, but it waits (for a bot to finish its turn). Call `finishWorkspaceSwitch` later. */
    data class Waiting(val workspaces: List<PhoneWorkspace>) : WorkspaceSwitchResult

    /** The computer ended the switch without opening the workspace. The pin stayed. */
    data class Ended(val state: String) : WorkspaceSwitchResult

    /** The switch finished and the phone now pins [environmentId], in one write. */
    data class Switched(val environmentId: String) : WorkspaceSwitchResult

    /** The pairing has no pinned identity, or the phone did not know the target identity in advance: nothing started and no bearer went out. The person pairs again. */
    data object NeedsPairing : WorkspaceSwitchResult

    /** The check failed. The old pin stays and no bearer went to the target. */
    data class Rejected(val reason: WorkspaceSwitchOutcome.Reason) : WorkspaceSwitchResult
}

/**
 * Test and app seams of a phone-started switch.
 *
 * [proof] reads how the computer proves, after the switch, that it is the one the phone paired with. It returns a
 * completion whose `authenticatedByPairing` is true only for an answer that a server at the same address cannot forge
 * (a proof from the paired computer's key). The contract of seira#525 has no such proof yet, so the default returns null
 * and a switch keeps the old pin. An address, a redirect or an identity probe is not a proof.
 */
class WorkspaceSwitchSeams(
    val now: () -> Long = System::currentTimeMillis,
    val proof: suspend (CompanionClient, PhoneWorkspaceSwitch) -> WorkspaceSwitchCompletion? = { _, _ -> null },
)
