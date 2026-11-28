package bot.seira.companion.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.createComposeRule
import bot.seira.companion.core.Connection
import bot.seira.companion.core.Profile
import bot.seira.companion.ui.theme.ThemeMode
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w390dp-h844dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsPairScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun lightSettingsIsWritten() {
        assertTrue(compose.captureDesign("settings", ThemeMode.LIGHT) { Settings() }.length() > 0)
    }

    @Test
    fun darkSettingsIsWritten() {
        assertTrue(compose.captureDesign("settings", ThemeMode.DARK) { Settings() }.length() > 0)
    }

    @Test
    fun lightPairIsWritten() {
        assertTrue(compose.captureDesign("pair", ThemeMode.LIGHT) { Pair() }.length() > 0)
    }

    @Test
    fun darkPairIsWritten() {
        assertTrue(compose.captureDesign("pair", ThemeMode.DARK) { Pair() }.length() > 0)
    }

    @Test
    fun lightPairConnectedIsWritten() {
        assertTrue(compose.captureDesign("pair-connected", ThemeMode.LIGHT) { Connected() }.length() > 0)
    }

    @Test
    fun darkPairConnectedIsWritten() {
        assertTrue(compose.captureDesign("pair-connected", ThemeMode.DARK) { Connected() }.length() > 0)
    }

    @Composable
    private fun Settings() {
        val scene = remember {
            WiringScene(
                connection = Connection(id = "c1", name = "Dina's MacBook", host = "192.168.1.20", port = 8810),
            )
        }
        CompositionLocalProvider(LocalCompanion provides scene.environment) {
            SettingsScreen(
                onBack = {},
                onOpenRoutines = {},
                onOpenConnectedApps = {},
                accounts = AccountInfoSource {
                    AccountInfo(Profile(name = "Dina Pratama", email = "dina@example.com"), connectedApps = 3)
                },
            )
        }
    }

    @Composable
    private fun Pair() {
        PairFrame(
            phase = PairPhase.FOUND,
            statusText = "Code found",
            statusIsError = false,
            torch = PairDesign.torch(hasFlashUnit = true, on = false),
            onTorch = {},
            onBack = {},
            backEnabled = true,
            viewfinder = null,
            card = {
                PairFoundCard(
                    name = "Dina's MacBook",
                    detail = "Same Wi-Fi · found just now",
                    connecting = false,
                    onConnect = {},
                    onEnterCode = {},
                )
            },
        )
    }

    @Composable
    private fun Connected() {
        PairFrame(
            phase = PairPhase.CONNECTED,
            statusText = "Paired with Dina's MacBook",
            statusIsError = false,
            torch = null,
            onTorch = {},
            onBack = {},
            backEnabled = true,
            viewfinder = null,
            card = { PairConnectedCard(onDone = {}, onPairAnother = {}) },
        )
    }
}
