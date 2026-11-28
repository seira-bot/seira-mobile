package bot.seira.companion.ui

import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.mutableLongStateOf

/**
 * Publishes frame times at 30 fps as state that the draw phase reads.
 * It holds [onFrame] as a field so a frame loop allocates nothing per tick.
 * The origin survives a stopped loop, so a clock that restarts continues its own timeline.
 */
internal class FrameClock {
    /** Nanoseconds since the first frame. */
    val nanos: MutableLongState = mutableLongStateOf(0L)

    val onFrame: (Long) -> Unit = ::publish

    private var origin = NO_ORIGIN
    private var published = 0L

    private fun publish(now: Long) {
        if (origin == NO_ORIGIN) origin = now
        val elapsed = now - origin
        if (elapsed - published >= FRAME_INTERVAL_NANOS) {
            published = elapsed
            nanos.longValue = elapsed
        }
    }
}

private const val FRAME_INTERVAL_NANOS = 1_000_000_000L / 30
private const val NO_ORIGIN = Long.MIN_VALUE
