package bot.seira.companion.ui

import bot.seira.companion.core.ChipTap
import bot.seira.companion.core.ComposerChip
import bot.seira.companion.core.Message
import bot.seira.companion.core.ToolActivity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ComposerSuggestionsTest {
    private var clock = 0.0

    private fun line(role: Message.Role, text: String) =
        Message(id = "m${clock.toInt()}", role = role, kind = Message.Kind.TEXT, at = clock++, text = text)

    private fun bot(text: String) = line(Message.Role.BOT, text)
    private fun person(text: String) = line(Message.Role.USER, text)

    private fun tool(name: String) = Message(
        id = "t${clock.toInt()}", role = Message.Role.BOT, kind = Message.Kind.ACTIVITY, at = clock++, tool = ToolActivity(name = name),
    )

    private fun kinds(messages: List<Message>, working: Boolean = false, savedCodeChips: Boolean = false) =
        ComposerSuggestions.suggest(messages, working, savedCodeChips).map { it.kind }

    @Test
    fun `working bot gives none`() {
        assertEquals(emptyList(), kinds(listOf(person("hi"), bot("Done.")), working = true))
    }

    @Test
    fun `last line of the person gives none`() {
        assertEquals(emptyList(), kinds(listOf(bot("Done."), person("thanks"))))
    }

    @Test
    fun `failed turn gives none`() {
        val failed = Message(
            id = "f", role = Message.Role.BOT, kind = Message.Kind.ACTIVITY, at = 5.0,
            tool = ToolActivity(name = "error: boom", ok = false),
        )
        val result = kinds(listOf(person("go"), failed))
        assertEquals(emptyList(), result)
    }

    @Test
    fun `empty conversation gives starters`() {
        assertEquals(
            listOf(SuggestionKind.CAPABILITIES, SuggestionKind.PLAN_DAY, SuggestionKind.NEEDS_ME),
            kinds(emptyList()),
        )
    }

    @Test
    fun `options give one suggestion each, at most three`() {
        val answer = "Which one should I take?\n1. Fast path\n2) **Safe path** that takes a lot longer than the other\n3. Skip\n4. Cancel"
        val result = ComposerSuggestions.suggest(listOf(person("help"), bot(answer)), false, false)
        assertEquals(3, result.size)
        assertEquals(listOf(1, 2, 3), result.map { it.number })
        assertEquals("Safe path that takes a lot longer than the other", result[1].text)
        assertEquals("2. Safe path that takes a l…", ComposerSuggestions.optionTitle(2, result[1].text.orEmpty()))
    }

    @Test
    fun `numbered steps without a question are not options`() {
        assertEquals(
            listOf(SuggestionKind.WHATS_NEXT, SuggestionKind.EXPLAIN),
            kinds(listOf(person("how"), bot("To set it up:\n1. Install the tool\n2. Run it once\n3. Restart"))),
        )
    }

    @Test
    fun `a question after the list makes it options`() {
        val result = ComposerSuggestions.suggest(listOf(person("help"), bot("1. Keep it\n2. Remove it\nWhich one?")), false, false)
        assertEquals(listOf(SuggestionKind.OPTION, SuggestionKind.OPTION), result.map { it.kind })
    }

    @Test
    fun `one numbered line is not a list of options`() {
        assertEquals(
            listOf(SuggestionKind.WHATS_NEXT, SuggestionKind.EXPLAIN),
            kinds(listOf(person("help"), bot("Step\n1. Only one"))),
        )
    }

    @Test
    fun `question gives yes, not now and more`() {
        val expected = listOf(SuggestionKind.YES, SuggestionKind.NOT_NOW, SuggestionKind.MORE)
        assertEquals(expected, kinds(listOf(person("fix it"), bot("I can fix it. Should I go ahead?"))))
        assertEquals(expected, kinds(listOf(person("fix it"), bot("続けますか？"))))
    }

    @Test
    fun `code change without saved code chips gives changes and tests`() {
        val messages = listOf(person("fix"), tool("Bash"), tool("edit"), bot("All fixed."))
        assertEquals(
            listOf(SuggestionKind.SHOW_CHANGES, SuggestionKind.RUN_TESTS),
            kinds(messages, savedCodeChips = false),
        )
    }

    @Test
    fun `code change with saved code chips falls through to default`() {
        val messages = listOf(person("fix"), tool("Bash"), bot("All fixed."))
        assertEquals(
            listOf(SuggestionKind.WHATS_NEXT, SuggestionKind.EXPLAIN),
            kinds(messages, savedCodeChips = true),
        )
    }

    @Test
    fun `code change before the last person line does not count`() {
        val messages = listOf(person("fix"), tool("Bash"), bot("Fixed."), person("thanks"), bot("Welcome."))
        assertEquals(listOf(SuggestionKind.WHATS_NEXT, SuggestionKind.EXPLAIN), kinds(messages))
    }

    @Test
    fun `long answer gives summarize`() {
        val long = "word ".repeat(200)
        assertEquals(
            listOf(SuggestionKind.SUMMARIZE, SuggestionKind.WHATS_NEXT),
            kinds(listOf(person("explain"), bot(long))),
        )
    }

    @Test
    fun `finished answer gives the default`() {
        assertEquals(
            listOf(SuggestionKind.WHATS_NEXT, SuggestionKind.EXPLAIN),
            kinds(listOf(person("hi"), bot("Hello there."))),
        )
    }

    private fun chip(title: String, prompt: String, tap: ChipTap) = ComposerChip(title, prompt, "x", tap)

    @Test
    fun `row puts server chips first and drops context when the server sent some`() {
        val server = listOf(chip("A", "Do A", ChipTap.FILL_COMPOSER))
        val context = listOf(ComposerSuggestions.chip("Next", "What should we do next?"))
        val saved = listOf(chip("Own", "My own", ChipTap.SEND))
        assertEquals(listOf("Do A", "My own"), ComposerSuggestions.row(server, context, saved).map { it.prompt })
    }

    @Test
    fun `row orders context before saved and drops a repeated prompt`() {
        val context = listOf(
            ComposerSuggestions.chip("Next", "What should we do next?"),
            ComposerSuggestions.chip("Explain", "Explain that in simpler words."),
        )
        val saved = listOf(
            chip("Next", "  what should we do NEXT? ", ChipTap.SEND),
            chip("Own", "My own", ChipTap.SEND),
        )
        val row = ComposerSuggestions.row(emptyList(), context, saved)
        assertEquals(listOf("What should we do next?", "Explain that in simpler words.", "My own"), row.map { it.prompt })
        assertEquals(ChipTap.FILL_COMPOSER, row.first().tap)
    }

    @Test
    fun `row holds at most six chips`() {
        val saved = (1..9).map { chip("S$it", "p$it", ChipTap.SEND) }
        assertEquals(6, ComposerSuggestions.row(emptyList(), emptyList(), saved).size)
    }

    @Test
    fun `every context chip fills the composer and never sends`() {
        val everything = SuggestionKind.entries.map { Suggestion(it, 1, "Option") }
        everything.forEach { suggestion ->
            val chip = ComposerSuggestions.chip(
                ComposerSuggestions.optionTitle(suggestion.number ?: 0, suggestion.text.orEmpty()),
                ComposerSuggestions.optionText(suggestion.text.orEmpty()),
            )
            assertEquals(ChipTap.FILL_COMPOSER, chip.tap)
            assertEquals(ComposerSuggestions.ICON, chip.icon)
        }
        assertTrue(everything.isNotEmpty())
    }
}
