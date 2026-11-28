package bot.seira.companion.ui

import android.app.NotificationManager
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import bot.seira.companion.R
import bot.seira.companion.core.CompanionEndpointKind
import bot.seira.companion.core.Connection
import bot.seira.companion.core.Profile
import bot.seira.companion.notifications.NotificationMapping
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsDesignTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `a channel the system has not made yet counts as on`() {
        assertTrue(SettingsDesign.channelIsOn(appNotificationsEnabled = true, importance = null))
        assertTrue(SettingsDesign.channelIsOn(true, NotificationManager.IMPORTANCE_HIGH))
        assertFalse(SettingsDesign.channelIsOn(true, NotificationManager.IMPORTANCE_NONE))
        assertFalse(SettingsDesign.channelIsOn(false, NotificationManager.IMPORTANCE_HIGH))
    }

    @Test
    fun `route kinds map to one label and an unknown route has none`() {
        assertEquals(R.string.pair_kind_same_wifi, SettingsDesign.kindLabel(CompanionEndpointKind.LAN))
        assertEquals(R.string.pair_kind_same_wifi, SettingsDesign.kindLabel(CompanionEndpointKind.BONJOUR))
        assertEquals(R.string.pair_kind_tailscale, SettingsDesign.kindLabel(CompanionEndpointKind.TAILNET))
        assertEquals(R.string.pair_kind_internet, SettingsDesign.kindLabel(CompanionEndpointKind.HOSTED))
        assertNull(SettingsDesign.kindLabel(null))
    }

    @Test
    fun `the three notification rows keep the channels the app creates`() {
        assertEquals(
            listOf(
                NotificationMapping.CHANNEL_BLOCKING,
                NotificationMapping.CHANNEL_DONE,
                NotificationMapping.CHANNEL_ROUTINE_FAILED,
            ),
            NotificationChannelRow.entries.map { it.channelId },
        )
    }

    @Test
    fun `the computer group names the paired computer`() {
        val mac = Connection(id = "c1", name = "Dina's MacBook", host = "127.0.0.1", port = 8810)
        val scene = WiringScene(connection = mac)
        compose.setContent {
            CompositionLocalProvider(LocalCompanion provides scene.environment) {
                CompanionTheme(darkTheme = false) {
                    SettingsScreen(onBack = {})
                }
            }
        }
        compose.waitUntil(5_000) { scene.session.connection.value != null }
        compose.waitForIdle()
        compose.onNodeWithText("Dina's MacBook").assertExists()
    }

    @Test
    fun `every language carries the same settings and pairing keys`() {
        val res = File("src/main/res")
        for (file in listOf("strings_settings.xml", "strings_pair.xml")) {
            val base = keys(File(res, "values/$file"))
            assertTrue(base.isNotEmpty(), "$file is empty")
            for (locale in listOf("values-b+zh+Hans", "values-b+zh+Hant")) {
                assertEquals(base, keys(File(res, "$locale/$file")), "$locale/$file differs from the English keys")
            }
        }
    }

    private fun keys(file: File): Map<String, List<String>> =
        Regex("""<string name="([^"]+)"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(file.readText())
            .associate { match ->
                val args = Regex("""%\d+\$[sd]""").findAll(match.groupValues[2]).map { it.value }.sorted().toList()
                match.groupValues[1] to args
            }
}
