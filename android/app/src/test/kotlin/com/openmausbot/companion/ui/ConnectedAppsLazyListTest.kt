package com.openmausbot.companion.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNode
import androidx.compose.ui.test.performTextInput
import com.openmausbot.companion.core.Connection
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ConnectedAppsLazyListTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var server: MockWebServer
    private val hits = ConcurrentHashMap<String, AtomicInteger>()

    private val catalog = buildString {
        append("""{"configured":true,"cards":[""")
        append(
            (0 until 500).joinToString(",") { i ->
                """{"slug":"app$i","label":"App $i","blurb":"Blurb $i"}"""
            },
        )
        append("]}")
    }
    private val connected = """{"configured":true,"credentialStore":"ok","services":{}}"""

    @BeforeTest
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                hits.getOrPut(path) { AtomicInteger(0) }.incrementAndGet()
                val body = when (path) {
                    "/api/connectors/catalog" -> catalog
                    "/api/connectors/connected" -> connected
                    else -> return MockResponse().setResponseCode(404)
                }
                return MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(body)
            }
        }
        server.start()
    }

    @AfterTest
    fun tearDown() {
        server.shutdown()
    }

    private fun show() {
        val scene = WiringScene(connection = requireNotNull(Connection.parse(server.url("/").toString())))
        compose.setContent {
            CompositionLocalProvider(LocalCompanion provides scene.environment) {
                CompanionTheme(darkTheme = false) {
                    ConnectedAppsScreen(onBack = {})
                }
            }
        }
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag(CONNECTOR_CARD_TAG).fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitForIdle()
    }

    private fun composedCards(): Int =
        compose.onAllNodesWithTag(CONNECTOR_CARD_TAG).fetchSemanticsNodes().size

    private fun asked(path: String): Int = hits[path]?.get() ?: 0

    @Test
    fun `a catalog of 500 cards composes only the cards that fit on screen`() {
        show()
        val composed = composedCards()
        assertTrue(composed in 1 until 50, "composed $composed of 500 cards")
    }

    @Test
    fun `typing filters the list and asks the computer for nothing more`() {
        show()
        val catalogBefore = asked("/api/connectors/catalog")
        val connectedBefore = asked("/api/connectors/connected")

        compose.onNode(hasSetTextAction()).performTextInput("app49")
        compose.waitForIdle()

        assertEquals(11, composedCards())
        assertEquals(catalogBefore, asked("/api/connectors/catalog"))
        assertEquals(connectedBefore, asked("/api/connectors/connected"))
    }
}
