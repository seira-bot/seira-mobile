package com.openmausbot.companion.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.openmausbot.companion.core.Bot
import com.openmausbot.companion.core.Chat
import com.openmausbot.companion.core.Fleet
import com.openmausbot.companion.core.Message
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A long press on a message opens one menu: Seira's (reactions, Copy, Select
 * text). Part of a reply is selected from "Select text", which opens the text in
 * its own dialog. The reply itself is not a selection surface, so Android's own
 * text toolbar does not open on top of the menu (seira-bot/seira#574).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalTestApi::class)
class MessageRowMenuTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>(
        effectContext = object : MotionDurationScale { override val scaleFactor = 0f },
    )

    private val bot: Bot = bot()
    private val chat = Chat.BotChat(bot)

    @Test
    fun `a long press on a bot reply opens the message menu`() {
        showRow(Message("b", Message.Role.BOT, Message.Kind.TEXT, 1.0, text = REPLY))
        compose.onNodeWithText(REPLY, useUnmergedTree = true).performTouchInput { longClick() }
        compose.onNodeWithText("Copy").assertExists()
        compose.onNodeWithText("Select text").assertExists()
    }

    @Test
    fun `a long press on your own message opens the message menu`() {
        showRow(Message("u", Message.Role.USER, Message.Kind.TEXT, 1.0, text = REPLY))
        compose.onNodeWithText(REPLY, useUnmergedTree = true).performTouchInput { longClick() }
        compose.onNodeWithText("Copy").assertExists()
        compose.onNodeWithText("Select text").assertExists()
    }

    @Test
    fun `Select text opens the raw text in its own dialog`() {
        showRow(Message("b", Message.Role.BOT, Message.Kind.TEXT, 1.0, text = REPLY))
        compose.onNodeWithText(REPLY, useUnmergedTree = true).performTouchInput { longClick() }
        compose.onNodeWithText("Select text").performClick()
        compose.onNodeWithText("Touch and hold the text to select part of it.").assertExists()
    }

    private fun showRow(message: Message) = show {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) { MessageRow(chat, message) }
    }

    private fun show(content: @Composable () -> Unit) {
        val wiring = WiringScene(fleet = Fleet(listOf(bot), emptyList()))
        compose.setContent {
            CompositionLocalProvider(LocalCompanion provides wiring.environment) {
                CompanionTheme(darkTheme = false) { content() }
            }
        }
        compose.waitForIdle()
    }

    private companion object {
        const val REPLY = "Run the check again, then send the March invoices to Dina."
    }
}
