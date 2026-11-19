package com.openmausbot.companion.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * How a live call is presented, shared by every screen.
 *
 * [expanded] is the full call screen; when false, a running call shows as the
 * chat's call bar or the banner. [requestedBotId] is a call that a surface
 * outside the chat (Home, a shortcut, the Quick Settings tile) asked for: the
 * chat of that bot starts it through its normal path, then [consume] clears it.
 */
class LiveCallUi {
    private val _expanded = MutableStateFlow(false)
    val expanded: StateFlow<Boolean> = _expanded.asStateFlow()

    fun expand() {
        _expanded.value = true
    }

    fun minimize() {
        _expanded.value = false
    }

    private val _requestedBotId = MutableStateFlow<String?>(null)
    val requestedBotId: StateFlow<String?> = _requestedBotId.asStateFlow()

    fun request(botId: String) {
        _requestedBotId.value = botId
    }

    fun consume(botId: String): Boolean = _requestedBotId.compareAndSet(botId, null)
}
