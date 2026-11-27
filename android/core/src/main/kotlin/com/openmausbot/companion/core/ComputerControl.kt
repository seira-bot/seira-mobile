package com.openmausbot.companion.core

import kotlinx.serialization.Serializable

enum class ComputerControlAction(val wire: String) {
    TAKE("take"),
    RELEASE("release"),
    CHECK("check"),
}

/**
 * Who is driving a bot's computer. [owned] is present when the request named a
 * control lease, and says whether that lease holds it.
 */
@Serializable
data class ComputerControlState(
    val held: Boolean,
    val owned: Boolean? = null,
    val acquired: Boolean? = null,
    val released: Boolean? = null,
    val helpReason: String? = null,
    val heldSinceMs: Double? = null,
)
