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
     * Only the newest failed turn of a conversation is offered, and only while
     * the person has not written again. An older failure is history.
     */
    fun isLatestFailure(messages: List<Message>, message: Message): Boolean {
        val index = messages.indexOfFirst { it.id == message.id }
        if (index < 0) return false
        val later = messages.drop(index + 1)
        return later.none { it.role == Message.Role.USER || isFailedTurn(it) }
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
    ): FailedTurnOffer? {
        if (turnRunning || failedTurnCause(tool.name) == null) return null
        val fixable = settingFix(tool) == SettingFix.MODEL && tool.partial != true
        val offer = FailedTurnOffer(
            switchModel = fixable && !modelListForbidden,
            tryAgain = fixable,
            checkUsage = tool.isCodexUsageLimit(),
            continueAt = tool.limitResetsAt?.takeIf { !continueForbidden },
            askOwner = fixable && modelListForbidden,
        )
        return offer.takeIf { it.any }
    }
}

sealed interface ModelListResult {
    data class Loaded(val instances: List<Instance>) : ModelListResult
    data object Forbidden : ModelListResult
    data object Failed : ModelListResult
}
