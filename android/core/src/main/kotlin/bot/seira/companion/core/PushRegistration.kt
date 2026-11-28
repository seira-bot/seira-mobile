package bot.seira.companion.core

import java.security.MessageDigest

/**
 * The last registration each computer accepted, kept as a digest of the push
 * token and the pairing id. A changed token gives a different digest, so the
 * computer is registered again at its next Live stream.
 */
interface PushRegistrationStore {
    suspend fun accepted(connectionId: String): String?
    suspend fun save(connectionId: String, signature: String)
    suspend fun forget(connectionId: String)
    suspend fun forgetAll()
}

class InMemoryPushRegistrationStore : PushRegistrationStore {
    private val signatures = mutableMapOf<String, String>()

    override suspend fun accepted(connectionId: String): String? = synchronized(signatures) { signatures[connectionId] }
    override suspend fun save(connectionId: String, signature: String) {
        synchronized(signatures) { signatures[connectionId] = signature }
    }
    override suspend fun forget(connectionId: String) {
        synchronized(signatures) { signatures.remove(connectionId) }
    }
    override suspend fun forgetAll() = synchronized(signatures) { signatures.clear() }
}

internal object PushSignature {
    fun of(token: String, pairing: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest("$token\n$pairing".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
