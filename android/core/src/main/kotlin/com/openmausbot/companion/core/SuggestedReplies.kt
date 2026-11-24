package com.openmausbot.companion.core

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** One server chip: [title] is its label, [prompt] is the text a tap puts in the composer. */
data class SuggestedReply(val title: String, val prompt: String)

/** What a tap on a chip does. */
enum class ChipTap {
    /** The tap sends the text at once. Only the chips the person saved have it: a chip from a model never does. */
    SEND,

    /** The tap puts the full text in the composer. The person reads it, edits it and presses Send. */
    FILL_COMPOSER,
}

/** A chip above the composer, from the server or from the saved list. */
data class ComposerChip(val title: String, val prompt: String, val icon: String?, val tap: ChipTap)

/**
 * The `suggestedReplies` field on the last bot line of a finished turn (seira#527, `suggested-replies.ts`).
 * The text comes from a model, so the phone runs the same cleaner as the computer before a chip exists.
 * The filter is best effort. The boundary is [tap]: a chip from the server never sends anything. A chip row can clip a
 * title (a narrow screen, large text, a row that scrolls), so no layout proves that the person saw the whole text. The
 * tap puts the full text in the composer, where the person reads it and presses Send.
 * Port of iOS `SuggestedReplies`.
 */
object SuggestedReplies {
    const val MAX = 4
    const val TITLE_MAX = 40
    const val PROMPT_MAX = 500
    private const val READ = 16

    /** Mark for a server chip on the composer row. */
    const val ICON = "next"

    private val CONTROL = Regex("""[\p{Cc}\p{Cf}\p{Zl}\p{Zp}]""")
    /** The white space that a script `\s` reads: the Java class is narrower, so the set is written out. */
    private val SPACES = Regex("""[\s\u00a0\u1680\u2000-\u200a\u2028\u2029\u202f\u205f\u3000\ufeff]+""")
    private val LINK = Regex(
        """(?:^|[^\p{L}\p{N}])(?:[a-z][a-z0-9+.-]*://|www\.|(?:javascript|data|vbscript|mailto|tel|sms|file|blob):)""",
        setOf(RegexOption.IGNORE_CASE),
    )
    private val TLDS = listOf(
        "com", "net", "org", "io", "dev", "app", "ai", "co", "xyz", "info", "biz", "gov", "edu", "uk", "us", "de", "fr", "jp",
        "cn", "ru", "tv", "id", "me", "link", "site", "online", "top", "store", "tech", "cloud", "page",
    )
    private val BARE_ADDRESS = Regex(
        """(?:^|[^\p{L}\p{N}])(?:(?:[\p{L}\p{N}](?:[\p{L}\p{N}-]*[\p{L}\p{N}])?\.)+(?:${TLDS.joinToString("|")})(?![\p{L}\p{N}-])""" +
            """|localhost(?![\p{L}\p{N}-])|[0-9]{1,3}(?:\.[0-9]{1,3}){3}(?![\p{L}\p{N}]))""",
        setOf(RegexOption.IGNORE_CASE),
    )
    private val SLASH_COMMAND = Regex("""^\s*/\S""")
    private val SHELL_SYNTAX = Regex("""`|\$\(|\$\{|&&|\|\||\||^\s*\$\s|>>|(?:^|\s)[0-9]?>\s*/|(?:^|\s)<\s*/""")
    private val SHELL_WORDS = setOf(
        "sudo", "rm", "curl", "wget", "chmod", "chown", "bash", "sh", "zsh", "npm", "npx", "pnpm", "yarn", "pip", "git", "ssh",
        "scp", "docker", "kubectl", "cd", "ls", "cat", "echo", "export", "eval", "exec", "kill", "dd", "mkfs",
    )
    private val ACTION_PAYLOAD = Regex("""^\s*[\{\[]|"(?:action|tool|command|cmd|url)"\s*:""", setOf(RegexOption.IGNORE_CASE))

    private fun length(value: String): Int = value.codePointCount(0, value.length)

    private fun tidy(value: JsonElement?): String? {
        val text = (value as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return null
        return text.replace(CONTROL, " ").replace(SPACES, " ").trim().ifEmpty { null }
    }

    private fun plain(text: String): Boolean {
        if (LINK.containsMatchIn(text) || BARE_ADDRESS.containsMatchIn(text) || SLASH_COMMAND.containsMatchIn(text) ||
            SHELL_SYNTAX.containsMatchIn(text) || ACTION_PAYLOAD.containsMatchIn(text)
        ) return false
        return text.substringBefore(' ') !in SHELL_WORDS
    }

    /**
     * The list a phone may show: at most [MAX] entries, each plain text, a title of at most [TITLE_MAX] and a prompt of at
     * most [PROMPT_MAX] characters. An entry with a part that is missing, not text, empty or over the limit, or that holds a
     * link, a slash command, shell syntax or an action payload, is dropped whole and never cut. A repeated prompt counts
     * once. A field that is not a list, or holds nothing usable, gives an empty list: the phone keeps its own chips.
     */
    fun clean(field: JsonElement?): List<SuggestedReply> {
        if (field !is JsonArray) return emptyList()
        val kept = mutableListOf<SuggestedReply>()
        val seen = mutableSetOf<String>()
        for (entry in field.take(READ)) {
            if (kept.size == MAX) break
            if (entry !is JsonObject) continue
            val title = tidy(entry["title"]) ?: continue
            val prompt = tidy(entry["prompt"]) ?: continue
            if (length(title) > TITLE_MAX || length(prompt) > PROMPT_MAX) continue
            if (!plain(title) || !plain(prompt)) continue
            if (!seen.add(prompt.lowercase())) continue
            kept += SuggestedReply(title, prompt)
        }
        return kept
    }

    /**
     * The chips of the open conversation: those of its last line, and only when that line is a bot line. A line of the
     * person after it, or a conversation with no field, gives none, so one conversation never shows the chips of another.
     */
    fun forTranscript(messages: List<Message>): List<SuggestedReply> {
        val last = messages.lastOrNull() ?: return emptyList()
        if (last.role != Message.Role.BOT) return emptyList()
        return clean(last.suggestedReplies)
    }

    /**
     * A tap on a chip from the server always fills the composer, for every title and prompt, whatever the screen shows.
     * The person reads the full text there and presses Send.
     */
    @Suppress("UNUSED_PARAMETER")
    fun tap(reply: SuggestedReply): ChipTap = ChipTap.FILL_COMPOSER

    /**
     * The composer row: the chips of the server first, then the saved chips. A saved chip with the prompt of a server chip
     * is left out, so the row never shows the same ask twice. Without server chips the row is the saved list as before.
     */
    fun composerChips(server: List<SuggestedReply>, saved: List<QuickReply>): List<ComposerChip> {
        val fromServer = server.map { ComposerChip(it.title, it.prompt, ICON, tap(it)) }
        val taken = server.map { it.prompt.lowercase() }.toSet()
        val own = saved
            .filterNot { it.prompt.trim().lowercase() in taken }
            .map { ComposerChip(it.title, it.prompt, it.icon, ChipTap.SEND) }
        return fromServer + own
    }
}
