package com.openmausbot.companion.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.openmausbot.companion.core.ContextUsage
import com.openmausbot.companion.core.EngineLimit
import com.openmausbot.companion.core.LimitWindowUsage
import com.openmausbot.companion.core.TaskUsage
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AiUsageScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val now = Instant.parse("2026-11-21T08:00:00Z")

    private fun ms(iso: String) = Instant.parse(iso).toEpochMilli().toDouble()

    @Test
    fun aCardShowsEachWindowWithItsPercentLeftAndResetTime() {
        val limit = EngineLimit(
            engine = "a", name = "Claude", driver = "claude", usedPercent = 40.0, blocked = false,
            windows = listOf(
                LimitWindowUsage("5-hour", 40.0, ms("2026-11-21T15:40:00Z")),
                LimitWindowUsage("monthly", 95.0, null),
            ),
        )
        compose.setContent {
            CompanionTheme { AiUsageCardView(limit, Locale.US, now, ZoneOffset.UTC) }
        }
        compose.onNodeWithText("Claude").assertIsDisplayed()
        compose.onNodeWithText("5-hour limit").assertIsDisplayed()
        compose.onNodeWithText("60% left · resets 3:40 PM").assertIsDisplayed()
        compose.onNodeWithText("Other limit").assertIsDisplayed()
        compose.onNodeWithText("5% left").assertIsDisplayed()
    }

    @Test
    fun aPausedCardShowsWhenItResumes() {
        val limit = EngineLimit(
            engine = "a", name = "Codex", driver = "codex", window = "5-hour", usedPercent = 100.0,
            resetsAt = ms("2026-11-21T12:30:00Z"), blocked = true,
        )
        compose.setContent {
            CompanionTheme { AiUsageCardView(limit, Locale.US, now, ZoneOffset.UTC) }
        }
        compose.onNodeWithText("Paused until 12:30 PM").assertIsDisplayed()
    }

    @Test
    fun theConversationLineShowsTokensTurnsAndCostOnlyWhenKnown() {
        compose.setContent {
            CompanionTheme {
                Column {
                    ConversationUsageSection(TaskUsage(input = 1200, output = 300, turns = 3, context = ContextUsage(50_000, 200_000)))
                    ConversationUsageSection(TaskUsage(input = 10, output = 5, costUsd = 0.4231, turns = 1))
                }
            }
        }
        compose.onNodeWithText("1,500 tokens in 3 turns").assertIsDisplayed()
        compose.onNodeWithText("Context 50,000 of 200,000").assertIsDisplayed()
        compose.onNodeWithText("15 tokens in 1 turn · about \$0.42").assertIsDisplayed()
    }
}
