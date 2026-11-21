package com.openmausbot.companion.core

import java.net.URI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder

/** A bot asking the person to connect an app or sign in to an MCP server: a message of kind `connector`. */
@Serializable
data class ConnectorCardData(
    val source: String? = null,
    val slug: String,
    val label: String,
    val description: String = "",
    val status: String = "required",
    val resumeKey: String = "",
    val alias: String? = null,
    val error: String? = null,
    val dismissed: Boolean? = null,
    val superseded: Boolean? = null,
    val resumed: Boolean? = null,
    val stopped: Boolean? = null,
) {
    enum class Status { REQUIRED, AUTHORIZING, CONNECTED, FAILED }

    /** A status this build does not know is still a card that asks. */
    val connectorStatus: Status
        get() = when (status) {
            "authorizing" -> Status.AUTHORIZING
            "connected" -> Status.CONNECTED
            "failed" -> Status.FAILED
            else -> Status.REQUIRED
        }

    val isMcp: Boolean get() = source == "mcp"
    val isGone: Boolean get() = dismissed == true || superseded == true
}

/** A bot asking the person to sign in to a website, in the place the bot works: a message of kind `signin`. */
@Serializable
data class SignInRequestCardData(
    val url: String,
    val site: String,
    val reason: String? = null,
    val place: String = "local",
    val done: Boolean? = null,
    val cancelled: Boolean? = null,
    val superseded: Boolean? = null,
    val savedLogin: String? = null,
    val resumed: Boolean? = null,
    val error: String? = null,
    val stopped: Boolean? = null,
    val answeredInChat: Boolean? = null,
) {
    enum class Place { BROWSER, CLOUD, VM, LOCAL }

    enum class Outcome { DONE, CANCELLED }

    /** A place this build does not know has no screen to open, like `local`. */
    val signInPlace: Place
        get() = when (place) {
            "browser" -> Place.BROWSER
            "cloud" -> Place.CLOUD
            "vm" -> Place.VM
            else -> Place.LOCAL
        }

    fun waits(): Boolean = done != true && cancelled != true && superseded != true && answeredInChat != true

    fun outcome(): Outcome? = when {
        done == true -> Outcome.DONE
        cancelled == true -> Outcome.CANCELLED
        else -> null
    }
}

enum class SignInChoice(val route: String) { DONE("done"), CANCEL("cancel") }

/** The screen a sign-in card's Open goes to. The page address of the card is never opened on the phone. */
sealed interface SignInOpenTarget {
    data object BrowserScreen : SignInOpenTarget
    data object ComputerScreen : SignInOpenTarget
}

object ConnectorCards {
    const val STATUS_CHECK_MS = 4_000L
    const val STATUS_WATCH_MS = 12 * 60 * 1_000L

    fun openTarget(card: SignInRequestCardData): SignInOpenTarget? = when (card.signInPlace) {
        SignInRequestCardData.Place.BROWSER -> SignInOpenTarget.BrowserScreen
        SignInRequestCardData.Place.CLOUD, SignInRequestCardData.Place.VM -> SignInOpenTarget.ComputerScreen
        SignInRequestCardData.Place.LOCAL -> null
    }

    /** An https address with a host, or null: the only kind of link a connector card opens. */
    fun secureLink(raw: String?): URI? {
        val url = raw?.let { runCatching { URI(it) }.getOrNull() } ?: return null
        return url.takeIf { it.scheme.equals("https", ignoreCase = true) && !it.host.isNullOrEmpty() }
    }

    /**
     * Ask for the status every [STATUS_CHECK_MS] until the card is connected or [STATUS_WATCH_MS] have passed
     * since [startedAt]. The first check runs at once. A failed check counts as not connected.
     * @return true when the card is connected.
     */
    suspend fun watchStatus(
        startedAt: Long,
        now: () -> Long,
        pause: suspend (Long) -> Unit = { delay(it) },
        check: suspend () -> Boolean,
    ): Boolean {
        while (now() - startedAt < STATUS_WATCH_MS) {
            val connected = try {
                check()
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Throwable) {
                false
            }
            if (connected) return true
            pause(STATUS_CHECK_MS)
        }
        return false
    }
}

@Serializable
data class ConnectorCardStatus(
    val connected: Boolean = false,
    val pending: Boolean? = null,
    val status: String? = null,
)

/** What the sign-in and connection cards call. [Session] answers it for the app; tests answer it by hand. */
interface ConnectorCardApi {
    suspend fun authorize(botId: String, messageId: String, threadId: String): Result<URI>
    suspend fun status(botId: String, messageId: String, threadId: String): Result<ConnectorCardStatus>
    suspend fun resume(botId: String, messageId: String, threadId: String): Result<Unit>
    suspend fun dismiss(botId: String, messageId: String, threadId: String): Result<Unit>
    suspend fun answerSignIn(botId: String, messageId: String, threadId: String, choice: SignInChoice): Result<Unit>
}

/** A card payload this build cannot read is no card: the message keeps its text and the page still decodes. */
internal abstract class LenientCardSerializer<T : Any>(private val inner: KSerializer<T>) : KSerializer<T?> {
    override val descriptor: SerialDescriptor = inner.nullable.descriptor

    override fun deserialize(decoder: Decoder): T? {
        val json = decoder as? JsonDecoder ?: return null
        val element = json.decodeJsonElement()
        return try {
            json.json.decodeFromJsonElement(inner, element)
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    override fun serialize(encoder: Encoder, value: T?) {
        if (value == null) encoder.encodeNull() else encoder.encodeSerializableValue(inner, value)
    }
}

internal object ConnectorCardSerializer : LenientCardSerializer<ConnectorCardData>(ConnectorCardData.serializer())

internal object SignInRequestCardSerializer : LenientCardSerializer<SignInRequestCardData>(SignInRequestCardData.serializer())
