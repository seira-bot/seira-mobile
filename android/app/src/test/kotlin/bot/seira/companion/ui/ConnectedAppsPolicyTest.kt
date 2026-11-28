package bot.seira.companion.ui

import bot.seira.companion.core.ConnectorCard
import bot.seira.companion.core.ConnectorCatalog
import bot.seira.companion.core.ConnectorStatus
import bot.seira.companion.core.ConnectorStatuses
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConnectedAppsPolicyTest {
    @Test
    fun `unavailable credential store preserves the last authoritative inventory`() {
        val known = ConnectedAppsInventory(
            statuses = mapOf("gmail" to ConnectorStatus(connected = true)),
        )
        val afterFailure = ConnectedAppsPolicy.accept(
            known,
            ConnectorStatuses(
                configured = false,
                credentialStore = "unavailable",
                services = emptyMap(),
            ),
        )

        assertEquals(known.statuses, afterFailure.statuses)
        assertTrue(afterFailure.credentialStoreUnreadable)
        assertFalse(
            ConnectedAppsPolicy.showSetupNotice(
                ConnectorCatalog(configured = false, cards = emptyList()),
                afterFailure.credentialStoreUnreadable,
            ),
            "an unreadable store is ignorance, not evidence that setup is missing",
        )
    }

    @Test
    fun `first unreadable response remains unknown rather than disconnected`() {
        val afterFailure = ConnectedAppsPolicy.accept(
            ConnectedAppsInventory(),
            ConnectorStatuses(
                configured = false,
                credentialStore = "unavailable",
                services = emptyMap(),
            ),
        )

        assertNull(afterFailure.statuses)
        assertTrue(afterFailure.credentialStoreUnreadable)
    }

    @Test
    fun `authoritative refresh replaces stale inventory and clears its warning`() {
        val refreshed = ConnectedAppsPolicy.accept(
            ConnectedAppsInventory(
                statuses = mapOf("gmail" to ConnectorStatus(connected = true)),
                credentialStoreUnreadable = true,
            ),
            ConnectorStatuses(
                configured = true,
                credentialStore = "ok",
                services = mapOf("slack" to ConnectorStatus(connected = true)),
            ),
        )

        assertEquals(setOf("slack"), refreshed.statuses?.keys)
        assertFalse(refreshed.credentialStoreUnreadable)
    }

    @Test
    fun `a wire status is read as words, not as a protocol token`() {
        assertEquals("Active", ConnectedAppsPolicy.statusLabel("ACTIVE"))
        assertEquals("Initiated", ConnectedAppsPolicy.statusLabel("initiated"))
        assertEquals("Not Connected", ConnectedAppsPolicy.statusLabel("not_connected"))
        assertEquals("Expired Token", ConnectedAppsPolicy.statusLabel("EXPIRED_TOKEN"))
        assertEquals("", ConnectedAppsPolicy.statusLabel(""))
    }

    @Test
    fun `search matches label and slug without changing order`() {
        val gmail = ConnectorCard("gmail", "Gmail", "Mail")
        val calendar = ConnectorCard("google-calendar", "Calendar", "Events")
        val slack = ConnectorCard("slack", "Slack", "Messages")

        assertEquals(listOf(calendar), ConnectedAppsPolicy.filterCards(listOf(gmail, calendar, slack), "GOOGLE"))
        assertEquals(listOf(slack), ConnectedAppsPolicy.filterCards(listOf(gmail, calendar, slack), "sla"))
        assertEquals(listOf(gmail, calendar, slack), ConnectedAppsPolicy.filterCards(listOf(gmail, calendar, slack), "  "))
    }

    @Test
    fun `a no-auth toolkit never offers Connect`() {
        val composio = ConnectorCard("composio", "Composio", "Built in", noAuth = true)
        val gmail = ConnectorCard("gmail", "Gmail", "Mail")

        assertFalse(ConnectedAppsPolicy.offersConnect(composio, null))
        assertFalse(ConnectedAppsPolicy.offersConnect(composio, ConnectorStatus(connected = false)))
        assertTrue(ConnectedAppsPolicy.offersConnect(gmail, ConnectorStatus(connected = false)))
        assertFalse(ConnectedAppsPolicy.offersConnect(gmail, ConnectorStatus(connected = true)))
        assertFalse(ConnectedAppsPolicy.offersConnect(gmail, ConnectorStatus(connected = false, pending = true)))
    }

    @Test
    fun `an account alias is trimmed and a blank one is the primary account`() {
        assertEquals("Work", ConnectedAppsPolicy.accountAlias("  Work "))
        assertNull(ConnectedAppsPolicy.accountAlias("   "))
        assertNull(ConnectedAppsPolicy.accountAlias(null))
    }

    @Test
    fun `the catalog wire field noAuth is read, and absent means false`() {
        val cards = bot.seira.companion.core.CompanionJson.decodeFromString<ConnectorCatalog>(
            """{"configured":true,"cards":[{"slug":"composio","label":"Composio","blurb":"b","noAuth":true},{"slug":"gmail","label":"Gmail","blurb":"b"}]}""",
        ).cards

        assertTrue(cards[0].noAuth)
        assertFalse(cards[1].noAuth)
    }
}
