package bot.seira.companion.core

import java.net.URI
import java.security.MessageDigest
import java.util.Base64
import kotlinx.serialization.Serializable
import org.bouncycastle.crypto.hpke.HPKE

/**
 * The only body the phone sends for a credential card. The server schema is strict:
 * exactly these eight fields, and the value never leaves the phone in clear.
 */
@Serializable
data class PhoneSecretEnvelope(
    val version: Int,
    val threadId: String,
    val keyId: String,
    val deviceId: String,
    val target: String,
    val requestKey: String,
    val encapsulatedKey: String,
    val ciphertext: String,
) {
    /** Security invariant: a log line never carries the sealed parts. */
    override fun toString(): String = "PhoneSecretEnvelope(target=$target, keyId=$keyId)"
}

data class PhoneSecretRequestContext(
    val deviceId: String,
    val botId: String,
    val threadId: String,
    val messageId: String,
    val target: String,
    val requestKey: String,
)

class PhoneSecretException(val reason: Reason, cause: Throwable? = null) : Exception(reason.name, cause) {
    enum class Reason {
        /** This pairing has no sealing key or no device id: pair again by QR code. */
        UNAVAILABLE,
        INSECURE_TRANSPORT,
        /** The computer refuses this target from a phone. */
        COMPUTER_ONLY,
        INVALID_REQUEST,
        INVALID_PUBLIC_KEY,
        INVALID_CREDENTIAL,
        ENCRYPTION_FAILED,
    }
}

/** The computer's answer to a sealed credential. */
@Serializable
data class CredentialProvided(val provided: Boolean = true, val resumed: Boolean = false)

/** A sealed value kept in memory so a retry resends the same bytes, which the computer treats as one operation. */
class PreparedPhoneCredential internal constructor(
    internal val identity: String,
    val connectionId: String,
    val botId: String,
    val messageId: String,
    val envelope: PhoneSecretEnvelope,
) {
    override fun toString(): String = "PreparedPhoneCredential(messageId=$messageId, envelope=$envelope)"
}

/** Where the send of one sealed card stands. No entry means nothing is in flight and no answer is kept. */
sealed interface CredentialSend {
    data object Sending : CredentialSend
    data class Sent(val answer: CredentialProvided) : CredentialSend
    data class Failed(val error: Exception) : CredentialSend
}

/** RFC 9180 base mode with DHKEM(P-256, HKDF-SHA256), HKDF-SHA256 and AES-256-GCM, as the computer opens it. */
object PhoneSecretCrypto {
    const val VERSION = 1
    const val MAXIMUM_CREDENTIAL_BYTES = 4_096
    const val INFO = "OpenMausBot phone credential v1"
    private const val AAD_LABEL = "openmausbot-phone-credential-v1"
    private const val PUBLIC_KEY_BYTES = 65
    private const val KEY_ID_BYTES = 16
    private const val KEY_ID_CHARACTERS = 22

    /**
     * Targets the computer refuses from a phone. The computer's own refusal stays the
     * fallback when this list falls behind it.
     */
    val COMPUTER_ONLY_TARGETS: Set<String> = setOf(
        "onePasswordToken",
        "openaiApiKey",
        "anthropicApiKey",
        "openrouterApiKey",
        "mistralApiKey",
        "cerebrasApiKey",
        "groqApiKey",
        "greenferenceApiKey",
        "modelgoApiKey",
        "metaApiKey",
        "deepseekApiKey",
    )

    private fun suite() = HPKE(HPKE.mode_base, HPKE.kem_P256_SHA256, HPKE.kdf_HKDF_SHA256, HPKE.aead_AES_GCM256)

    /** The canonical unpadded base64url form of an uncompressed P-256 point on the curve, or null. */
    fun normalizedPublicKey(encoded: String): String? {
        val raw = decodeBase64Url(encoded) ?: return null
        if (raw.size != PUBLIC_KEY_BYTES || raw[0] != 0x04.toByte()) return null
        if (runCatching { suite().deserializePublicKey(raw) }.isFailure) return null
        return encoded.takeIf { encodeBase64Url(raw) == it }
    }

    fun publicKeyId(encoded: String): String? {
        val normalized = normalizedPublicKey(encoded) ?: return null
        return keyIdOf(decodeBase64Url(normalized) ?: return null)
    }

    fun authenticatedData(keyId: String, context: PhoneSecretRequestContext): ByteArray? {
        val routeIds = listOf(context.deviceId, context.botId, context.threadId, context.messageId, context.requestKey)
        if (keyId.length != KEY_ID_CHARACTERS || !keyId.all(::isBase64UrlCharacter)) return null
        if (!routeIds.all(::isRouteId) || !isTargetId(context.target)) return null
        return listOf(
            AAD_LABEL,
            keyId,
            context.deviceId,
            context.botId,
            context.threadId,
            context.messageId,
            context.target,
            context.requestKey,
        ).joinToString("\n").toByteArray(Charsets.UTF_8)
    }

    /**
     * The UTF-8 size of [value] once trimmed, as [encrypt] seals it. Security invariant: it
     * copies nothing, so checking a typed value leaves no extra plaintext in memory.
     */
    fun credentialByteCount(value: CharSequence): Int {
        var start = 0
        var end = value.length
        while (start < end && value[start].isWhitespace()) start++
        while (end > start && value[end - 1].isWhitespace()) end--
        var bytes = 0
        var index = start
        while (index < end) {
            val character = value[index]
            bytes += when {
                character.code < 0x80 -> 1
                character.code < 0x800 -> 2
                character.isHighSurrogate() && index + 1 < end && value[index + 1].isLowSurrogate() -> {
                    index++
                    4
                }
                // The JDK encoder writes "?" for a lone surrogate.
                character.isSurrogate() -> 1
                else -> 3
            }
            index++
        }
        return bytes
    }

    fun fitsCredentialLimit(value: CharSequence): Boolean = credentialByteCount(value) in 1..MAXIMUM_CREDENTIAL_BYTES

    fun encrypt(value: String, publicKey: String, context: PhoneSecretRequestContext): PhoneSecretEnvelope {
        if (!fitsCredentialLimit(value)) throw PhoneSecretException(PhoneSecretException.Reason.INVALID_CREDENTIAL)
        val plaintext = value.trim().toByteArray(Charsets.UTF_8)
        try {
            val normalized = normalizedPublicKey(publicKey)
                ?: throw PhoneSecretException(PhoneSecretException.Reason.INVALID_PUBLIC_KEY)
            val raw = decodeBase64Url(normalized)
                ?: throw PhoneSecretException(PhoneSecretException.Reason.INVALID_PUBLIC_KEY)
            val keyId = keyIdOf(raw)
            val aad = authenticatedData(keyId, context)
                ?: throw PhoneSecretException(PhoneSecretException.Reason.INVALID_REQUEST)
            val (encapsulatedKey, ciphertext) = try {
                val hpke = suite()
                val sender = hpke.setupBaseS(hpke.deserializePublicKey(raw), INFO.toByteArray(Charsets.UTF_8))
                val sealed = sender.seal(aad, plaintext)
                sender.encapsulation to sealed
            } catch (error: Exception) {
                throw PhoneSecretException(PhoneSecretException.Reason.ENCRYPTION_FAILED, error)
            }
            return PhoneSecretEnvelope(
                version = VERSION,
                threadId = context.threadId,
                keyId = keyId,
                deviceId = context.deviceId,
                target = context.target,
                requestKey = context.requestKey,
                encapsulatedKey = encodeBase64Url(encapsulatedKey),
                ciphertext = encodeBase64Url(ciphertext),
            )
        } finally {
            plaintext.fill(0)
        }
    }

    private fun keyIdOf(raw: ByteArray): String =
        encodeBase64Url(MessageDigest.getInstance("SHA-256").digest(raw).copyOf(KEY_ID_BYTES))

    private fun isBase64UrlCharacter(character: Char): Boolean =
        character in 'A'..'Z' || character in 'a'..'z' || character in '0'..'9' || character == '-' || character == '_'

    private fun isRouteId(value: String): Boolean =
        value.length in 1..128 && value.all(::isBase64UrlCharacter)

    private fun isTargetId(value: String): Boolean =
        value.length in 1..64 &&
            (value[0] in 'A'..'Z' || value[0] in 'a'..'z') &&
            value.all { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' }

    private fun encodeBase64Url(data: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(data)

    private fun decodeBase64Url(value: String): ByteArray? {
        if (value.isEmpty() || !value.all(::isBase64UrlCharacter)) return null
        return runCatching { Base64.getUrlDecoder().decode(value) }.getOrNull()
    }
}

/** The card's help link, only as a plain https address with a host and no sign-in part. */
val SecretRequestCardData.safeHelpUrl: String?
    get() {
        val raw = helpUrl?.trim()?.takeIf(String::isNotEmpty) ?: return null
        val uri = runCatching { URI(raw) }.getOrNull() ?: return null
        val secure = uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrEmpty() && uri.rawUserInfo == null
        return raw.takeIf { secure }
    }

/** A companion pairing made after secure phone entry existed: the computer's sealing key and this phone's device id. */
val Connection.pairedForSecrets: Boolean
    get() = !pairedWithServer && !secretPublicKey.isNullOrEmpty() && !companionDeviceId.isNullOrEmpty()

/** The route this connection dials keeps a credential off the local network in clear. */
val Connection.dialsProtectedRoute: Boolean
    get() {
        val endpoint = activeEndpoint ?: CompanionEndpoint.direct(host, port, priority = 0) ?: return false
        return allowsEndpoint(endpoint) && endpoint.protectsCredentials
    }
