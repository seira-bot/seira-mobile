package bot.seira.companion.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import bot.seira.companion.core.APIError
import bot.seira.companion.core.Bot
import bot.seira.companion.core.Chat
import bot.seira.companion.core.CompanionJson
import bot.seira.companion.core.ConnectorCardApi
import bot.seira.companion.core.ConnectorCardStatus
import bot.seira.companion.core.Fleet
import bot.seira.companion.core.Message
import bot.seira.companion.core.Sender
import bot.seira.companion.core.SignInChoice
import bot.seira.companion.core.SignInOpenTarget
import java.io.File
import kotlinx.serialization.decodeFromString
import java.net.URI
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private class FakeCardApi : ConnectorCardApi {
    val calls = mutableListOf<String>()
    var authorizeResult: Result<URI> = Result.success(URI("https://auth.example.com/start"))
    var statusResult: Result<ConnectorCardStatus> = Result.success(ConnectorCardStatus(connected = false))
    var resumeResult: Result<Unit> = Result.success(Unit)
    var answerResult: Result<Unit> = Result.success(Unit)

    override suspend fun authorize(botId: String, messageId: String, threadId: String): Result<URI> {
        calls += "authorize:$botId:$messageId:$threadId"
        return authorizeResult
    }

    override suspend fun status(botId: String, messageId: String, threadId: String): Result<ConnectorCardStatus> {
        calls += "status:$botId:$messageId:$threadId"
        return statusResult
    }

    override suspend fun resume(botId: String, messageId: String, threadId: String): Result<Unit> {
        calls += "resume:$botId:$messageId:$threadId"
        return resumeResult
    }

    override suspend fun dismiss(botId: String, messageId: String, threadId: String): Result<Unit> {
        calls += "dismiss:$botId:$messageId:$threadId"
        return Result.success(Unit)
    }

    override suspend fun answerSignIn(botId: String, messageId: String, threadId: String, choice: SignInChoice): Result<Unit> {
        calls += "answer:$botId:$messageId:$threadId:${choice.route}"
        return answerResult
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ConnectorSignInCardTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val bot: Bot = bot()
    private val chat = Chat.BotChat(bot)
    private val api = FakeCardApi()
    private val opened = mutableListOf<URI>()
    private val openedScreens = mutableListOf<Pair<String, SignInOpenTarget>>()
    private var pageOpens = true

    private fun connector(fields: String = "", slug: String = "gmail", label: String = "Gmail") =
        msg(
            """{"id":"c1","role":"bot","kind":"connector","at":1,"text":"fallback text","connector":{"slug":"$slug","label":"$label","description":"Read your mail","resumeKey":"k"${if (fields.isEmpty()) "" else ",$fields"}}}""",
        )

    private fun signIn(fields: String = "") = msg(
        """{"id":"s1","role":"bot","kind":"signin","at":1,"text":"fallback text","signIn":{"url":"https://example.com/login","site":"example.com","reason":"Needs your account"${if (fields.isEmpty()) "" else ",$fields"}}}""",
    )

    private fun msg(json: String) = CompanionJson.decodeFromString<Message>(json)

    private var shown by mutableStateOf<Pair<Chat, Message>?>(null)
    private var generation by mutableIntStateOf(0)
    private var contentSet = false

    private fun show(message: Message, chat: Chat = this.chat) {
        shown = chat to message
        generation++
        if (!contentSet) {
            contentSet = true
            val wiring = WiringScene(fleet = Fleet(listOf(bot), emptyList()))
            compose.setContent {
                CompositionLocalProvider(
                    LocalCompanion provides wiring.environment,
                    LocalConnectorCardApi provides api,
                    LocalConnectionPageOpener provides { url: URI -> opened += url; pageOpens },
                    LocalSignInOpen provides SignInOpen { id, target -> openedScreens += id to target },
                ) {
                    CompanionTheme(darkTheme = false) {
                        val current = shown
                        if (current != null) key(generation) {
                            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) { MessageRow(current.first, current.second) }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun count(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().size

    private fun statusCalls() = api.calls.count { it.startsWith("status:") }

    // AC-26
    @Test
    fun aRequiredConnectorCardOffersConnectAndNotNow() {
        show(connector())
        compose.onNodeWithText("Gmail").assertIsDisplayed()
        compose.onNodeWithText("Read your mail").assertIsDisplayed()
        compose.onNodeWithText("Connect securely").assertIsDisplayed()
        compose.onNodeWithText("Not now").assertIsDisplayed()
        compose.onNodeWithText("Requested by your bot").assertIsDisplayed()
        assertEquals(0, statusCalls(), "a card that is not connecting asks nothing")
    }

    @Test
    fun aFailedCardOffersTryAgainAndShowsTheServerError() {
        show(connector("\"status\":\"failed\",\"error\":\"The sign-in expired.\""))
        compose.onNodeWithText("Try again").assertIsDisplayed()
        compose.onNodeWithText("The sign-in expired.").assertIsDisplayed()
    }

    @Test
    fun anMcpCardIsTitledSignInToAndSaysSignIn() {
        show(connector("\"source\":\"mcp\"", slug = "linear", label = "Linear"))
        compose.onNodeWithText("Sign in to Linear").assertIsDisplayed()
        compose.onNodeWithText("Sign in on the page that opens, never in chat.").assertIsDisplayed()
        compose.onNodeWithTag(CONNECTOR_CONNECT_TAG).assertIsDisplayed()
        compose.onNodeWithText("Sign in").assertIsDisplayed()
    }

    // AC-27
    @Test
    fun connectAsksTheComputerAndOpensTheHttpsLinkOnce() {
        show(connector())
        compose.onNodeWithTag(CONNECTOR_CONNECT_TAG).performClick()
        compose.waitForIdle()
        assertEquals(listOf("authorize:bot-1:c1:thread-bot-1"), api.calls)
        assertEquals(listOf("https://auth.example.com/start"), opened.map { it.toString() })
        assertEquals(0, count(CONNECTOR_PAGE_LINK_TAG))
    }

    @Test
    fun aLinkThatIsNotHttpsIsNeverOpened() {
        for (bad in listOf("http://auth.example.com/start", "javascript:alert(1)", "https:///nohost")) {
            api.authorizeResult = Result.success(URI(bad))
            show(connector())
            compose.onNodeWithTag(CONNECTOR_CONNECT_TAG).performClick()
            compose.waitForIdle()
            compose.onNodeWithText("This sign-in link is not valid. Try again.").assertIsDisplayed()
        }
        assertEquals(emptyList<URI>(), opened)
    }

    @Test
    fun aRefusedLinkRequestShowsTheServerSentence() {
        api.authorizeResult = Result.failure(APIError.BadUrl)
        show(connector())
        compose.onNodeWithTag(CONNECTOR_CONNECT_TAG).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("This sign-in link is not valid. Try again.").assertIsDisplayed()
        assertEquals(emptyList<URI>(), opened)
    }

    @Test
    fun anMcpConflictIsANoteAndAnAppConflictIsAnError() {
        api.authorizeResult = Result.failure(APIError.Status(409, "Sign in on your computer."))
        show(connector("\"source\":\"mcp\"", slug = "linear", label = "Linear"))
        compose.onNodeWithTag(CONNECTOR_CONNECT_TAG).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Sign in on your computer.").assertIsDisplayed()
        assertEquals(0, count(CONNECTOR_ERROR_TAG))
    }

    @Test
    fun aBrowserThatDidNotOpenLeavesTheLinkToTap() {
        pageOpens = false
        show(connector())
        compose.onNodeWithTag(CONNECTOR_CONNECT_TAG).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("If the page did not open, use the link below.").assertIsDisplayed()
        pageOpens = true
        compose.onNodeWithTag(CONNECTOR_PAGE_LINK_TAG).performClick()
        compose.waitForIdle()
        assertEquals(2, opened.size)
        assertEquals(0, count(CONNECTOR_PAGE_LINK_TAG))
    }

    @Test
    fun notNowDismissesTheCard() {
        show(connector())
        compose.onNodeWithTag(CONNECTOR_DISMISS_TAG).performClick()
        compose.waitForIdle()
        assertEquals(listOf("dismiss:bot-1:c1:thread-bot-1"), api.calls)
        assertEquals(0, count(CONNECTOR_CONNECT_TAG))
    }

    // AC-28
    @Test
    fun anAuthorizingCardChecksTheStatusWhileOnScreenAndShowsOpenAgain() {
        show(connector("\"status\":\"authorizing\""))
        compose.onNodeWithText("Open again").assertIsDisplayed()
        compose.onNodeWithText("Waiting for sign-in…").assertIsDisplayed()
        assertTrue(statusCalls() >= 1, "the first check runs at once")
        assertEquals("status:bot-1:c1:thread-bot-1", api.calls.first())
    }

    // AC-31
    @Test
    fun dismissedAndSupersededConnectorCardsDrawNothing() {
        for (field in listOf("\"dismissed\":true", "\"superseded\":true")) {
            show(connector(field))
            compose.onNodeWithText("Gmail").assertDoesNotExist()
            compose.onNodeWithText("fallback text").assertDoesNotExist()
            assertEquals(0, count(CONNECTOR_CONNECT_TAG))
        }
    }

    @Test
    fun aStoppedConnectorCardIsOneLineWithoutButtons() {
        show(connector("\"stopped\":true"))
        compose.onNodeWithText("Gmail · Stopped").assertIsDisplayed()
        assertEquals(0, count(CONNECTOR_CONNECT_TAG))
        assertEquals(0, count(CONNECTOR_DISMISS_TAG))
    }

    @Test
    fun aConnectedCardOffersContinueUntilItResumes() {
        show(connector("\"status\":\"connected\""))
        compose.onNodeWithText("Gmail · Connected").assertIsDisplayed()
        compose.onNodeWithTag(CONNECTOR_CONTINUE_TAG).performClick()
        compose.waitForIdle()
        assertEquals(listOf("resume:bot-1:c1:thread-bot-1"), api.calls)
    }

    @Test
    fun aResumedConnectedCardSaysContinuingWithoutAButton() {
        show(connector("\"status\":\"connected\",\"resumed\":true"))
        compose.onNodeWithText("Gmail · Connected · Continuing").assertIsDisplayed()
        assertEquals(0, count(CONNECTOR_CONTINUE_TAG))
    }

    @Test
    fun aFailedResumeShowsTheServerSentence() {
        api.resumeResult = Result.failure(APIError.Status(409, "Finish connecting every requested app first."))
        show(connector("\"status\":\"connected\""))
        compose.onNodeWithTag(CONNECTOR_CONTINUE_TAG).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Finish connecting every requested app first.").assertIsDisplayed()
    }

    @Test
    fun anUnreadableConnectorCardFallsBackToItsText() {
        show(msg("""{"id":"c2","role":"bot","kind":"connector","at":1,"text":"fallback text","connector":{"slug":3}}"""))
        compose.onNodeWithText("fallback text").assertIsDisplayed()
    }

    // AC-29
    @Test
    fun aWaitingSignInCardOffersCancelOpenAndDone() {
        show(signIn("\"place\":\"cloud\""))
        compose.onNodeWithText("Sign in needed").assertIsDisplayed()
        compose.onNodeWithText("example.com").assertIsDisplayed()
        compose.onNodeWithText("“Needs your account” — Scout").assertIsDisplayed()
        compose.onNodeWithText("Sign in on the cloud desktop Scout works on. Open shows it with Take control.").assertIsDisplayed()
        compose.onNodeWithText("You type the password into the page yourself. Seira never sees it.").assertIsDisplayed()
        compose.onNodeWithTag(SIGNIN_CANCEL_TAG).assertIsDisplayed()
        compose.onNodeWithTag(SIGNIN_OPEN_TAG).assertIsDisplayed()
        compose.onNodeWithTag(SIGNIN_DONE_TAG).assertIsDisplayed()
    }

    @Test
    fun doneAndCancelSendTheChoiceWithTheThread() {
        show(signIn("\"place\":\"vm\""))
        compose.onNodeWithTag(SIGNIN_DONE_TAG).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(SIGNIN_CANCEL_TAG).performClick()
        compose.waitForIdle()
        assertEquals(listOf("answer:bot-1:s1:thread-bot-1:done", "answer:bot-1:s1:thread-bot-1:cancel"), api.calls)
    }

    @Test
    fun aFailedAnswerShowsTheServerSentenceAndKeepsTheButtons() {
        api.answerResult = Result.failure(APIError.Status(404, "no such sign-in request"))
        show(signIn("\"place\":\"browser\""))
        compose.onNodeWithTag(SIGNIN_DONE_TAG).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("no such sign-in request").assertIsDisplayed()
        compose.onNodeWithTag(SIGNIN_DONE_TAG).assertIsDisplayed()
    }

    @Test
    fun openGoesToTheBrowserScreenForTheBuiltInBrowser() {
        show(signIn("\"place\":\"browser\""))
        compose.onNodeWithText("Sign in in Scout’s built-in browser. Open takes you to its browser, already in your control.").assertIsDisplayed()
        compose.onNodeWithTag(SIGNIN_OPEN_TAG).performClick()
        compose.waitForIdle()
        assertEquals(listOf<Pair<String, SignInOpenTarget>>("bot-1" to SignInOpenTarget.BrowserScreen), openedScreens)
    }

    @Test
    fun openGoesToTheComputerScreenForCloudAndVm() {
        for (place in listOf("cloud", "vm")) {
            openedScreens.clear()
            show(signIn("\"place\":\"$place\""))
            compose.onNodeWithTag(SIGNIN_OPEN_TAG).performClick()
            compose.waitForIdle()
            assertEquals(listOf<Pair<String, SignInOpenTarget>>("bot-1" to SignInOpenTarget.ComputerScreen), openedScreens, place)
        }
    }

    @Test
    fun aLocalSignInCardHasNoOpen() {
        show(signIn("\"place\":\"local\""))
        compose.onNodeWithText("Sign in on the computer Seira runs on, in the browser Scout is using.").assertIsDisplayed()
        assertEquals(0, count(SIGNIN_OPEN_TAG))
        compose.onNodeWithTag(SIGNIN_DONE_TAG).assertIsDisplayed()
    }

    // AC-30
    @Test
    fun noSignInPathOpensThePageAddressOnThePhone() {
        show(signIn("\"place\":\"cloud\""))
        compose.onNodeWithTag(SIGNIN_OPEN_TAG).performClick()
        compose.onNodeWithTag(SIGNIN_DONE_TAG).performClick()
        compose.waitForIdle()
        assertEquals(emptyList<URI>(), opened)

        val root = File("src/main/kotlin/bot/seira/companion/ui").takeIf(File::isDirectory)
            ?: File("app/src/main/kotlin/bot/seira/companion/ui")
        val source = File(root, "SignInCard.kt").readText()
        for (forbidden in listOf("card.url", ".url", "CustomTabs", "ACTION_VIEW", "startActivity", "openConnectionPage", "LocalUriHandler", "openUri")) {
            assertFalse(source.contains(forbidden), "SignInCard.kt must not use $forbidden")
        }
    }

    @Test
    fun aSignInCardInARoomUsesTheSendingBot() {
        val room = Chat.RoomChat(room())
        val message = signIn("\"place\":\"cloud\"").copy(from = Sender("bot-2", "Atlas", "blue"))
        show(message, chat = room)
        compose.onNodeWithTag(SIGNIN_DONE_TAG).performClick()
        compose.waitForIdle()
        assertEquals(listOf("answer:bot-2:s1:thread-room-1:done"), api.calls)
        assertTrue(cardBotId(room, message.copy(from = null)) == null)
    }

    // AC-31
    @Test
    fun closedSignInCardsAreOneLineWithoutButtons() {
        val cases = mapOf(
            "\"answeredInChat\":true" to "example.com · You answered in the chat",
            "\"superseded\":true" to "example.com · Replaced by a newer request",
            "\"stopped\":true" to "example.com · Stopped",
        )
        for ((field, line) in cases) {
            show(signIn(field))
            compose.onNodeWithText(line).assertIsDisplayed()
            assertEquals(0, count(SIGNIN_DONE_TAG))
            assertEquals(0, count(SIGNIN_CANCEL_TAG))
            assertEquals(0, count(SIGNIN_OPEN_TAG))
        }
    }

    @Test
    fun aSettledSignInCardSaysWhatHappenedAndWhatComesNext() {
        val cases = listOf(
            "\"done\":true" to "Signed in to example.com · Waiting to resume",
            "\"done\":true,\"resumed\":true" to "Signed in to example.com · Scout is continuing",
            "\"cancelled\":true" to "Sign-in to example.com declined · Waiting to resume",
            "\"done\":true,\"stopped\":true" to "Signed in to example.com · Stopped",
            "\"done\":true,\"savedLogin\":\"mail.example.com\"" to "Login saved for mail.example.com · Waiting to resume",
            "\"done\":true,\"error\":\"The bot is busy.\"" to "Signed in to example.com · Resuming failed",
        )
        for ((field, line) in cases) {
            show(signIn(field))
            compose.onNodeWithText(line).assertIsDisplayed()
            assertEquals(0, count(SIGNIN_DONE_TAG), field)
        }
    }

    @Test
    fun tryAgainSendsTheSameChoiceAgain() {
        show(signIn("\"cancelled\":true,\"error\":\"The bot is busy.\""))
        compose.onNodeWithText("The bot is busy.").assertIsDisplayed()
        compose.onNodeWithTag(SIGNIN_RETRY_TAG).performClick()
        compose.waitForIdle()
        assertEquals(listOf("answer:bot-1:s1:thread-bot-1:cancel"), api.calls)
    }

    @Test
    fun anUnreadableSignInCardFallsBackToItsText() {
        show(msg("""{"id":"s2","role":"bot","kind":"signin","at":1,"text":"fallback text"}"""))
        compose.onNodeWithText("fallback text").assertIsDisplayed()
    }
}
