package com.openmausbot.companion.core

import kotlinx.serialization.Serializable

/** What the computer did with a queued message the person asked to send into the running turn. */
sealed interface SteerResult {
    /** The held messages went into the running turn. [queueIds] are the queue entries that joined it. */
    data class Steered(val queueIds: List<String>) : SteerResult

    /** The turn cannot take them now; they stay queued. [blockedBy] names why, when the computer says (images, addressee). */
    data class StillQueued(val blockedBy: String?) : SteerResult

    /** The entry left the queue before the request arrived. */
    data object Gone : SteerResult
}

@Serializable
internal data class SteerBody(
    val steered: Boolean? = null,
    val queued: Boolean? = null,
    val queueIds: List<String> = emptyList(),
    val blockedBy: String? = null,
) {
    fun result(): SteerResult =
        if (steered == true) SteerResult.Steered(queueIds) else SteerResult.StillQueued(blockedBy)
}
