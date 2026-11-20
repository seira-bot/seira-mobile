package com.openmausbot.companion.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher

import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.openmausbot.companion.core.Connection
import com.openmausbot.companion.core.Profile
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsVoiceHelpTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val mac = Connection(id = "c1", name = "Dina's MacBook", host = "192.168.1.20", port = 8810)

    private fun mount(connection: Connection?): WiringScene {
        val scene = WiringScene(connection = connection)
        compose.setContent {
            CompositionLocalProvider(LocalCompanion provides scene.environment) {
                CompanionTheme(darkTheme = false) {
                    SettingsScreen(
                        onBack = {},
                        onOpenConnectedApps = {},
                        accounts = AccountInfoSource {
                            AccountInfo(Profile(name = "Dina Pratama", email = "dina@example.com"), connectedApps = 3)
                        },
                    )
                }
            }
        }
        compose.waitForIdle()
        return scene
    }

    private fun assertNoIcon3d() =
        compose.onAllNodes(
            SemanticsMatcher("has an icon3d tag") { node ->
                node.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("icon3d.") == true
            },
            useUnmergedTree = true,
        ).assertCountEquals(0)

    @Test
    fun `a paired phone shows voice and calls with a voice row`() {
        mount(mac)
        compose.onNodeWithText("Voice and calls").performScrollTo()
        compose.onNodeWithText("Voice").performScrollTo()
        assertNoIcon3d()
    }

    @Test
    fun `the voice row opens the live call settings`() {
        mount(mac)
        compose.onNodeWithText("Voice").performScrollTo().performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Live call settings").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun `the live captions switch writes the shared preference`() {
        val scene = mount(mac)
        val preferences = scene.environment.chatPreferences
        assertTrue(preferences.liveCaptions.value)
        compose.onNodeWithText("Show the words during a call").performScrollTo().assertExists()
        compose.onNodeWithText("Live captions").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(false, preferences.liveCaptions.value)
        compose.onNodeWithText("Live captions").performClick()
        compose.waitForIdle()
        assertEquals(true, preferences.liveCaptions.value)
    }

    @Test
    fun `an unpaired phone has no voice and calls but still has help`() {
        mount(null)
        compose.onAllNodesWithText("Voice and calls").assertCountEquals(0)
        compose.onAllNodesWithText("Live captions").assertCountEquals(0)
        compose.onNodeWithText("Help").performScrollTo()
        assertNoIcon3d()
    }

    private fun assertOpens(label: String, url: String) {
        mount(null)
        compose.onNodeWithText(label).performScrollTo().performClick()
        val started: Intent? = Shadows.shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity
        assertNotNull(started)
        assertEquals(Intent.ACTION_VIEW, started.action)
        assertEquals(Uri.parse(url), started.data)
    }

    @Test
    fun `questions and answers opens the help page`() =
        assertOpens("Questions and answers", SettingsPolicy.HELP_FAQ_URL)

    @Test
    fun `report a problem opens the issue list`() =
        assertOpens("Report a problem", SettingsPolicy.HELP_REPORT_URL)
}
