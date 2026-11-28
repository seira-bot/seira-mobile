package bot.seira.companion.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

/**
 * The card a burst shows in its chat: a preview until the person presses Run,
 * then one progress card patched in place. Port of `BurstCardData` in
 * `phone-protocol`. [phase] and the keys of [counts] stay strings so a status
 * a newer computer adds never breaks decoding.
 */
@Serializable
data class BurstCard(
    val burstId: String = "",
    val botId: String = "",
    val task: String = "",
    val phase: String = "",
    val total: Int = 0,
    val requested: Int = 0,
    val parallel: Int = 0,
    val taskLimit: Int? = null,
    val smallPlan: Boolean? = null,
    val modelName: String = "",
    val estimateMinutes: Int = 0,
    val counts: Map<String, Int> = emptyMap(),
    val sample: List<String> = emptyList(),
    val failed: List<BurstFailure>? = null,
    val failedMore: Int? = null,
) {
    val isPreview: Boolean get() = phase == "preview"
    val isActive: Boolean get() = phase == "running" || phase == "merging"
    fun count(status: String): Int = counts[status] ?: 0
    val canRetry: Boolean get() = count("failed") > 0 && (phase == "running" || phase == "done")
}

@Serializable
data class BurstFailure(val title: String = "", val reason: String = "")

/** One part of a burst, as the computer lists it. `threadId` is absent until the part starts. */
@Serializable
data class BurstChildRow(
    val seq: Int = 0,
    val title: String = "",
    val status: String = "",
    val threadId: String? = null,
    val error: String? = null,
)

@Serializable
data class BurstView(
    val burstId: String = "",
    val phase: String = "",
    val instructions: String = "",
    val items: List<String> = emptyList(),
    val children: List<BurstChildRow> = emptyList(),
)

enum class BurstAction(val path: String) {
    RUN("run"),
    CANCEL("cancel"),
    RETRY("retry"),
    EDIT("edit"),
}

data class BurstEdit(val instructions: String, val items: List<String>)

sealed interface BurstOutcome {
    data object Done : BurstOutcome

    /** [message] is the computer's own words, or null when it gave none. */
    data class Failed(val message: String?) : BurstOutcome
}

object Bursts {
    const val TASK_LIMIT = 120
    const val MIN_ITEMS = 2

    /** The lines of the Items field: one task each, blanks dropped. */
    fun parseItems(text: String): List<String> =
        text.lines().map { it.trim() }.filter { it.isNotEmpty() }

    fun canSave(items: List<String>, busy: Boolean): Boolean = !busy && items.size >= MIN_ITEMS

    /** The rows that count toward "running" and "waiting"; statuses this app does not know are left out. */
    fun countParts(card: BurstCard): List<Pair<String, Int>> = listOf("running", "queued", "failed")
        .map { it to card.count(it) }
        .filter { it.second > 0 }

    /** A part can open its conversation once it has one. */
    fun opensThread(row: BurstChildRow): Boolean = !row.threadId.isNullOrEmpty()
}

/**
 * The press of one burst button. While a request runs, every button is off and
 * a second press is ignored. A failure leaves the card as it was and keeps the
 * computer's words, or [fallback], to show under it.
 */
class BurstActionRunner(
    private val perform: suspend (BurstAction, BurstEdit?) -> BurstOutcome,
    private val fallback: String,
) {
    data class State(val busy: Boolean = false, val error: String? = null)

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    /** True when the computer took the action. */
    suspend fun press(action: BurstAction, edit: BurstEdit? = null): Boolean {
        val idle = _state.value
        if (idle.busy || !_state.compareAndSet(idle, State(busy = true))) return false
        val outcome = try {
            perform(action, edit)
        } catch (error: CancellationException) {
            _state.value = State()
            throw error
        } catch (_: Throwable) {
            BurstOutcome.Failed(null)
        }
        _state.value = when (outcome) {
            is BurstOutcome.Done -> State()
            is BurstOutcome.Failed -> State(error = outcome.message?.takeIf { it.isNotBlank() } ?: fallback)
        }
        return outcome is BurstOutcome.Done
    }
}
