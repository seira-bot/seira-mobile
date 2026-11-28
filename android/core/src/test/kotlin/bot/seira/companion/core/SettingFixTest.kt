package bot.seira.companion.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.decodeFromString

class SettingFixTest {
    private fun failed(cause: String, nameCode: String? = null, setup: Boolean? = null) =
        ToolActivity(name = "error: $cause", ok = false, nameCode = nameCode, setup = setup)

    @Test
    fun nameCodeOfAUsageNoticeFixesWithAModel() {
        for (code in listOf(
            "chat.notice.usageLimit", "chat.notice.usageLimitResets", "chat.notice.claudeExtraUsage",
            "chat.notice.claudeLimitWeekly", "chat.notice.toolCountExceeded", "chat.notice.modelNoRouteForBot",
        )) {
            assertEquals(SettingFix.MODEL, settingFix(failed("Something went wrong.", nameCode = code)), code)
        }
    }

    @Test
    fun otherNameCodesAndPartialMatchesDoNotFixWithAModel() {
        assertNull(settingFix(failed("Boom.", nameCode = "chat.notice.somethingElse")))
        assertNull(settingFix(failed("Boom.", nameCode = "x.chat.notice.usageLimit")))
        assertNull(settingFix(failed("Boom.", nameCode = "chat.notice.usageLimit.extra")))
    }

    @Test
    fun causeTextOfALimitOrOutageFixesWithAModel() {
        for (cause in listOf(
            "Your account is out of credits.",
            "Qwen hit a rate limit.",
            "The plan has reached its usage limit.",
            "Claude is overloaded right now.",
            "The provider had a temporary server problem.",
            "Switch this bot to another AI.",
            "SWITCH THE BOT TO ANOTHER MODEL",
        )) {
            assertEquals(SettingFix.MODEL, settingFix(failed(cause)), cause)
        }
    }

    @Test
    fun spendLimitWinsOverAModelNotice() {
        val tool = failed("This workspace has reached its monthly spend limit.", nameCode = "chat.notice.usageLimit")
        assertEquals(SettingFix.SPEND_LIMIT, settingFix(tool))
    }

    @Test
    fun signedOutNeedsTheSetupFlag() {
        assertEquals(SettingFix.SIGN_IN, settingFix(failed("Codex is signed out.", setup = true)))
        assertNull(settingFix(failed("Codex is signed out.")))
    }

    @Test
    fun aRowThatIsNotAFailedTurnHasNoFix() {
        assertNull(settingFix(ToolActivity(name = "Read file")))
        assertNull(settingFix(failed("Disk full.")))
    }

    @Test
    fun codexUsageLimitIsRecognisedByItsOwnSentence() {
        assertTrue(failed(CODEX_USAGE_LIMIT_REACHED).isCodexUsageLimit())
        assertFalse(failed("Claude hit a rate limit.").isCodexUsageLimit())
        assertFalse(ToolActivity(name = CODEX_USAGE_LIMIT_REACHED).isCodexUsageLimit())
    }

    @Test
    fun toolActivityDecodesTheFailedTurnFields() {
        val tool = CompanionJson.decodeFromString<ToolActivity>(
            """{"name":"error: Limit.","ok":false,"limitResetsAt":1790000000000,
                "nameCode":"chat.notice.usageLimitResets","nameParams":{"time":"5 pm","count":2},"partial":true}""",
        )
        assertEquals(1790000000000.0, tool.limitResetsAt)
        assertEquals("chat.notice.usageLimitResets", tool.nameCode)
        assertEquals(setOf("time", "count"), tool.nameParams?.keys)
        assertEquals(true, tool.partial)
        val plain = CompanionJson.decodeFromString<ToolActivity>("""{"name":"Read file"}""")
        assertNull(plain.limitResetsAt)
        assertNull(plain.nameCode)
        assertNull(plain.nameParams)
        assertNull(plain.partial)
    }
}
