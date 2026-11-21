package com.openmausbot.companion.ui

import android.os.Looper
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import com.openmausbot.companion.core.Bot
import com.openmausbot.companion.core.Chat
import com.openmausbot.companion.core.CompanionEndpoint
import com.openmausbot.companion.core.CompanionEndpointKind
import com.openmausbot.companion.core.Connection
import com.openmausbot.companion.core.CredentialSend
import com.openmausbot.companion.core.Fleet
import com.openmausbot.companion.core.Frame
import com.openmausbot.companion.core.Message
import com.openmausbot.companion.core.ModelSelection
import com.openmausbot.companion.core.SecretRequestCardData
import com.openmausbot.companion.core.Session
import com.openmausbot.companion.core.StreamFrame
import java.io.File
import java.net.InetAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The credential card on the phone: it offers a field only to a pairing that can seal on a
 * protected route, and the typed value goes when the app stops, the card leaves the screen,
 * or the request closes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-xxhdpi")
class SecretCardTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val publicKey = "BIPBQ12_dWnF1DZLsTZO3Vg0NGjds5-jp9h3jhjr2To7bJelczS0LM82rfXV68PmSJhz2ePosj3fL974XckCpDU"
    private val bot = Bot(
        id = "mochi",
        threadId = "t1",
        name = "Mochi",
        title = "",
        description = "",
        notifications = true,
        color = "blue",
        unread = false,
        modelSelection = ModelSelection("instance-1", "model-1"),
        createdAt = 0.0,
    )
    private val pending = SecretRequestCardData(target = "ttsKey", requestKey = "credential-request-1", label = "Voice key")

    private fun message(secret: SecretRequestCardData) =
        Message("m1", Message.Role.BOT, Message.Kind.SECRET, 1.0, secret = secret)

    private fun tailnet(key: String? = publicKey, device: String? = "paired-device-1", port: Int = 8810): Connection {
        val endpoint = assertNotNull(
            CompanionEndpoint.create("http://mac.fixture.ts.net:$port", CompanionEndpointKind.TAILNET, 0),
        )
        return Connection(
            id = "c1",
            name = "Mac",
            host = endpoint.host,
            port = endpoint.port,
            activeEndpoint = endpoint,
            endpoints = listOf(endpoint),
            secretPublicKey = key,
            companionDeviceId = device,
        ).establishingRoutePolicyFromInvite()
    }

    private var shown by mutableStateOf(true)
    private var secret by mutableStateOf(pending)

    private fun show(connection: Connection, http: OkHttpClient = OkHttpClient(), live: Boolean = true): WiringScene {
        val wiring = WiringScene(
            connection = connection,
            httpClient = http,
            fleet = Fleet(listOf(bot), emptyList()),
            events = {
                flow {
                    emit(StreamFrame(Frame.Hello(cursor = "fixture:1", resumed = false)))
                    awaitCancellation()
                }
            },
        )
        compose.setContent {
            CompositionLocalProvider(LocalCompanion provides wiring.environment) {
                CompanionTheme(darkTheme = false) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        if (shown) MessageRow(Chat.BotChat(bot), message(secret))
                    }
                }
            }
        }
        wiring.session.connect()
        compose.waitUntil(5_000) {
            if (live) wiring.session.status.value is Session.Status.Live else wiring.session.connection.value != null
        }
        compose.waitForIdle()
        return wiring
    }

    private val politeLiveRegion = SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)

    private fun fields() = compose.onAllNodesWithTag(CREDENTIAL_FIELD_TAG).fetchSemanticsNodes().size

    private fun typed(): Int =
        compose.onNodeWithTag(CREDENTIAL_FIELD_TAG).fetchSemanticsNode().config[SemanticsProperties.EditableText].text.length

    private fun windowIsSecure(): Boolean =
        compose.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0

    @Test
    fun anOlderPairingAsksToPairAgainAndOffersNoField() {
        show(tailnet(key = null))
        compose.onNodeWithText("Pair again to enter here", useUnmergedTree = true).assertExists()
        assertEquals(0, fields())
        assertFalse(windowIsSecure())
    }

    @Test
    fun aLocalRouteAsksForASecureConnectionAndOffersNoField() {
        show(Connection(id = "c1", name = "Mac", host = "192.168.1.5", port = 8810, secretPublicKey = publicKey, companionDeviceId = "d1"))
        compose.onNodeWithText("Secure connection required", useUnmergedTree = true).assertExists()
        assertEquals(0, fields())
    }

    @Test
    fun aComputerOnlyTargetPointsToTheComputer() {
        secret = pending.copy(target = "openaiApiKey")
        show(tailnet())
        compose.onNodeWithText("Enter this key in Seira on your computer.", useUnmergedTree = true).assertExists()
        assertEquals(0, fields())
    }

    @Test
    fun theTypedValueGoesWhenTheAppStops() {
        show(tailnet())
        assertTrue(windowIsSecure(), "the entry keeps the window out of screenshots")
        compose.onNodeWithTag(CREDENTIAL_FIELD_TAG).performTextInput("sk-test")
        assertEquals(7, typed())

        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.waitForIdle()

        assertEquals(0, typed())
    }

    @Test
    fun theTypedValueGoesWhenTheCardLeavesTheScreen() {
        show(tailnet())
        compose.onNodeWithTag(CREDENTIAL_FIELD_TAG).performTextInput("sk-test")

        shown = false
        compose.waitForIdle()
        assertFalse(windowIsSecure(), "the window is secure only while the entry shows")

        shown = true
        compose.waitForIdle()
        assertEquals(0, typed())
    }

    @Test
    fun theTypedValueGoesWhenTheRequestCloses() {
        show(tailnet())
        compose.onNodeWithTag(CREDENTIAL_FIELD_TAG).performTextInput("sk-test")

        secret = pending.copy(provided = true)
        compose.waitForIdle()
        compose.onNodeWithText("Saved securely on your computer.", useUnmergedTree = true).assertExists()
        assertEquals(0, fields())

        secret = pending
        compose.waitForIdle()
        assertEquals(0, typed())
    }

    @Test
    fun anOlderPairingCardIsALiveRegion() {
        show(tailnet(key = null))
        compose.onNode(hasContentDescription("Pair again by QR code", substring = true)).assert(politeLiveRegion)
    }

    @Test
    fun aServerPairingPointsToSeiraForThatServer() {
        val server = tailnet().copy(serverEnvironmentId = "environment-1")
        show(server, live = false)
        compose.onNodeWithText("Enter it in Seira for this server", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Pair again to enter here", useUnmergedTree = true).assertDoesNotExist()
        assertEquals(0, fields())
    }

    @Test
    fun theLimitCountsBytesAndSaysWhenAValueIsTooLong() {
        show(tailnet())
        compose.onNodeWithTag(CREDENTIAL_FIELD_TAG).performTextInput("é".repeat(2048))
        compose.onNodeWithTag(CREDENTIAL_SAVE_TAG).assertIsEnabled()
        compose.onNodeWithText("This value is too long. Paste only the key.", useUnmergedTree = true).assertDoesNotExist()

        compose.onNodeWithTag(CREDENTIAL_FIELD_TAG).performTextInput("é")
        compose.onNodeWithTag(CREDENTIAL_SAVE_TAG).assertIsNotEnabled()
        compose.onNode(hasText("This value is too long. Paste only the key.") and politeLiveRegion).assertExists()
    }

    @Test
    fun theSendKeepsGoingWhenTheCardLeavesTheScreen() {
        val answer = CountDownLatch(1)
        val provides = AtomicInteger(0)
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.path?.endsWith("/provide") != true) return MockResponse().setResponseCode(404)
                provides.incrementAndGet()
                answer.await(10, TimeUnit.SECONDS)
                return MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody("""{"provided":true,"resumed":false}""")
            }
        }
        server.start()
        val http = OkHttpClient.Builder()
            .dns(object : Dns {
                override fun lookup(hostname: String) = listOf(InetAddress.getByName("127.0.0.1"))
            })
            .build()
        try {
            val wiring = show(tailnet(port = server.port), http)
            compose.onNodeWithTag(CREDENTIAL_FIELD_TAG).performTextInput("sk-test")
            compose.onNodeWithTag(CREDENTIAL_SAVE_TAG).performClick()
            compose.waitUntil(5_000) { provides.get() == 1 }

            shown = false
            compose.waitForIdle()
            answer.countDown()
            compose.waitUntil(5_000) {
                shadowOf(Looper.getMainLooper()).idle()
                wiring.session.credentialSends.value.values.none { it == CredentialSend.Sending }
            }
            val outcome = wiring.session.credentialSends.value.values.single()
            assertTrue(outcome is CredentialSend.Sent, "the send ended as $outcome")

            shown = true
            compose.waitForIdle()
            compose.onNode(hasText("Encrypted and saved on your computer") and politeLiveRegion).assertExists()
            assertEquals(1, provides.get())
            assertEquals(0, fields())
        } finally {
            answer.countDown()
            server.shutdown()
            http.connectionPool.evictAll()
        }
    }

    @Test
    fun theTypedValueIsNeverKeptInSavedState() {
        val source = listOf(
            File("src/main/kotlin/com/openmausbot/companion/ui/SecretCard.kt"),
            File("app/src/main/kotlin/com/openmausbot/companion/ui/SecretCard.kt"),
        ).first(File::isFile).readText()
        assertFalse("rememberSaveable" in source)
        assertFalse("rememberTextFieldState" in source)
    }
}
