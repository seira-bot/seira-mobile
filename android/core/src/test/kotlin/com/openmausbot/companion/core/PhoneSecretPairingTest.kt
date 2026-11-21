package com.openmausbot.companion.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The computer's sealing key arrives in the pairing QR and stays with the saved pairing. */
class PhoneSecretPairingTest {
    private val publicKey = "BIPBQ12_dWnF1DZLsTZO3Vg0NGjds5-jp9h3jhjr2To7bJelczS0LM82rfXV68PmSJhz2ePosj3fL974XckCpDU"
    private val token = "seira_pair_" + "a".repeat(43)

    private fun invite(secretKey: String?): String =
        "seira://pair?address=macbook.tail1234.ts.net%3A8810&token=$token&code=004209&name=Milind%27s%20Mac" +
            (secretKey?.let { "&secretKey=$it" } ?: "")

    @Test
    fun anInviteKeepsTheComputersSealingKey() {
        val parsed = assertNotNull(PairingInvite.parse(invite(publicKey)))
        assertEquals(publicKey, parsed.connection.secretPublicKey)
        assertNull(parsed.connection.companionDeviceId, "the device id comes from the pairing answer")
        assertTrue(parsed.connection.dialsProtectedRoute)
    }

    @Test
    fun anInviteWithAnInvalidSealingKeyIsRefused() {
        assertNull(PairingInvite.parse(invite("not-a-p256-key")))
        assertNull(PairingInvite.parse(invite(publicKey.dropLast(1))))
        assertNull(PairingInvite.parse(invite("$publicKey%3D")))
    }

    @Test
    fun anOlderInviteHasNoSealingKey() {
        val parsed = assertNotNull(PairingInvite.parse(invite(null)))
        assertNull(parsed.connection.secretPublicKey)
        assertFalse(parsed.connection.copy(companionDeviceId = "device-1").pairedForSecrets)
    }

    @Test
    fun aSavedPairingFromBeforeTheKeyStillDecodes() {
        val saved = CompanionJson.decodeFromString<Connection>(
            """{"id":"saved","name":"Mac","host":"mac.tail1234.ts.net","port":8810}""",
        )
        assertNull(saved.secretPublicKey)
        assertNull(saved.companionDeviceId)
        assertFalse(saved.pairedForSecrets)

        val registry = CompanionJson.decodeFromString<ConnectionRegistry>(
            """{"connections":[{"id":"saved","name":"Mac","host":"mac.tail1234.ts.net","port":8810}],"activeConnectionId":"saved"}""",
        )
        assertNull(registry.activeConnection?.secretPublicKey)
    }

    @Test
    fun theKeyAndDeviceIdSurviveASaveAndRestore() {
        val connection = Connection(
            id = "saved",
            name = "Mac",
            host = "mac.tail1234.ts.net",
            port = 8810,
            secretPublicKey = publicKey,
            companionDeviceId = "device-1",
        )
        val restored = CompanionJson.decodeFromString<Connection>(CompanionJson.encodeToString(Connection.serializer(), connection))
        assertEquals(publicKey, restored.secretPublicKey)
        assertEquals("device-1", restored.companionDeviceId)
        assertTrue(restored.pairedForSecrets)
    }

    @Test
    fun onlyACompanionPairingWithAKeyAndADeviceCanSeal() {
        val paired = Connection(name = "Mac", host = "mac.tail1234.ts.net", port = 8810, secretPublicKey = publicKey, companionDeviceId = "d1")
        assertTrue(paired.pairedForSecrets)
        assertFalse(paired.copy(secretPublicKey = null).pairedForSecrets)
        assertFalse(paired.copy(companionDeviceId = "").pairedForSecrets)
    }

    @Test
    fun aLocalNetworkRouteDoesNotProtectACredential() {
        val lan = Connection(name = "Mac", host = "192.168.1.5", port = 8810, secretPublicKey = publicKey, companionDeviceId = "d1")
        assertFalse(lan.dialsProtectedRoute)
        val tailnet = assertNotNull(CompanionEndpoint.create("http://mac.tail1234.ts.net:8810", CompanionEndpointKind.TAILNET, 0))
        val routed = lan.copy(host = tailnet.host, activeEndpoint = tailnet, endpoints = listOf(tailnet))
            .establishingRoutePolicyFromInvite()
        assertTrue(routed.dialsProtectedRoute)
    }
}
