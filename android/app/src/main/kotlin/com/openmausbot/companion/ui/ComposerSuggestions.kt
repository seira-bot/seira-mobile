package com.openmausbot.companion.ui

import android.content.res.Resources
import com.openmausbot.companion.R
import com.openmausbot.companion.core.ChipTap
import com.openmausbot.companion.core.ComposerChip
import com.openmausbot.companion.core.FailedTurnActions
import com.openmausbot.companion.core.Message
import com.openmausbot.companion.core.QuickReplyContext

/** What a context suggestion asks. The words come from string resources, so this file holds no copy. */
enum class SuggestionKind {
    CAPABILITIES, PLAN_DAY, NEEDS_ME, OPTION, YES, NOT_NOW, MORE, SHOW_CHANGES, RUN_TESTS, SUMMARIZE, WHATS_NEXT, EXPLAIN,
}

/** One suggestion. [number] and [text] carry the option of an [SuggestionKind.OPTION]. */
data class Suggestion(val kind: SuggestionKind, val number: Int? = null, val text: String? = null)

/**
 * Suggestions for the chip row above the composer, read from the conversation the phone already holds.
 * A suggestion never sends: its tap fills the composer and the person presses Send.
 */
object ComposerSuggestions {
    const val ICON = "suggest"
    const val MAX_SUGGESTIONS = 3
    const val ROW_MAX = 6
    private const val TITLE_OPTION_MAX = 24
    private const val PROMPT_OPTION_MAX = 200
    private const val LONG_ANSWER = 700

    private val OPTION_LINE = Regex("""^\s*(\d+)[.)]\s+(.+)""")
    private val MARKUP = Regex("""[*_`#]+""")

    fun suggest(messages: List<Message>, working: Boolean, savedCodeChips: Boolean): List<Suggestion> {
        if (working) return emptyList()
        val texts = messages.filter { it.kind == Message.Kind.TEXT && !it.text.isNullOrBlank() }
        if (texts.lastOrNull()?.role == Message.Role.USER) return emptyList()
        if (FailedTurnActions.lastTurnFailed(messages)) return emptyList()
        if (texts.isEmpty()) {
            return listOf(
                Suggestion(SuggestionKind.CAPABILITIES),
                Suggestion(SuggestionKind.PLAN_DAY),
                Suggestion(SuggestionKind.NEEDS_ME),
            )
        }
        val answer = texts.last().text.orEmpty().trim()

        val lines = answer.lines()
        val asks = lines.any { OPTION_LINE.find(it) == null && (it.contains('?') || it.contains('？')) }
        val options = lines.mapNotNull { OPTION_LINE.find(it) }
            .mapNotNull { m ->
                val n = m.groupValues[1].toIntOrNull() ?: return@mapNotNull null
                val label = m.groupValues[2].replace(MARKUP, "").trim()
                if (label.isEmpty()) null else Suggestion(SuggestionKind.OPTION, n, label)
            }
        if (asks && options.size >= 2) return options.take(MAX_SUGGESTIONS)

        val lastLine = lines.lastOrNull { it.isNotBlank() }.orEmpty().trim().trimEnd('*', '_', '`', '"', ')', ' ')
        if (lastLine.endsWith("?") || lastLine.endsWith("？")) {
            return listOf(
                Suggestion(SuggestionKind.YES),
                Suggestion(SuggestionKind.NOT_NOW),
                Suggestion(SuggestionKind.MORE),
            )
        }

        val lastPerson = messages.indexOfLast { it.role == Message.Role.USER && it.kind == Message.Kind.TEXT }
        val ranCode = messages.drop(lastPerson + 1).any {
            it.kind == Message.Kind.ACTIVITY && it.tool?.let(QuickReplyContext::isCodeTool) == true
        }
        if (ranCode && !savedCodeChips) {
            return listOf(Suggestion(SuggestionKind.SHOW_CHANGES), Suggestion(SuggestionKind.RUN_TESTS))
        }
        if (answer.length > LONG_ANSWER) {
            return listOf(Suggestion(SuggestionKind.SUMMARIZE), Suggestion(SuggestionKind.WHATS_NEXT))
        }
        return listOf(Suggestion(SuggestionKind.WHATS_NEXT), Suggestion(SuggestionKind.EXPLAIN))
    }

    /** The label of an option chip: the number first, then the option cut to fit. */
    fun optionTitle(number: Int, text: String): String {
        val cut = if (text.length > TITLE_OPTION_MAX) text.take(TITLE_OPTION_MAX).trimEnd() + "…" else text
        return "$number. $cut"
    }

    fun optionText(text: String): String = text.take(PROMPT_OPTION_MAX)

    /** A context chip always fills the composer. */
    fun chip(title: String, prompt: String): ComposerChip = ComposerChip(title, prompt, ICON, ChipTap.FILL_COMPOSER)

    /**
     * The row: server chips, then context chips only when the server sent none, then saved chips. A chip whose prompt
     * repeats an earlier one is dropped. The row holds at most [ROW_MAX] chips.
     */
    fun row(server: List<ComposerChip>, context: List<ComposerChip>, saved: List<ComposerChip>): List<ComposerChip> {
        val ordered = server + (if (server.isEmpty()) context else emptyList()) + saved
        val seen = mutableSetOf<String>()
        return ordered.filter { seen.add(it.prompt.trim().lowercase()) }.take(ROW_MAX)
    }
}

/** The chip for [Suggestion], with the words of the current language. */
fun Suggestion.toChip(resources: Resources): ComposerChip {
    fun pair(title: Int, prompt: Int) = ComposerSuggestions.chip(resources.getString(title), resources.getString(prompt))
    return when (kind) {
        SuggestionKind.CAPABILITIES -> pair(R.string.chat_suggest_capabilities, R.string.chat_suggest_capabilities_prompt)
        SuggestionKind.PLAN_DAY -> pair(R.string.chat_suggest_plan_day, R.string.chat_suggest_plan_day_prompt)
        SuggestionKind.NEEDS_ME -> pair(R.string.chat_suggest_needs_me, R.string.chat_suggest_needs_me_prompt)
        SuggestionKind.YES -> pair(R.string.chat_suggest_yes, R.string.chat_suggest_yes_prompt)
        SuggestionKind.NOT_NOW -> pair(R.string.chat_suggest_not_now, R.string.chat_suggest_not_now_prompt)
        SuggestionKind.MORE -> pair(R.string.chat_suggest_more, R.string.chat_suggest_more_prompt)
        SuggestionKind.SHOW_CHANGES -> pair(R.string.chat_suggest_show_changes, R.string.chat_suggest_show_changes_prompt)
        SuggestionKind.RUN_TESTS -> pair(R.string.chat_suggest_run_tests, R.string.chat_suggest_run_tests_prompt)
        SuggestionKind.SUMMARIZE -> pair(R.string.chat_suggest_summarize, R.string.chat_suggest_summarize_prompt)
        SuggestionKind.WHATS_NEXT -> pair(R.string.chat_suggest_next, R.string.chat_suggest_next_prompt)
        SuggestionKind.EXPLAIN -> pair(R.string.chat_suggest_explain, R.string.chat_suggest_explain_prompt)
        SuggestionKind.OPTION -> {
            val n = number ?: 0
            val label = text.orEmpty()
            ComposerSuggestions.chip(
                ComposerSuggestions.optionTitle(n, label),
                resources.getString(R.string.chat_suggest_option_prompt, n, ComposerSuggestions.optionText(label)),
            )
        }
    }
}
