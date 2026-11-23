package com.openmausbot.companion.ui.launch

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Hand-over between the system splash and [LaunchVisor].
 *
 * @param fast the launch did not come from the launcher; skip the full blink.
 * @param reduceMotion the system turned animations off.
 * @param brandingFromSplash the system splash already shows the wordmark.
 */
class LaunchVisorState(
    val fast: Boolean,
    val reduceMotion: Boolean,
    val brandingFromSplash: Boolean,
) {
    var startOffsetMs by mutableStateOf<Long?>(null)
        private set

    var forcedFast by mutableStateOf(false)
        private set

    var finished by mutableStateOf(false)
        private set

    val started: Boolean get() = startOffsetMs != null

    /** Later calls do nothing. */
    fun start(offsetMs: Long, forceFast: Boolean = false) {
        if (started) return
        forcedFast = forceFast
        startOffsetMs = offsetMs
    }

    fun finish() {
        finished = true
    }
}
