package com.openmausbot.companion.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.openmausbot.companion.R
import com.openmausbot.companion.core.ConnectionAdvice
import com.openmausbot.companion.core.Session

/** One sentence of translated copy: a string resource and the values for its format arguments. */
internal class CopyPart(@StringRes val id: Int, val args: List<Any> = emptyList()) {
    override fun equals(other: Any?) = other is CopyPart && other.id == id && other.args == args
    override fun hashCode() = 31 * id + args.hashCode()
}

private val exactCoreCopy: Map<String, Int> = mapOf(
    ConnectionAdvice.TAILSCALE_OFF to R.string.core_route_tailscale_off,
    "Android blocked this app from your local network. Allow local network access for it in " +
        "the phone's Settings, then try again." to R.string.core_route_local_network_blocked,
    "Make sure the computer is awake with Seira open, then try again." to R.string.core_route_secure_failed,
    "You're offline." to R.string.core_advice_offline,
    "Lost the connection." to R.string.core_lost_connection,
    "Could not reach the computer." to R.string.core_could_not_reach_computer,
    Session.OFFLINE_MESSAGE to R.string.core_computer_offline,
    "Unlock this phone to reach your computer." to R.string.core_unlock_to_reach,
    "Unlock this phone, then try switching computers again." to R.string.core_unlock_to_switch,
    "This saved connection is no longer available on this phone. Remove it and pair again." to
        R.string.core_saved_connection_unavailable,
    "The server changed while pairing. Scan a new code and try again." to R.string.core_pairing_server_changed,
    Session.SPENT_QR_MESSAGE to R.string.core_pairing_code_used,
    Session.INVALID_PAIRING_LINK_MESSAGE to R.string.core_pairing_invalid,
    "Another pairing attempt is already in progress." to R.string.core_pairing_in_progress,
    Session.STORAGE_UNAVAILABLE_MESSAGE to R.string.core_pairing_storage_unavailable,
    "This address belongs to a different server. Pair again to continue." to R.string.core_different_server,
    "Pair this phone with your computer to open that task." to R.string.core_pair_to_open_task,
    "Pair this phone with your computer to open that thread." to R.string.core_pair_to_open_thread,
    Session.THREAD_GONE_MESSAGE to R.string.core_thread_gone,
    "That agent no longer exists." to R.string.core_agent_gone,
    "Write a message or attach a file first." to R.string.core_write_or_attach,
    "Update Seira on this computer before sending images." to R.string.core_update_for_images,
    "Couldn't send this message. Try again." to R.string.core_cannot_send,
    "The computer sent something this app couldn't read." to R.string.core_unreadable_response,
)

private val patternCoreCopy: List<Pair<Regex, (MatchResult) -> CopyPart>> = listOf(
    Regex("""^This phone can't reach “(.+)”\. Open Tailscale on this phone and sign in with the same """ +
        """account as the computer\. If Private DNS shows a provider name, set it to Off or Automatic """ +
        """\(Settings → Network & internet\)\.$""") to
        { m -> CopyPart(R.string.core_route_tailnet_unreachable, listOf(m.groupValues[1])) },
    Regex("""^Your phone couldn't reach (.+) over Tailscale\. Turn on Tailscale on this phone, signed in """ +
        """to the same tailnet as the computer, then try again\.$""") to
        { m -> CopyPart(R.string.core_route_tailscale_only, listOf(m.groupValues[1])) },
    Regex("""^Your phone couldn't reach (.+) on this network\. Put the phone on the same Wi-Fi as the """ +
        """computer\. If it already is, the computer's firewall may be blocking Seira: on a Windows PC, """ +
        """set its network to Private; on Linux, run the firewall command that Seira shows on the computer\. """ +
        """Or open Settings → Remote access on the computer and sign in so """ +
        """the phone can connect from anywhere\.$""") to
        { m -> CopyPart(R.string.core_route_same_network, listOf(m.groupValues[1])) },
    Regex("""^“(.+)” didn't resolve\. If that's a Tailscale name, this phone may not be on the tailnet\.$""") to
        { m -> CopyPart(R.string.core_advice_host_unresolved, listOf(m.groupValues[1])) },
    Regex("""^Reached your computer, but Phone access isn't answering on port (\d+) — open Seira → """ +
        """Settings → Phone\.$""") to
        { m -> CopyPart(R.string.core_advice_port_closed, listOf(m.groupValues[1].toInt())) },
    Regex("""^No route to your computer at (.+) — different network, or a firewall\.$""") to
        { m -> CopyPart(R.string.core_advice_no_route, listOf(m.groupValues[1])) },
    Regex("""^Could not reach (.+)\.$""") to
        { m -> CopyPart(R.string.core_advice_could_not_reach_host, listOf(m.groupValues[1])) },
    Regex("""^The route through (.+) is temporarily unavailable \(HTTP (\d+)\)\.$""") to
        { m -> CopyPart(R.string.core_advice_gateway, listOf(m.groupValues[1], m.groupValues[2].toInt())) },
)

private const val KEEPS_RETRYING = " The app keeps retrying automatically."
private const val INTERNET_ALSO_FAILED = " The internet address did not answer either. Check that the computer is on."
private val TRYING_NEXT = Regex("""^(.*) Trying (.+) next\.$""", RegexOption.DOT_MATCHES_ALL)
private val COULDNT_REACH_LEAD = Regex("""^Couldn't reach (.+?)\. (.+)$""", RegexOption.DOT_MATCHES_ALL)

private fun body(text: String): CopyPart? {
    exactCoreCopy[text]?.let { return CopyPart(it) }
    for ((pattern, build) in patternCoreCopy) pattern.matchEntire(text)?.let { return build(it) }
    return null
}

/**
 * The sentences that make up a message the connection layer wrote in English, in reading order,
 * or null when the message is not one it knows. A message is a lead-in, a body, and the
 * optional tail the failover adds.
 */
internal fun coreCopyParts(source: String): List<CopyPart>? {
    var rest = source
    val tail = ArrayList<CopyPart>()
    if (rest.endsWith(KEEPS_RETRYING)) {
        rest = rest.removeSuffix(KEEPS_RETRYING)
        tail.add(0, CopyPart(R.string.core_advice_keeps_retrying))
        TRYING_NEXT.matchEntire(rest)?.let { match ->
            rest = match.groupValues[1]
            tail.add(0, CopyPart(R.string.core_advice_trying_next, listOf(match.groupValues[2])))
        }
    }
    if (rest.endsWith(INTERNET_ALSO_FAILED)) {
        rest = rest.removeSuffix(INTERNET_ALSO_FAILED)
        tail.add(0, CopyPart(R.string.core_route_internet_also_failed))
    }
    body(rest)?.let { return listOf(it) + tail }
    val lead = COULDNT_REACH_LEAD.matchEntire(rest) ?: return null
    val main = body(lead.groupValues[2]) ?: return null
    return listOf(CopyPart(R.string.core_route_couldnt_reach_lead, listOf(lead.groupValues[1])), main) + tail
}

/** The localized form of [source], or null when [source] is not connection-layer copy. */
@Composable
internal fun localizedCoreCopy(source: String): String? {
    val parts = coreCopyParts(source) ?: return null
    val sentences = ArrayList<String>(parts.size)
    for (part in parts) sentences.add(stringResource(part.id, *part.args.toTypedArray()))
    return sentences.joinToString(" ")
}
