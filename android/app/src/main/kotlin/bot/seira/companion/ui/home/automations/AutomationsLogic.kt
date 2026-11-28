package bot.seira.companion.ui.home.automations

import bot.seira.companion.core.Bot
import bot.seira.companion.core.Routine
import bot.seira.companion.core.RoutineRun
import bot.seira.companion.ui.RoutineRules

internal enum class AutomationsSegment { LIST, CALENDAR }

internal enum class ReceiptTone { SUCCESS, DANGER, WARNING, QUIET }

internal enum class RoutineMenuAction { RUN_NOW, EDIT, TOGGLE, OPEN_RESULTS, DELETE }

internal object AutomationsRules {
    const val RECEIPTS_SHOWN = 20

    fun receipts(runs: List<RoutineRun>): List<RoutineRun> =
        RoutineRules.receipts(runs).take(RECEIPTS_SHOWN)

    /** The newest run that left a results thread behind. */
    fun resultsRun(routineId: String, runs: List<RoutineRun>): RoutineRun? =
        runs.filter { it.routineId == routineId && !it.threadId.isNullOrBlank() }
            .maxByOrNull { it.scheduledFor }

    /** Seira when it is there, otherwise the first bot. */
    fun defaultAssistant(bots: List<Bot>): Bot? =
        bots.firstOrNull { it.name.equals("Seira", ignoreCase = true) } ?: bots.firstOrNull()

    /** "Set it up" waits for a sentence. */
    fun canSetUp(prompt: String): Boolean = prompt.isNotBlank()

    fun tone(status: String): ReceiptTone = when (RoutineRules.runStatus(status)) {
        RoutineRules.RunStatus.COMPLETED -> ReceiptTone.SUCCESS
        RoutineRules.RunStatus.FAILED -> ReceiptTone.DANGER
        RoutineRules.RunStatus.WAITING -> ReceiptTone.WARNING
        else -> ReceiptTone.QUIET
    }

    /** What the switch shows: the person's tap counts until the computer answers. */
    fun shownEnabled(routine: Routine, pending: Map<String, Boolean>): Boolean =
        pending[routine.id] ?: routine.enabled

    fun menu(routine: Routine, runs: List<RoutineRun>, atMillis: Double): List<RoutineMenuAction> = buildList {
        add(RoutineMenuAction.RUN_NOW)
        add(RoutineMenuAction.EDIT)
        if (routine.canToggle(atMillis)) add(RoutineMenuAction.TOGGLE)
        if (resultsRun(routine.id, runs) != null) add(RoutineMenuAction.OPEN_RESULTS)
        add(RoutineMenuAction.DELETE)
    }
}
