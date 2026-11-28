package bot.seira.companion.core

import kotlinx.serialization.Serializable

/**
 * Backup-safe list of paired computers. Device tokens remain in [TokenStore],
 * keyed by [Connection.id], and therefore never appear in this value.
 */
@Serializable
data class ConnectionRegistry(
    val connections: List<Connection> = emptyList(),
    val activeConnectionId: String? = null,
) {
    val activeConnection: Connection?
        get() = activeConnectionId?.let(::connection)

    fun connection(id: String): Connection? = connections.firstOrNull { it.id == id }

    fun normalized(): ConnectionRegistry {
        val unique = connections.distinctBy(Connection::id)
        val active = activeConnectionId?.takeIf { candidate -> unique.any { it.id == candidate } }
            ?: unique.firstOrNull()?.id
        return ConnectionRegistryUnchecked(unique, active).value
    }

    fun upsert(connection: Connection, makeActive: Boolean = true): ConnectionRegistry {
        val position = connections.indexOfFirst { it.id == connection.id }
        val next = if (position < 0) connections + connection else connections.toMutableList().apply {
            set(position, connection)
        }
        return ConnectionRegistryUnchecked(
            next,
            if (makeActive || activeConnectionId == null) connection.id else activeConnectionId,
        ).value.normalized()
    }

    fun select(id: String): ConnectionRegistry? =
        if (connections.any { it.id == id }) ConnectionRegistryUnchecked(connections, id).value else null

    fun remove(id: String): ConnectionRegistry {
        val next = connections.filterNot { it.id == id }
        val active = if (activeConnectionId == id) next.firstOrNull()?.id else activeConnectionId
        return ConnectionRegistryUnchecked(next, active).value.normalized()
    }

    /**
     * The saved pairing a new pairing replaces: one that shares a real route and carries no
     * identity that differs. Two servers behind one address, or two computers paired only
     * through the relay, stay two computers. A claimed identity alone never matches: a QR or
     * a server can claim any identity.
     */
    fun matchingConnection(candidate: Connection): Connection? {
        val candidateRoutes = candidate.matchableRoutes()
        if (candidateRoutes.isEmpty()) return null
        return connections.firstOrNull { saved ->
            saved.matchableRoutes().any { it in candidateRoutes } && !saved.identityDiffersFrom(candidate)
        }
    }

    companion object {
        /** Decode untrusted persisted data while repairing duplicate/missing selection. */
        fun restoring(connections: List<Connection>, activeConnectionId: String?): ConnectionRegistry {
            val unique = connections.distinctBy(Connection::id)
            val active = activeConnectionId?.takeIf { candidate -> unique.any { it.id == candidate } }
                ?: unique.firstOrNull()?.id
            return ConnectionRegistryUnchecked(unique, active).value
        }
    }
}

/** Result of reading v1 registry data or transparently lifting the old record. */
data class ConnectionRegistryRestore(
    val registry: ConnectionRegistry,
    val migratedLegacyConnection: Boolean,
)

/** RFC 6761 `.invalid` names never resolve: a relay-only QR names one, so it is no address. */
private fun Connection.matchableRoutes(): Set<String> =
    orderedEndpoints.filterNot { it.host.endsWith(".invalid", ignoreCase = true) }.map { it.url }.toSet()

private fun Connection.identityDiffersFrom(other: Connection): Boolean =
    differs(serverEnvironmentId, other.serverEnvironmentId) || differs(secretPublicKey, other.secretPublicKey)

private fun differs(left: String?, right: String?): Boolean = left != null && right != null && left != right

/* Lets the repair paths build a validated registry without duplicating checks. */
private data class ConnectionRegistryUnchecked(
    val connections: List<Connection>,
    val active: String?,
) {
    val value: ConnectionRegistry
        get() = ConnectionRegistry(connections, active)
}
