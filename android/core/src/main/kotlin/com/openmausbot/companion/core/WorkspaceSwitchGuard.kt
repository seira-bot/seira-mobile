package com.openmausbot.companion.core

import java.util.UUID

/**
 * What the paired computer sends back to finish a switch the phone started. [authenticatedByPairing]
 * is true only when the answer came over the authenticated channel of the same pairing. An address,
 * a redirect or an id from an unauthenticated probe never sets it.
 */
data class WorkspaceSwitchCompletion(
    val operationId: String,
    val workspaceId: String,
    val environmentId: String,
    val authenticatedByPairing: Boolean,
)

sealed interface WorkspaceSwitchStart {
    data class Started(val operationId: String) : WorkspaceSwitchStart

    /** The phone does not know the target identity in advance, so it keeps the pin and asks for pairing again. */
    data object NeedsPairing : WorkspaceSwitchStart
}

sealed interface WorkspaceSwitchOutcome {
    data class Repinned(val environmentId: String) : WorkspaceSwitchOutcome

    /** The pin stays as it was. */
    data class Rejected(val reason: Reason) : WorkspaceSwitchOutcome

    enum class Reason {
        NO_PENDING_SWITCH,
        PAIRING_CHANGED,
        EXPIRED,
        VERIFICATION_FAILED,
        WRONG_OPERATION,
        WRONG_WORKSPACE,
        WRONG_ENVIRONMENT,
        NOT_AUTHENTICATED,
    }
}

/**
 * The pin of one pairing: the server identity the phone expects at the address. A different identity at
 * the same address stops the connection, as it does without this class. Reading the identity is a check at
 * one moment. It does not authenticate the peer, so it does not guarantee where a later request goes. The pin moves only after a
 * switch that this phone started, bound to this pairing and this target, finishes with an
 * authenticated completion for the same operation. Every pending switch is used once, whatever the
 * result. The caller stores the new pin in one write.
 *
 * The target identity comes from the authenticated workspace list of the old connection. The workspace
 * id alone does not prove it.
 */
class WorkspaceSwitchGuard(
    initialPin: String,
    private val now: () -> Long,
    private val ttlMillis: Long = DEFAULT_TTL_MILLIS,
    private val newOperationId: () -> String = { UUID.randomUUID().toString() },
) {
    private class Pending(
        var operationId: String,
        val pairingId: String,
        val workspaceId: String,
        val targetEnvironmentId: String,
        val expiresAt: Long,
    )

    private val lock = Any()
    private var currentPin = initialPin
    private var pending: Pending? = null

    val pin: String get() = synchronized(lock) { currentPin }

    /** Call this only after the user picked [workspaceId] on this phone. */
    fun begin(pairingId: String, workspaceId: String, targetEnvironmentId: String?): WorkspaceSwitchStart =
        synchronized(lock) {
            pending = null
            val target = targetEnvironmentId?.trim().orEmpty()
            if (workspaceId.isBlank() || target.isEmpty()) return WorkspaceSwitchStart.NeedsPairing
            val operationId = newOperationId()
            pending = Pending(operationId, pairingId, workspaceId, target, now() + ttlMillis)
            WorkspaceSwitchStart.Started(operationId)
        }

    /**
     * Binds the operation id the computer gave to the switch this phone started. It binds only when the answer names the
     * same workspace and the same target identity as the phone read before the switch. Anything else ends the pending
     * switch: the pin stays as it was.
     */
    fun bind(pairingId: String, operationId: String, workspaceId: String, environmentId: String?): Boolean =
        synchronized(lock) {
            val switch = pending ?: return false
            val ok = switch.pairingId == pairingId && operationId.isNotBlank() && workspaceId == switch.workspaceId &&
                environmentId == switch.targetEnvironmentId
            if (!ok) {
                pending = null
                return false
            }
            switch.operationId = operationId
            true
        }

    /** A null [completion] means the check did not finish: no answer, an error, or a failed signature. */
    fun complete(pairingId: String, completion: WorkspaceSwitchCompletion?): WorkspaceSwitchOutcome =
        synchronized(lock) {
            val switch = pending ?: return reject(WorkspaceSwitchOutcome.Reason.NO_PENDING_SWITCH)
            pending = null
            val reason = when {
                switch.pairingId != pairingId -> WorkspaceSwitchOutcome.Reason.PAIRING_CHANGED
                now() >= switch.expiresAt -> WorkspaceSwitchOutcome.Reason.EXPIRED
                completion == null -> WorkspaceSwitchOutcome.Reason.VERIFICATION_FAILED
                completion.operationId != switch.operationId -> WorkspaceSwitchOutcome.Reason.WRONG_OPERATION
                !completion.authenticatedByPairing -> WorkspaceSwitchOutcome.Reason.NOT_AUTHENTICATED
                completion.workspaceId != switch.workspaceId -> WorkspaceSwitchOutcome.Reason.WRONG_WORKSPACE
                completion.environmentId != switch.targetEnvironmentId -> WorkspaceSwitchOutcome.Reason.WRONG_ENVIRONMENT
                else -> null
            }
            if (reason != null) return WorkspaceSwitchOutcome.Rejected(reason)
            currentPin = switch.targetEnvironmentId
            WorkspaceSwitchOutcome.Repinned(currentPin)
        }

    fun cancel() = synchronized(lock) { pending = null }

    /** True when the identity read at the address is the pin. A check at one moment, not authentication of the peer. */
    fun bearerAllowed(observedEnvironmentId: String): Boolean =
        synchronized(lock) { observedEnvironmentId == currentPin }

    private fun reject(reason: WorkspaceSwitchOutcome.Reason): WorkspaceSwitchOutcome =
        WorkspaceSwitchOutcome.Rejected(reason)

    companion object {
        const val DEFAULT_TTL_MILLIS = 60_000L
    }
}
