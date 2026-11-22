package com.openmausbot.companion.core

enum class SettingFix { MODEL, SIGN_IN, SPEND_LIMIT }

const val CODEX_USAGE_URL: String = "https://chatgpt.com/codex/settings/usage"
const val CODEX_USAGE_LIMIT_REACHED: String =
    "Your ChatGPT account (used by Codex) reached its Codex usage limit."

private const val SPEND_LIMIT_REFUSAL = "this workspace has reached its monthly spend limit"

private val MODEL_NOTICES = Regex(
    """chat\.notice\.(?:usageLimit|usageLimitResets|claudeExtraUsage|claudeLimit\w*|toolCount\w*|modelNoRoute\w*)""",
)
private val MODEL_WORDS = Regex(
    """\baccount is out of credits\b|\bhit a rate limit\b|\bhas reached its usage limit\b|\bis overloaded right now\b|\bhad a temporary server problem\b|\bswitch (?:this|the) bot to another (?:AI|model)\b""",
    RegexOption.IGNORE_CASE,
)
private val SIGNED_OUT = Regex(
    """\b(?:is signed out|isn't signed in|sign in again|sign-in has expired)\b""",
    RegexOption.IGNORE_CASE,
)

/**
 * The setting that fixes a failed turn, read from its stored row. Port of
 * `settingFix` in `shared/setting-fixes.ts` on the computer.
 */
fun settingFix(tool: ToolActivity): SettingFix? {
    val cause = failedTurnCause(tool.name) ?: return null
    if (cause.lowercase().contains(SPEND_LIMIT_REFUSAL)) return SettingFix.SPEND_LIMIT
    if (tool.nameCode?.let { MODEL_NOTICES.matches(it) } == true) return SettingFix.MODEL
    if (MODEL_WORDS.containsMatchIn(cause)) return SettingFix.MODEL
    if (tool.setup == true && SIGNED_OUT.containsMatchIn(cause)) return SettingFix.SIGN_IN
    return null
}

/** The turn was refused by the ChatGPT account's own Codex usage limit. */
fun ToolActivity.isCodexUsageLimit(): Boolean =
    failedTurnCause(name)?.contains(CODEX_USAGE_LIMIT_REACHED) == true

/** What a failed-turn row offers under its error. */
data class FailedTurnOffer(
    val switchModel: Boolean,
    val tryAgain: Boolean,
    val checkUsage: Boolean,
    /** Epoch ms of the reset, when "Continue at" shows. */
    val continueAt: Double?,
    val askOwner: Boolean,
) {
    val any: Boolean get() = switchModel || tryAgain || checkUsage || continueAt != null || askOwner
}

object FailedTurnActions {
    /**
     * The one failed turn that shows the actions: the latest, while nothing
     * has been said since and no usage-limit offer is open. Matches the iPhone.
     */
    fun switchModelOfferMessageId(messages: List<Message>): String? {
        val index = messages.indexOfLast(::isFailedTurn)
        if (index < 0) return null
        if (messages.drop(index + 1).any { it.kind == Message.Kind.TEXT }) return null
        if (messages.any { it.card?.quotaSwitch != null && it.card.isPending }) return null
        if (messages[index].tool?.claudeUpdate == true) return null
        return messages[index].id
    }

    /**
     * The failed or stopped turn that shows Try again: the newest one, while
     * nothing has been said since and no usage-limit offer is open.
     */
    fun retryOfferMessageId(messages: List<Message>): String? {
        val index = messages.indexOfLast { isFailedTurn(it) || isStoppedTurn(it) }
        if (index < 0) return null
        if (messages.drop(index + 1).any { it.kind == Message.Kind.TEXT }) return null
        if (messages.any { it.card?.quotaSwitch != null && it.card.isPending }) return null
        val tool = messages[index].tool
        if (tool?.claudeUpdate == true) return null
        if (tool != null && CloudComputerNotice.matches(tool)) return null
        return messages[index].id
    }

    /**
     * The newest turn failed and the bot has not answered since. A turn whose
     * reply came back ([ToolActivity.partial]) did not fail as a whole.
     */
    fun lastTurnFailed(messages: List<Message>): Boolean {
        val index = messages.indexOfLast { isFailedTurn(it) && it.tool?.partial != true }
        if (index < 0) return false
        return messages.drop(index + 1).none { it.role == Message.Role.BOT && it.kind == Message.Kind.TEXT }
    }

    /** "3:40 PM" for a reset today, "Fri 3:40 PM" further out. */
    fun continueTime(
        resetsAtMs: Double,
        nowMs: Long = System.currentTimeMillis(),
        zone: java.time.ZoneId = java.time.ZoneId.systemDefault(),
        locale: java.util.Locale = java.util.Locale.getDefault(),
    ): String {
        val at = java.time.Instant.ofEpochMilli(resetsAtMs.toLong()).atZone(zone)
        val today = java.time.Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        val time = java.time.format.DateTimeFormatter
            .ofLocalizedTime(java.time.format.FormatStyle.SHORT).withLocale(locale).format(at)
        if (at.toLocalDate() == today) return time
        return java.time.format.DateTimeFormatter.ofPattern("EEE", locale).format(at) + " " + time
    }

    fun lastUserMessage(messages: List<Message>): Message? =
        messages.lastOrNull {
            it.role == Message.Role.USER && it.kind == Message.Kind.TEXT && !it.text.isNullOrBlank()
        }

    fun offer(
        tool: ToolActivity,
        turnRunning: Boolean,
        modelListForbidden: Boolean,
        continueForbidden: Boolean,
        canRetry: Boolean = true,
    ): FailedTurnOffer? {
        if (turnRunning || failedTurnCause(tool.name) == null) return null
        val fixable = settingFix(tool) == SettingFix.MODEL && tool.partial != true
        val offer = FailedTurnOffer(
            switchModel = fixable && !modelListForbidden,
            tryAgain = canRetry && !CloudComputerNotice.matches(tool),
            checkUsage = tool.isCodexUsageLimit(),
            continueAt = tool.limitResetsAt?.takeIf { !continueForbidden },
            askOwner = fixable && modelListForbidden,
        )
        return offer.takeIf { it.any }
    }

    /** What a stopped turn offers: Try again, when the person has a message to send again. */
    fun stoppedOffer(turnRunning: Boolean, canRetry: Boolean): FailedTurnOffer? =
        FailedTurnOffer(
            switchModel = false,
            tryAgain = canRetry,
            checkUsage = false,
            continueAt = null,
            askOwner = false,
        ).takeIf { !turnRunning && it.any }
}

sealed interface ModelListResult {
    data class Loaded(val instances: List<Instance>) : ModelListResult
    data object Forbidden : ModelListResult
    data object Failed : ModelListResult
}
