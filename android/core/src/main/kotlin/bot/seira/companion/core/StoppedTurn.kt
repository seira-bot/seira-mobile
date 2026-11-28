package bot.seira.companion.core

private const val STOPPED_PREFIX = "stopped:"

private val HARNESS_PREFIX = Regex("""^provider returned a(?: streaming)? completion error:\s*""", RegexOption.IGNORE_CASE)

private val CLIENT_CANCEL = listOf(
    Regex("""^context\s+canceled\.?$""", RegexOption.IGNORE_CASE),
    Regex("""^(?:context\s+canceled)?\s*the\s+request\s+was\s+cancell?ed\s+by\s+the\s+client\.?$""", RegexOption.IGNORE_CASE),
    Regex("""^(?:(?:the|this)\s+)?operation\s+was\s+aborted\.?$""", RegexOption.IGNORE_CASE),
    Regex("""^(?:the\s+)?request\s+was\s+aborted\.?$""", RegexOption.IGNORE_CASE),
    Regex("""^the\s+user\s+aborted\s+a\s+request\.?$""", RegexOption.IGNORE_CASE),
)

/**
 * The text is only a client abort sentence, alone or inside the harness
 * wrapper. Port of `isClientCancellation` in `shared/client-cancel.ts` on the
 * computer: a real error that merely mentions a cancellation stays an error.
 */
fun isClientCancellation(text: String): Boolean {
    val body = text.trim().replace(HARNESS_PREFIX, "").trim()
    return body.isNotEmpty() && CLIENT_CANCEL.any { it.matches(body) }
}

/**
 * A row the chat reads as a stop: the stopped activity, a bot bubble that is
 * only the abort sentence, or a failed-turn row whose cause is that sentence.
 * Port of `isCancelledTranscriptRow` in `shared/client-cancel.ts`.
 */
fun isStoppedTurn(message: Message): Boolean {
    if (message.role == Message.Role.USER) return false
    return when (message.kind) {
        Message.Kind.TEXT -> message.attachments.isNullOrEmpty() && isClientCancellation(message.text.orEmpty())
        Message.Kind.ACTIVITY -> message.tool?.let {
            it.name.startsWith(STOPPED_PREFIX) || isClientCancellation(failedTurnCause(it.name).orEmpty())
        } == true
        else -> false
    }
}
