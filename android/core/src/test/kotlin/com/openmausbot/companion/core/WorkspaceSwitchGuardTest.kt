package com.openmausbot.companion.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** The pin of a pairing across a workspace switch. The Swift twin is `WorkspaceSwitchGuardTests.swift`. */
class WorkspaceSwitchGuardTest {
    private var clock = 1_000L
    private var counter = 0
    private val guard = WorkspaceSwitchGuard(
        initialPin = OLD,
        now = { clock },
        ttlMillis = 60_000,
        newOperationId = { "op-${++counter}" },
    )

    private fun start(target: String? = NEW): String =
        assertIs<WorkspaceSwitchStart.Started>(guard.begin(PAIRING, "trading", target)).operationId

    private fun completion(
        operationId: String,
        workspaceId: String = "trading",
        environmentId: String = NEW,
        authenticated: Boolean = true,
    ) = WorkspaceSwitchCompletion(operationId, workspaceId, environmentId, authenticated)

    private fun assertRejected(reason: WorkspaceSwitchOutcome.Reason, outcome: WorkspaceSwitchOutcome) {
        assertEquals(WorkspaceSwitchOutcome.Rejected(reason), outcome)
        assertKeepsOldPin()
    }

    private fun assertKeepsOldPin() {
        assertEquals(OLD, guard.pin)
        assertTrue(guard.bearerAllowed(OLD))
        assertFalse(guard.bearerAllowed(NEW))
    }

    @Test
    fun aSwitchThePhoneStartedAndVerifiedMovesThePin() {
        val operation = start()
        assertEquals(WorkspaceSwitchOutcome.Repinned(NEW), guard.complete(PAIRING, completion(operation)))
        assertEquals(NEW, guard.pin)
        assertTrue(guard.bearerAllowed(NEW))
        assertFalse(guard.bearerAllowed(OLD))
    }

    @Test
    fun anIdChangeWithoutASwitchThePhoneStartedStopsAsToday() {
        assertFalse(guard.bearerAllowed(NEW))
        assertRejected(
            WorkspaceSwitchOutcome.Reason.NO_PENDING_SWITCH,
            guard.complete(PAIRING, completion("op-from-another-device")),
        )
    }

    @Test
    fun aFailedVerificationKeepsTheOldPinAndSendsNoTokenToTheTarget() {
        start()
        assertRejected(WorkspaceSwitchOutcome.Reason.VERIFICATION_FAILED, guard.complete(PAIRING, null))
    }

    @Test
    fun aServerAtTheSameAddressThatClaimsAnotherIdIsRejected() {
        val operation = start()
        assertRejected(
            WorkspaceSwitchOutcome.Reason.WRONG_ENVIRONMENT,
            guard.complete(PAIRING, completion(operation, environmentId = "env-other")),
        )
    }

    @Test
    fun aCompletionForAnotherWorkspaceIsRejected() {
        val operation = start()
        assertRejected(
            WorkspaceSwitchOutcome.Reason.WRONG_WORKSPACE,
            guard.complete(PAIRING, completion(operation, workspaceId = "default")),
        )
    }

    @Test
    fun aCompletionThatTheTargetDidNotSendOverThePairingIsRejected() {
        val operation = start()
        assertRejected(
            WorkspaceSwitchOutcome.Reason.NOT_AUTHENTICATED,
            guard.complete(PAIRING, completion(operation, authenticated = false)),
        )
    }

    @Test
    fun aCompletionForAnotherOperationIsRejected() {
        start()
        assertRejected(WorkspaceSwitchOutcome.Reason.WRONG_OPERATION, guard.complete(PAIRING, completion("op-99")))
    }

    @Test
    fun anExpiredSwitchIsRejected() {
        val operation = start()
        clock += 60_000
        assertRejected(WorkspaceSwitchOutcome.Reason.EXPIRED, guard.complete(PAIRING, completion(operation)))
    }

    @Test
    fun aCompletionCannotBeReplayed() {
        val operation = start()
        assertIs<WorkspaceSwitchOutcome.Repinned>(guard.complete(PAIRING, completion(operation)))
        assertEquals(
            WorkspaceSwitchOutcome.Rejected(WorkspaceSwitchOutcome.Reason.NO_PENDING_SWITCH),
            guard.complete(PAIRING, completion(operation)),
        )
        assertEquals(NEW, guard.pin)
    }

    @Test
    fun aFailedSwitchCannotBeRetriedWithTheSameOperation() {
        val operation = start()
        guard.complete(PAIRING, null)
        assertRejected(WorkspaceSwitchOutcome.Reason.NO_PENDING_SWITCH, guard.complete(PAIRING, completion(operation)))
    }

    @Test
    fun aChangedPairingOrACancelledSwitchIsRejected() {
        var operation = start()
        assertRejected(
            WorkspaceSwitchOutcome.Reason.PAIRING_CHANGED,
            guard.complete("pairing-2", completion(operation)),
        )
        operation = start()
        guard.cancel()
        assertRejected(WorkspaceSwitchOutcome.Reason.NO_PENDING_SWITCH, guard.complete(PAIRING, completion(operation)))
    }

    @Test
    fun aTargetIdentityThePhoneDoesNotKnowKeepsThePinAndAsksForPairing() {
        assertEquals(WorkspaceSwitchStart.NeedsPairing, guard.begin(PAIRING, "trading", null))
        assertEquals(WorkspaceSwitchStart.NeedsPairing, guard.begin(PAIRING, "trading", " "))
        assertRejected(WorkspaceSwitchOutcome.Reason.NO_PENDING_SWITCH, guard.complete(PAIRING, completion("op-1")))
    }

    private companion object {
        const val OLD = "env-old"
        const val NEW = "env-new"
        const val PAIRING = "pairing-1"
    }
}
