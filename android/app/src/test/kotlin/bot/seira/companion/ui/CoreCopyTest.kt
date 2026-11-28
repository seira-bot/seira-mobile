package bot.seira.companion.ui

import bot.seira.companion.R
import bot.seira.companion.core.ConnectionAdvice
import bot.seira.companion.core.ConnectionFailure
import bot.seira.companion.core.PairingRouteError
import bot.seira.companion.core.Session
import java.net.ConnectException
import java.net.UnknownHostException
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.junit.Test

class CoreCopyTest {
    private fun ids(message: String) = assertNotNull(coreCopyParts(message), message).map(CopyPart::id)

    @Test
    fun everyConnectionAdviceMessageHasAResource() {
        val host = "192.168.1.42"
        val tryingNext = "mac.example.ts.net"
        for (failure in ConnectionFailure.values().filter { it != ConnectionFailure.CANCELLED }) {
            for (next in listOf(null, tryingNext)) {
                val parts = assertNotNull(coreCopyParts(ConnectionAdvice.message(failure, host, 8810, next)))
                assertEquals(R.string.core_advice_keeps_retrying, parts.last().id)
                if (next != null) assertEquals(CopyPart(R.string.core_advice_trying_next, listOf(next)), parts[parts.size - 2])
            }
        }
        assertEquals(
            listOf(R.string.core_advice_gateway, R.string.core_advice_keeps_retrying),
            ids(ConnectionAdvice.message(502, host)),
        )
    }

    @Test
    fun pairingRouteAdviceIsTranslatedWithTheComputerName() {
        val refused = mapOf("http://192.168.1.42:8810" to ConnectException("refused"))
        val sameNetwork = PairingRouteError(refused.keys.toList(), refused, "Rama's Mac", vpnActive = null)
        assertEquals(
            listOf(CopyPart(R.string.core_route_same_network, listOf("Rama's Mac"))),
            coreCopyParts(sameNetwork.message.orEmpty()),
        )

        val tailnetRoute = "https://rama-mac.tail1234.ts.net:8810"
        val unresolved = mapOf(tailnetRoute to UnknownHostException("unknown"))
        val tailscaleOff = PairingRouteError(listOf(tailnetRoute), unresolved, "Mac", vpnActive = false)
        assertEquals(
            listOf(R.string.core_route_couldnt_reach_lead, R.string.core_route_tailscale_off),
            ids(tailscaleOff.message.orEmpty()),
        )
        val tailscaleOn = PairingRouteError(listOf(tailnetRoute), unresolved, "Mac", vpnActive = true)
        assertEquals(
            listOf(R.string.core_route_tailnet_unreachable),
            ids(tailscaleOn.message.orEmpty()),
        )
    }

    @Test
    fun sessionMessagesAreTranslated() {
        assertEquals(listOf(R.string.core_computer_offline), ids(Session.OFFLINE_MESSAGE))
        assertEquals(listOf(R.string.core_pairing_code_used), ids(Session.SPENT_QR_MESSAGE))
        assertEquals(listOf(R.string.core_pairing_invalid), ids(Session.INVALID_PAIRING_LINK_MESSAGE))
        assertEquals(listOf(R.string.core_thread_gone), ids(Session.THREAD_GONE_MESSAGE))
        assertEquals(listOf(R.string.core_pairing_storage_unavailable), ids(Session.STORAGE_UNAVAILABLE_MESSAGE))
        assertEquals(listOf(R.string.core_route_tailscale_off), ids(ConnectionAdvice.TAILSCALE_OFF))
    }

    @Test
    fun unknownMessagesStayAsTheyAre() {
        assertNull(coreCopyParts("Something nobody wrote a resource for."))
        assertNull(coreCopyParts("Couldn't reach Mac. Something else entirely."))
    }
}
