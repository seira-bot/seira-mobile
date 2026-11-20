package com.openmausbot.companion.core

/**
 * Desktop 0.1.181 and earlier know only the OpenMausBot ids. Desktop 0.1.182 sends them and
 * accepts the Seira ids too. The phone reads both and sends the OpenMausBot ids.
 */
object DesktopWireIds {
    val PAIRING_SCHEMES: Set<String> = setOf("openmausbot", "seira")
    val PAIRING_TOKEN_PREFIXES: List<String> = listOf("omb_pair_", "seira_pair_")
    val SERVER_APP_IDS: Set<String> = setOf("openmausbot", "seira")
    val ENVIRONMENT_PATHS: List<String> = listOf(
        "/.well-known/openmausbot/environment",
        "/.well-known/seira/environment",
    )
    val MDNS_SERVICE_TYPES: List<String> = listOf("_openmausbot._tcp", "_seira._tcp")
}
