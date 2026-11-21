package com.openmausbot.companion.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import com.openmausbot.companion.R
import com.openmausbot.companion.core.Chat
import com.openmausbot.companion.core.Connection
import com.openmausbot.companion.core.Fleet
import com.openmausbot.companion.core.Profile
import com.openmausbot.companion.ui.theme.ThemeMode
import java.io.File
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
class Icons3dTest {
    @get:Rule val compose = createComposeRule()

    private val chat = Chat.BotChat(bot())
    private val allActions = ChatActions.sheet(
        chat,
        hasPendingApproval = false,
        canAddAttachment = true,
        hasCamera = true,
        offersCall = true,
        offersSchedule = true,
    )
    private val mac = Connection(id = "c1", name = "Dina's MacBook", host = "192.168.1.20", port = 8810)

    @Composable
    private fun Sheet() {
        val scene = remember { WiringScene(fleet = Fleet(listOf(bot()), emptyList())) }
        CompositionLocalProvider(LocalCompanion provides scene.environment) {
            Box(Modifier.fillMaxSize()) {
                ChatPlusSheet(open = true, chat = chat, actions = allActions, onDismiss = {}, onAction = {})
            }
        }
    }

    @Composable
    private fun Settings(connection: Connection?) {
        val scene = remember { WiringScene(connection = connection) }
        CompositionLocalProvider(LocalCompanion provides scene.environment) {
            SettingsScreen(
                onBack = {},
                onOpenRoutines = {},
                onOpenConnectedApps = {},
                onConnect = {},
                accounts = AccountInfoSource {
                    AccountInfo(Profile(name = "Dina Pratama", email = "dina@example.com"), connectedApps = 3)
                },
            )
        }
    }

    @Composable
    private fun Empty(empty: RosterEmpty = RosterEmpty.CHATS) {
        RosterEmptyState(
            empty = empty,
            refreshing = false,
            onNewChat = {},
            onPairComputer = {},
            onRefresh = {},
        )
    }

    private fun ComposeContentTestRule.assertTags(vararg names: String) {
        waitForIdle()
        names.forEach {
            assertTrue(onAllNodesWithTag("icon3d.$it", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty(), "no icon3d.$it")
        }
    }

    private fun ComposeContentTestRule.assertNoTags(vararg names: String) {
        waitForIdle()
        names.forEach {
            assertTrue(onAllNodesWithTag("icon3d.$it", useUnmergedTree = true).fetchSemanticsNodes().isEmpty(), "unexpected icon3d.$it")
        }
    }

    private fun publish(name: String, mode: ThemeMode, content: @Composable () -> Unit) {
        val file = compose.captureDesign(name, mode) { content() }
        assertTrue(file.length() > 0)
        val out = File("build/outputs/character-preview").apply { mkdirs() }
        file.copyTo(File(out, "icons-${file.name}"), overwrite = true)
    }

    @Test
    fun sheetShowsTheSixActionIcons() {
        compose.setContent { CompanionTheme(darkTheme = false) { Sheet() } }
        compose.assertTags("camera", "photos", "files", "live_call", "schedule", "cloud_desktop")
    }

    @Test
    fun settingsShowsNoIcon3d() {
        compose.setContent { CompanionTheme(darkTheme = false) { Settings(mac) } }
        compose.assertNoTags("appearance", "computer", "notifications", "voice", "privacy", "help")
    }

    @Test
    fun settingsWithoutAComputerShowsNoIcon3d() {
        compose.setContent { CompanionTheme(darkTheme = false) { Settings(null) } }
        compose.assertNoTags("computer", "pair", "help")
    }

    @Test
    fun emptyStateShowsItsIcon() {
        compose.setContent { CompanionTheme(darkTheme = false) { Empty() } }
        compose.assertTags("empty_chats")
    }

    @Test
    fun unpairedHomeShowsTheNotConnectedIcon() {
        compose.setContent { CompanionTheme(darkTheme = false) { UnpairedHomeScreen(onConnect = {}, onOpenSettings = {}) } }
        compose.assertTags("not_connected")
    }

    @Test
    fun updatesSheetShowsTheAllDoneIcon() {
        val scene = WiringScene()
        compose.setContent {
            CompositionLocalProvider(LocalCompanion provides scene.environment) {
                CompanionTheme(darkTheme = false) { UpdatesSheet(onOpen = {}, onDismiss = {}) }
            }
        }
        compose.assertTags("all_done")
    }

    @Test
    fun pairFrameShowsThePairIcon() {
        compose.setContent {
            CompanionTheme(darkTheme = false) {
                PairFrame(
                    phase = PairPhase.FOUND,
                    statusText = "Code found",
                    statusIsError = false,
                    torch = null,
                    onTorch = {},
                    onBack = {},
                    backEnabled = true,
                    viewfinder = null,
                    card = {},
                )
            }
        }
        compose.assertTags("pair")
    }

    @Test
    fun lightEmptyPreviewIsWritten() = publish("empty", ThemeMode.LIGHT) { Empty() }

    @Test
    fun darkEmptyPreviewIsWritten() = publish("empty", ThemeMode.DARK) { Empty() }

    @Test
    fun lightNotConnectedPreviewIsWritten() = publish("empty-not-connected", ThemeMode.LIGHT) { Empty(RosterEmpty.NOT_CONNECTED) }

    @Test
    fun darkNotConnectedPreviewIsWritten() = publish("empty-not-connected", ThemeMode.DARK) { Empty(RosterEmpty.NOT_CONNECTED) }

    @Test
    fun lightOfflinePreviewIsWritten() = publish("empty-offline", ThemeMode.LIGHT) { Empty(RosterEmpty.OFFLINE) }

    @Test
    fun darkOfflinePreviewIsWritten() = publish("empty-offline", ThemeMode.DARK) { Empty(RosterEmpty.OFFLINE) }

    @Test
    fun lightAllDonePreviewIsWritten() = publish("empty-all-done", ThemeMode.LIGHT) { UpdatesEmptyState(onBack = {}) }

    @Test
    fun darkAllDonePreviewIsWritten() = publish("empty-all-done", ThemeMode.DARK) { UpdatesEmptyState(onBack = {}) }

    @Test
    fun lightSettingsPreviewIsWritten() = publish("settings", ThemeMode.LIGHT) { Settings(mac) }

    @Test
    fun darkSettingsPreviewIsWritten() = publish("settings", ThemeMode.DARK) { Settings(mac) }

    @Test
    fun lightSheetPreviewIsWritten() = publish("sheet", ThemeMode.LIGHT) { Sheet() }

    @Test
    fun darkSheetPreviewIsWritten() = publish("sheet", ThemeMode.DARK) { Sheet() }
}
