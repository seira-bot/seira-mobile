package com.openmausbot.companion.update

import com.openmausbot.companion.audio.LiveCallSnapshot
import com.openmausbot.companion.core.CompanionState

/** An update restarts the app, so it waits while the person is in the middle of something. */
object UpdateGate {
    fun busy(
        call: LiveCallSnapshot,
        pairingRequested: Boolean,
        hasPairingInvite: Boolean,
        state: CompanionState,
    ): Boolean = call.active ||
        pairingRequested ||
        hasPairingInvite ||
        state.pendingQueued.values.any { it.isNotEmpty() } ||
        state.pendingEdits.isNotEmpty()
}
