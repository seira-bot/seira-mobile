package bot.seira.companion.core

import java.math.BigInteger
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.jsonObject
import org.bouncycastle.crypto.hpke.HPKE

/** The same vectors as iOS `PhoneSecretTests`, and a seal opened by the computer's side of RFC 9180. */
class PhoneSecretTest {
    private val publicKey = "BIPBQ12_dWnF1DZLsTZO3Vg0NGjds5-jp9h3jhjr2To7bJelczS0LM82rfXV68PmSJhz2ePosj3fL974XckCpDU"
    private val context = PhoneSecretRequestContext(
        deviceId = "paired-device-1",
        botId = "bot-1",
        threadId = "thread-1",
        messageId = "message-1",
        target = "ttsKey",
        requestKey = "credential-request-1",
    )

    @Test
    fun keyIdMatchesTheIosVector() {
        assertEquals(publicKey, PhoneSecretCrypto.normalizedPublicKey(publicKey))
        assertEquals("taWSR_nZ7ojlH_0Z3tar6Q", PhoneSecretCrypto.publicKeyId(publicKey))
    }

    @Test
    fun authenticatedDataMatchesTheIosVector() {
        val aad = assertNotNull(PhoneSecretCrypto.authenticatedData("taWSR_nZ7ojlH_0Z3tar6Q", context))
        assertEquals(
            listOf(
                "openmausbot-phone-credential-v1",
                "taWSR_nZ7ojlH_0Z3tar6Q",
                "paired-device-1",
                "bot-1",
                "thread-1",
                "message-1",
                "ttsKey",
                "credential-request-1",
            ).joinToString("\n"),
            aad.toString(Charsets.UTF_8),
        )
    }

    @Test
    fun authenticatedDataRefusesIdsOutsideTheRouteAlphabet() {
        assertNull(PhoneSecretCrypto.authenticatedData("taWSR_nZ7ojlH_0Z3tar6Q", context.copy(botId = "bot/1")))
        assertNull(PhoneSecretCrypto.authenticatedData("taWSR_nZ7ojlH_0Z3tar6Q", context.copy(threadId = "")))
        assertNull(PhoneSecretCrypto.authenticatedData("taWSR_nZ7ojlH_0Z3tar6Q", context.copy(requestKey = "k".repeat(129))))
        assertNull(PhoneSecretCrypto.authenticatedData("taWSR_nZ7ojlH_0Z3tar6Q", context.copy(target = "1ttsKey")))
        assertNull(PhoneSecretCrypto.authenticatedData("taWSR_nZ7ojlH_0Z3tar6Q", context.copy(target = "tts_key")))
        assertNull(PhoneSecretCrypto.authenticatedData("short", context))
        assertFailsWith<PhoneSecretException> {
            PhoneSecretCrypto.encrypt("value", publicKey, context.copy(messageId = "message 1"))
        }.also { assertEquals(PhoneSecretException.Reason.INVALID_REQUEST, it.reason) }
    }

    @Test
    fun refusesKeysThatAreNotOneCanonicalUncompressedPointOnTheCurve() {
        val raw = decode(publicKey)
        val compressed = byteArrayOf(if (raw[64].toInt() and 1 == 0) 0x02 else 0x03) + raw.copyOfRange(1, 33)
        val offCurve = raw.copyOf().also { it[64] = (it[64].toInt() xor 0x01).toByte() }
        val refused = listOf(
            "compressed" to encode(compressed),
            "uncompressed prefix on 33 bytes" to encode(byteArrayOf(0x04) + raw.copyOfRange(1, 33)),
            "one character short" to publicKey.dropLast(1),
            "padded" to "$publicKey=",
            "standard alphabet" to publicKey.replace('-', '+').replace('_', '/'),
            "off the curve" to encode(offCurve),
            "empty" to "",
            "whitespace" to " $publicKey",
        )
        refused.forEach { (name, key) ->
            assertNull(PhoneSecretCrypto.normalizedPublicKey(key), name)
            assertNull(PhoneSecretCrypto.publicKeyId(key), name)
        }
        val error = assertFailsWith<PhoneSecretException> { PhoneSecretCrypto.encrypt("value", encode(offCurve), context) }
        assertEquals(PhoneSecretException.Reason.INVALID_PUBLIC_KEY, error.reason)
    }

    @Test
    fun refusesAnEmptyOrOversizedValueBeforeSealing() {
        listOf("", "   ", "\n\t ").forEach { value ->
            val error = assertFailsWith<PhoneSecretException> { PhoneSecretCrypto.encrypt(value, publicKey, context) }
            assertEquals(PhoneSecretException.Reason.INVALID_CREDENTIAL, error.reason)
        }
        val oversized = assertFailsWith<PhoneSecretException> {
            PhoneSecretCrypto.encrypt("x".repeat(4_097), publicKey, context)
        }
        assertEquals(PhoneSecretException.Reason.INVALID_CREDENTIAL, oversized.reason)
        val multiByte = assertFailsWith<PhoneSecretException> {
            PhoneSecretCrypto.encrypt("é".repeat(2_049), publicKey, context)
        }
        assertEquals(PhoneSecretException.Reason.INVALID_CREDENTIAL, multiByte.reason, "the limit counts UTF-8 bytes")

        val largest = PhoneSecretCrypto.encrypt("x".repeat(4_096), publicKey, context)
        assertEquals(5_483, largest.ciphertext.length, "4096 bytes and the 16-byte tag, in unpadded base64url")
    }

    @Test
    fun theLimitCheckCountsTheBytesThatGetSealed() {
        val samples = listOf(
            "", "   ", " sk-test\n", "é".repeat(2_048), "€".repeat(1_366), "😀", " 😀é€x ",
            "\uD83D", "a\uDE00b", "\uD83Dx", "x".repeat(4_096), "\u3000key\u2003",
        )
        samples.forEach { value ->
            assertEquals(value.trim().toByteArray(Charsets.UTF_8).size, PhoneSecretCrypto.credentialByteCount(value), "for <$value>")
        }

        assertTrue(PhoneSecretCrypto.fitsCredentialLimit("é".repeat(2_048)), "2048 two-byte characters are 4096 bytes")
        assertFalse(PhoneSecretCrypto.fitsCredentialLimit("€".repeat(1_366)), "1366 characters are 4098 bytes")
        assertFalse(PhoneSecretCrypto.fitsCredentialLimit(" \n\t"))
        val refused = assertFailsWith<PhoneSecretException> {
            PhoneSecretCrypto.encrypt("€".repeat(1_366), publicKey, context)
        }
        assertEquals(PhoneSecretException.Reason.INVALID_CREDENTIAL, refused.reason)
    }

    @Test
    fun sealsOnlyTheTrimmedValueAndSendsExactlyEightFields() {
        val envelope = PhoneSecretCrypto.encrypt("  android-secret-that-must-not-leak \n", publicKey, context)
        val wire = CompanionJson.encodeToString(PhoneSecretEnvelope.serializer(), envelope)
        val fields = CompanionJson.parseToJsonElement(wire).jsonObject.keys

        assertEquals(
            setOf("version", "threadId", "keyId", "deviceId", "target", "requestKey", "encapsulatedKey", "ciphertext"),
            fields,
        )
        assertEquals(1, envelope.version)
        assertEquals("taWSR_nZ7ojlH_0Z3tar6Q", envelope.keyId)
        assertEquals(87, envelope.encapsulatedKey.length)
        assertEquals(65, decode(envelope.encapsulatedKey).size)
        assertEquals(
            "android-secret-that-must-not-leak".length + 16,
            decode(envelope.ciphertext).size,
            "the trimmed value and the tag",
        )
        assertFalse(wire.contains("android-secret-that-must-not-leak"))
        assertFalse(envelope.toString().contains(envelope.ciphertext))
        assertFalse(envelope.toString().contains(envelope.encapsulatedKey))
    }

    @Test
    fun theComputersKeyOpensTheSealWithBouncyCastle() {
        val hpke = HPKE(HPKE.mode_base, HPKE.kem_P256_SHA256, HPKE.kdf_HKDF_SHA256, HPKE.aead_AES_GCM256)
        val recipient = hpke.generatePrivateKey()
        val encodedKey = encode(hpke.serializePublicKey(recipient.public))

        val envelope = PhoneSecretCrypto.encrypt("sk-bouncy-round-trip", encodedKey, context)

        val keyId = assertNotNull(PhoneSecretCrypto.publicKeyId(encodedKey))
        assertEquals(keyId, envelope.keyId)
        val opener = hpke.setupBaseR(decode(envelope.encapsulatedKey), recipient, PhoneSecretCrypto.INFO.toByteArray())
        val aad = assertNotNull(PhoneSecretCrypto.authenticatedData(keyId, context))
        assertEquals("sk-bouncy-round-trip", opener.open(aad, decode(envelope.ciphertext)).toString(Charsets.UTF_8))
    }

    @Test
    fun anIndependentRfc9180OpenerOpensTheSealAndRefusesAnotherCard() {
        val recipient = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
        val point = uncompressed(recipient.public as ECPublicKey)
        val encodedKey = encode(point)

        val envelope = PhoneSecretCrypto.encrypt("sk-jdk-round-trip", encodedKey, context)
        val aad = assertNotNull(PhoneSecretCrypto.authenticatedData(envelope.keyId, context))
        val opened = JdkHpkeOpener.open(
            recipient.private,
            point,
            decode(envelope.encapsulatedKey),
            PhoneSecretCrypto.INFO.toByteArray(),
            aad,
            decode(envelope.ciphertext),
        )
        assertContentEquals("sk-jdk-round-trip".toByteArray(), opened)

        val otherCard = assertNotNull(PhoneSecretCrypto.authenticatedData(envelope.keyId, context.copy(messageId = "message-2")))
        assertTrue(
            runCatching {
                JdkHpkeOpener.open(
                    recipient.private,
                    point,
                    decode(envelope.encapsulatedKey),
                    PhoneSecretCrypto.INFO.toByteArray(),
                    otherCard,
                    decode(envelope.ciphertext),
                )
            }.isFailure,
            "the seal is bound to its card",
        )
    }

    @Test
    fun everySealUsesAFreshEphemeralKey() {
        val first = PhoneSecretCrypto.encrypt("same value", publicKey, context)
        val second = PhoneSecretCrypto.encrypt("same value", publicKey, context)
        assertTrue(first.encapsulatedKey != second.encapsulatedKey)
        assertTrue(first.ciphertext != second.ciphertext)
    }

    @Test
    fun theHelpLinkIsOnlyAPlainHttpsAddress() {
        fun help(url: String?) = SecretRequestCardData(helpUrl = url).safeHelpUrl
        assertEquals("https://platform.example.com/keys", help("https://platform.example.com/keys"))
        assertEquals("HTTPS://example.com", help("HTTPS://example.com"))
        assertNull(help(null))
        assertNull(help(""))
        assertNull(help("http://example.com/keys"))
        assertNull(help("https://user:pass@example.com/keys"))
        assertNull(help("javascript:alert(1)"))
        assertNull(help("https:///keys"))
        assertNull(help("https://exa mple.com"))
    }

    private fun encode(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    private fun decode(value: String): ByteArray = Base64.getUrlDecoder().decode(value)

    private fun uncompressed(key: ECPublicKey): ByteArray =
        byteArrayOf(0x04) + fixed(key.w.affineX) + fixed(key.w.affineY)

    private fun fixed(value: BigInteger): ByteArray {
        val bytes = value.toByteArray().dropWhile { it == 0.toByte() }.toByteArray()
        return ByteArray(32 - bytes.size) + bytes
    }

    /** RFC 9180 base mode, DHKEM(P-256, HKDF-SHA256), HKDF-SHA256, AES-256-GCM, from the JDK alone. */
    private object JdkHpkeOpener {
        private val kemSuite = "KEM".toByteArray() + byteArrayOf(0x00, 0x10)
        private val hpkeSuite = "HPKE".toByteArray() + byteArrayOf(0x00, 0x10, 0x00, 0x01, 0x00, 0x02)

        fun open(
            privateKey: PrivateKey,
            recipientPoint: ByteArray,
            enc: ByteArray,
            info: ByteArray,
            aad: ByteArray,
            ciphertext: ByteArray,
        ): ByteArray {
            val agreement = KeyAgreement.getInstance("ECDH")
            agreement.init(privateKey)
            agreement.doPhase(publicKey(enc), true)
            val dh = agreement.generateSecret()
            val eaePrk = extract(kemSuite, ByteArray(0), "eae_prk", dh)
            val sharedSecret = expand(kemSuite, eaePrk, "shared_secret", enc + recipientPoint, 32)
            val pskIdHash = extract(hpkeSuite, ByteArray(0), "psk_id_hash", ByteArray(0))
            val infoHash = extract(hpkeSuite, ByteArray(0), "info_hash", info)
            val keyContext = byteArrayOf(0x00) + pskIdHash + infoHash
            val secret = extract(hpkeSuite, sharedSecret, "secret", ByteArray(0))
            val key = expand(hpkeSuite, secret, "key", keyContext, 32)
            val nonce = expand(hpkeSuite, secret, "base_nonce", keyContext, 12)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
            cipher.updateAAD(aad)
            return cipher.doFinal(ciphertext)
        }

        private fun publicKey(point: ByteArray): java.security.PublicKey {
            val parameters = AlgorithmParameters.getInstance("EC")
            parameters.init(ECGenParameterSpec("secp256r1"))
            val spec = parameters.getParameterSpec(ECParameterSpec::class.java)
            val w = ECPoint(BigInteger(1, point.copyOfRange(1, 33)), BigInteger(1, point.copyOfRange(33, 65)))
            return KeyFactory.getInstance("EC").generatePublic(ECPublicKeySpec(w, spec))
        }

        private fun extract(suite: ByteArray, salt: ByteArray, label: String, ikm: ByteArray): ByteArray =
            hmac(if (salt.isEmpty()) ByteArray(32) else salt, "HPKE-v1".toByteArray() + suite + label.toByteArray() + ikm)

        private fun expand(suite: ByteArray, prk: ByteArray, label: String, info: ByteArray, length: Int): ByteArray {
            val labeled = byteArrayOf((length shr 8).toByte(), length.toByte()) +
                "HPKE-v1".toByteArray() + suite + label.toByteArray() + info
            return hmac(prk, labeled + byteArrayOf(0x01)).copyOf(length)
        }

        private fun hmac(key: ByteArray, data: ByteArray): ByteArray =
            Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key, "HmacSHA256")) }.doFinal(data)
    }
}
