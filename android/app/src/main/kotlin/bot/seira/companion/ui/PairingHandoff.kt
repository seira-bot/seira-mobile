package bot.seira.companion.ui

import bot.seira.companion.core.DesktopWireIds

/**
 * What counts as a pairing deep link.
 *
 * Recognised in exactly one place — `PairingLinkActivity`, the trampoline that
 * keeps the credential-carrying URL out of any Intent the system remembers.
 */
internal object PairingLink {
    val SCHEMES: Set<String> = DesktopWireIds.PAIRING_SCHEMES
    const val HOST = "pair"

    fun isInvite(scheme: String?, host: String?): Boolean =
        scheme?.lowercase() in SCHEMES && host.equals(HOST, ignoreCase = true)
}

/**
 * The order in which the trampoline does its three jobs, extracted so the order
 * itself can be tested.
 *
 * **The invariant: the Activity is finishing before the credential is handed to
 * anything that outlives it.**
 *
 * `noHistory` and `persistNever` are not enough on their own. Between the moment
 * the URL reaches `Session` — which holds it in a long-lived `StateFlow` the UI
 * reads — and the moment `finish()` marks the record, there is a window in which
 * the process can die while the system still holds a *newly launched,
 * non-finishing* `ActivityRecord`. AOSP preserves such a record and relaunches it
 * with its original Intent, credential and all, which is exactly the redelivery
 * this trampoline exists to prevent. `finish()` is a synchronous call into the
 * system server, so once it returns the record is marked finishing and no
 * relaunch can bring the Intent back.
 *
 * So: relaunch, mark finishing, and only then deliver.
 */
internal object PairingHandoff {

    /**
     * @param inviteUrl the pairing URL, or null when this was not one of ours.
     * @param relaunch starts the sanitized [bot.seira.companion.MainActivity] Intent.
     * @param markFinishing `Activity.finish()` — synchronous, so the record is
     *   marked before it returns.
     * @param deliver hands the URL to the in-memory `Session`. Never called
     *   before [markFinishing].
     */
    fun run(
        inviteUrl: String?,
        relaunch: () -> Unit,
        markFinishing: () -> Unit,
        deliver: (String) -> Unit,
    ) = runThen(relaunch, markFinishing) { if (inviteUrl != null) deliver(inviteUrl) }

    /** The same order for a route that hands over something other than an invite. */
    fun runThen(
        relaunch: () -> Unit,
        markFinishing: () -> Unit,
        after: () -> Unit,
    ) {
        // 1. Hand the user to the real screen.
        relaunch()
        // 2. Mark this record finishing. Everything after this point is safe from
        //    an Intent redelivery, because there is no record left to relaunch.
        markFinishing()
        // 3. Only now does anything enter process memory.
        after()
    }
}
