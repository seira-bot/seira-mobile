package com.openmausbot.companion.ui

import com.openmausbot.companion.R

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.openmausbot.companion.core.CompanionEndpoint
import com.openmausbot.companion.core.Connection
import com.openmausbot.companion.core.PairingInvite
import com.openmausbot.companion.core.PairingRouteError
import com.openmausbot.companion.core.ServerAddressError
import com.openmausbot.companion.core.ServerPairingRetryError
import com.openmausbot.companion.discovery.DiscoveredService
import com.openmausbot.companion.discovery.DiscoveryState
import com.openmausbot.companion.discovery.toConnection
import com.openmausbot.companion.ui.theme.Seira
import com.openmausbot.companion.ui.theme.SeiraType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Pairing: scan the computer's QR, confirm its identity, and connect — the port
 * of `ios/App/PairingView.swift`.
 *
 * Three ways in, because discovery is allowed to fail. NSD finds the computer by
 * name when the network cooperates; when it does not — a guest network with
 * multicast off, a responder that could not take port 5353 — the address the
 * desktop panel prints is typed instead.
 *
 * Only the first of the three is on screen to begin with. The other two live
 * behind **Other ways to connect**, and opening that is what starts a browse and
 * what asks for the nearby-devices permission a browse needs — the port of
 * `.onChange(of: showingOtherWays)` in `ios/App/PairingView.swift`. This screen
 * used to browse from the moment it existed, so someone who only ever meant to
 * scan the QR code printed on their own computer paid for a local search they
 * never looked at, and met a permission dialog to make it work.
 *
 * A scan never pairs by itself. A scanned or deep-linked invite fills the form
 * and the user confirms the computer's name and address before anything is
 * redeemed (§6).
 */
@Composable
fun PairingScreen(onCancel: () -> Unit) {
    val environment = LocalCompanion.current
    val session = environment.session
    val scope = rememberCoroutineScope()
    val pairingFailedMessage = stringResource(R.string.mobile_pairing_failed)
    val noAddressMessage = stringResource(R.string.mobile_pairing_no_address)
    val invalidAddressMessage = stringResource(R.string.mobile_pairing_invalid_address)
    val invalidQrMessage = stringResource(R.string.mobile_pairing_invalid_qr)
    // `PairingView.swift` fires `Haptics.selection()` on every one of these:
    // scanning, picking a discovered computer, taking a typed address, both
    // submits, and going back to the list.
    val haptics = rememberHaptics()

    val secrets = PairingSecrets

    var manualAddress by rememberSaveable { mutableStateOf("") }
    // Saved instance state holds the computer and a handle, never a secret. A
    // rotation keeps the same process, so PairingSecrets still has the scanned
    // credential and the typed digits; a restore after the system killed the app
    // gets an empty store, and the confirm step asks for a rescan rather than
    // replaying a token that may already have been redeemed (§6).
    var pending by rememberSaveable(stateSaver = PendingPairingSaver) {
        mutableStateOf<PendingPairing?>(null)
    }
    // A pending pairing restored after a process restart carries a handle this
    // store never minted, so writes to it would go nowhere and the next rotation
    // would find the code field empty again. Give a typed pairing a fresh slot
    // before it accepts input; a scanned one stays orphaned, which is the rescan
    // case.
    LaunchedEffect(pending?.handle) {
        val current = pending ?: return@LaunchedEffect
        val rebound = current.rebindingIfOrphaned(secrets)
        if (rebound !== current) pending = rebound
    }
    // Read back out of the process-scoped store, so a rotation does not lose the
    // digits and a process restart does.
    var code by remember(pending?.handle) { mutableStateOf(secrets.code(pending?.handle)) }
    // Presentational only: the spinner and the disabled buttons. Whether an
    // attempt is in flight is the secret store's answer, not this flag's —
    // `PairingSecrets` is the one object that can refuse to replace the slot.
    var pairing by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    var scanError by remember { mutableStateOf<String?>(null) }
    var connectedName by remember { mutableStateOf<String?>(null) }
    var showingScanner by remember { mutableStateOf(false) }
    // "Looking…" forever is not an answer. After a few seconds with nothing
    // found, say the thing that is almost always true.
    var searchedLongEnough by remember { mutableStateOf(false) }
    // Closed to begin with, so the QR path — the one the desktop points at — is
    // the whole screen until somebody asks for more.
    var showingOtherWays by rememberSaveable { mutableStateOf(false) }

    val permissionSnapshot by environment.permissions.snapshot.collectAsState()

    LaunchedEffect(showingOtherWays) {
        searchedLongEnough = false
        if (!showingOtherWays) return@LaunchedEffect
        // Nearby devices, and on API 37 the local network, are asked for here
        // and nowhere else in the app. This is the first moment the app has any
        // use for them, and the person has just said what that use is.
        val missing = environment.permissions.discoveryPermissions()
        if (missing.isNotEmpty()) environment.requestPermissions(missing)
        delay(8_000)
        searchedLongEnough = true
    }

    // The QR code, a deep link and a typed address reach a LAN or Bonjour
    // computer without passing the list above, and on Android 17 dialing one
    // needs the local-network grant — without it the redemption fails as
    // "Couldn't reach this computer". So it is asked for when the confirmation
    // for such a computer opens, the first moment the app will dial it. Hosted
    // and Tailscale computers ask for nothing. Keyed on the computer, not the
    // handle, so a rebind after a process restart does not ask twice.
    LaunchedEffect(pending?.connection) {
        val connection = pending?.connection ?: return@LaunchedEffect
        val missing = environment.permissions.localRoutePermissions(pairingRoutes(connection))
        if (missing.isNotEmpty()) environment.requestPermissions(missing)
    }

    // The credential goes to the process-scoped store; only the handle is ever
    // held by composition state, and the handle is minted with the computer it
    // belongs to, so this screen never has two halves to line up.
    fun openPending(connection: Connection, fromScan: Boolean) {
        val opened = PendingPairing.opening(secrets, connection, fromScan) ?: return
        pending = opened
        code = ""
        failure = null
    }

    val invite by session.pairingInvite.collectAsState()
    // One key, and it is the invite. `takeInvite` refuses while a redemption
    // holds the slot, and what brings the refused invitation back is not a
    // second key on this effect but `endAttemptTaking` in submit's `finally` —
    // releasing the slot is the retry. Opening the confirmation and emptying
    // the queue are one transaction on the other side of the boundary, which is
    // what `accept(_:)` in `ios/App/PairingView.swift` expresses as
    // `guard submission.allowsNavigation`.
    LaunchedEffect(invite) {
        val accepted = invite ?: return@LaunchedEffect
        val next = secrets.takeInvite(pending, accepted, session::consumePairingInvite)
        if (next !== pending) {
            pending = next
            code = ""
            failure = null
        }
    }

    // Session raises pairing problems through actionError; on this screen they
    // belong next to the form rather than in a modal on top of it.
    val actionError by session.actionErrorFlow.collectAsState()
    LaunchedEffect(actionError) {
        val message = actionError ?: return@LaunchedEffect
        failure = message
        session.actionError = null
    }

    // Cold, and collected only while the panel is open: collecting is what
    // starts the browse, and cancelling the collection is what stops it. The key
    // is the panel, so closing it tears the browse down the way `.onDisappear`
    // does on iOS — and entering this screen starts nothing at all.
    val discovery by produceState<DiscoveryState>(DiscoveryState.Idle, showingOtherWays) {
        if (!showingOtherWays) {
            value = DiscoveryState.Idle
            return@produceState
        }
        environment.discovery.discover().collect { value = it }
    }

    fun submit(connection: Connection, credential: String, cameFromScanner: Boolean) {
        val selectedHandle = pending?.handle
        val pairRequestId = secrets.pairRequestId(selectedHandle) ?: return
        // Claims the slot for this redemption. It refuses a second submit, and
        // from here the store will not open a slot for an arriving deep link,
        // so the credential being redeemed cannot be replaced under it.
        if (!secrets.beginAttempt(selectedHandle)) return
        pairing = true
        failure = null
        scope.launch {
            try {
                session.pair(connection, credential, pairRequestId)
                // Every cleanup below names the handle this attempt owns. The
                // slot may already belong to a newer invite, and erasing that
                // one would cost the user a trip back to the computer (§6).
                secrets.clear(selectedHandle)
                connectedName = connection.name
            } catch (error: Throwable) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                failure = session.actionError ?: error.message ?: pairingFailedMessage
                session.actionError = null
                when (pairingFailureDisposition(error, cameFromScanner)) {
                    PairingFailureDisposition.RETAIN_ATTEMPT -> {
                        // Neither the credential nor its request id leaves memory: Retry is the
                        // same idempotent logical request, possibly recovering a lost response.
                    }
                    PairingFailureDisposition.DROP_SCANNED_ATTEMPT -> {
                        if (pending?.handle == selectedHandle) pending = null
                        secrets.clear(selectedHandle)
                    }
                    PairingFailureDisposition.RESET_TYPED_ATTEMPT -> {
                        if (pending?.handle == selectedHandle) code = ""
                        secrets.resetAttempt(selectedHandle)
                    }
                }
            } finally {
                // Releasing the slot is also the retry. An invite that landed in
                // the window before Session marked this attempt was published
                // rather than deferred, and refused above; this is where it is
                // finally taken, in the same step that frees the slot.
                val next = secrets.endAttemptTaking(
                    handle = selectedHandle,
                    current = pending,
                    invite = session.pairingInvite.value,
                    consume = session::consumePairingInvite,
                )
                if (next !== pending) {
                    pending = next
                    code = ""
                    failure = null
                }
                pairing = false
            }
        }
    }

    val torch = remember { ScannerTorch() }
    val cameraAccess by environment.camera.access.collectAsState()
    LaunchedEffect(showingScanner) {
        if (showingScanner) environment.camera.ensure() else scanError = null
    }
    LaunchedEffect(showingScanner, cameraAccess) {
        if (showingScanner && cameraAccess == CameraAccess.DENIED) showingOtherWays = true
    }

    val selected = pending
    val phase = PairDesign.phase(
        connected = connectedName != null,
        pairing = pairing,
        hasComputer = selected != null,
    )
    val scanning = showingScanner && selected == null && connectedName == null
    val errorText = (failure?.let { localizedMobileCopy(it) }) ?: scanError
    val statusText = errorText ?: if (phase == PairPhase.CONNECTED) {
        stringResource(R.string.pair_status_paired_with, connectedName.orEmpty())
    } else {
        stringResource(PairDesign.statusRes(phase))
    }
    val primaryFound = (discovery as? DiscoveryState.Active)
        ?.found
        ?.firstOrNull { it.toConnection() != null }

    fun chooseDiscovered(service: DiscoveredService) {
        haptics.play(HapticCue.SELECT)
        failure = null
        val connection = service.toConnection()
        if (connection == null) {
            failure = noAddressMessage
        } else {
            openPending(connection, fromScan = false)
        }
    }

    PairFrame(
        phase = phase,
        statusText = statusText,
        statusIsError = errorText != null,
        torch = if (scanning) PairDesign.torch(torch.hasFlash, torch.on) else null,
        onTorch = torch::toggle,
        onBack = {
            if (showingScanner) {
                showingScanner = false
            } else {
                onCancel()
            }
        },
        backEnabled = !pairing,
        modifier = Modifier.fillMaxSize(),
        viewfinder = if (scanning) {
            {
                QrScannerView(
                    torch = torch,
                    validate = { payload ->
                        if (PairingInvite.parse(payload) == null) {
                            invalidQrMessage
                        } else {
                            // Session decides whether this invite may be accepted at all
                            // (already paired, credential already burned) and publishes
                            // it as `pairingInvite` for the confirm step above.
                            session.receivePairingURL(payload)
                            showingScanner = false
                            null
                        }
                    },
                    onValidationError = { scanError = it },
                )
            }
        } else {
            null
        },
        card = {
            if (connectedName != null) {
                PairConnectedCard(
                    onDone = onCancel,
                    onPairAnother = {
                        connectedName = null
                        showingScanner = false
                    },
                )
            } else if (selected != null) {
                ConfirmationCard(
                    confirmation = PairingConfirmation.of(selected, secrets),
                    kind = SettingsDesign.kindLabel(pairingRoutes(selected.connection).firstOrNull()?.kind),
                    code = code,
                    onCodeChange = { value ->
                        val normalized = value.uppercase(java.util.Locale.ROOT)
                            .filter { it in 'A'..'Z' || it in '0'..'9' }.take(12)
                        code = normalized
                        secrets.setCode(selected.handle, normalized)
                    },
                    pairing = pairing,
                    onSubmit = { credential ->
                        haptics.play(HapticCue.SELECT)
                        submit(selected.connection, credential, selected.fromScan)
                    },
                    onCancel = {
                        haptics.play(HapticCue.SELECT)
                        pending = null
                        code = ""
                        secrets.clear(selected.handle)
                        failure = null
                    },
                )
            } else {
                val toggleOtherWays = {
                    haptics.play(HapticCue.SELECT)
                    showingOtherWays = !showingOtherWays
                }
                if (primaryFound != null) {
                    PairFoundCard(
                        name = primaryFound.name,
                        detail = stringResource(
                            R.string.pair_found_just_now,
                            stringResource(R.string.pair_kind_same_wifi),
                        ),
                        connecting = false,
                        onConnect = { chooseDiscovered(primaryFound) },
                        onEnterCode = toggleOtherWays,
                    )
                } else {
                    if (scanning) {
                        PairComputerRow(
                            name = stringResource(R.string.pair_waiting),
                            detail = stringResource(R.string.pair_waiting_body),
                        )
                    }
                    if (cameraAccess == CameraAccess.DENIED && showingScanner) {
                        PairPrimaryButton(
                            stringResource(R.string.mobile_open_settings_134635e9),
                            environment.openAppSettings,
                        )
                    } else if (!showingScanner) {
                        PairPrimaryButton(
                            stringResource(R.string.mobile_scan_qr_code_04e3f103),
                            {
                                haptics.play(HapticCue.SELECT)
                                failure = null
                                showingScanner = true
                            },
                        )
                    }
                    PairTextButton(stringResource(R.string.pair_enter_code), toggleOtherWays)
                }
            }
        },
        below = {
            if (selected == null && connectedName == null && showingOtherWays) {
                SetupSection()
                DiscoverySection(
                    discovery = discovery,
                    searchedLongEnough = searchedLongEnough,
                    skip = primaryFound,
                    onChoose = ::chooseDiscovered,
                )
                // Next to the list it makes empty, and only once the search it
                // describes is the thing on screen.
                if (permissionSnapshot.discoveryNeedsRequest) {
                    Text(
                        text = stringResource(R.string.mobile_discovery_permission_hint),
                        style = SeiraType.callout,
                        color = Seira.colors.ink3,
                    )
                }
                ManualSection(
                    address = manualAddress,
                    onAddressChange = { manualAddress = it },
                    onContinue = {
                        haptics.play(HapticCue.SELECT)
                        failure = null
                        // The whole link a server or the desktop printed
                        // (https://host/pair#code=…, openmausbot://pair?…) names
                        // both the address and the credential, so it takes the
                        // scanner's road: Session vets it and the confirmation
                        // opens. The field is emptied because it is saved
                        // instance state, which never holds a credential.
                        val text = manualAddress.trim()
                        val connection = Connection.parse(manualAddress)
                        when {
                            PairingInvite.parse(text) != null -> {
                                manualAddress = ""
                                session.receivePairingURL(text)
                            }
                            connection == null -> failure = invalidAddressMessage
                            else -> openPending(connection, fromScan = false)
                        }
                    },
                )
            }
        },
    )
}

internal enum class PairingFailureDisposition {
    RETAIN_ATTEMPT,
    DROP_SCANNED_ATTEMPT,
    RESET_TYPED_ATTEMPT,
}

/**
 * Route ambiguity, retryable server refusals, and a server that could not be
 * reached keep the same logical request alive. An address that answered and is
 * not a server clears the code like any other refusal, as iOS does: the code was
 * for a different address.
 */
internal fun pairingFailureDisposition(
    error: Throwable,
    cameFromScanner: Boolean,
): PairingFailureDisposition = when {
    error is PairingRouteError || error is ServerPairingRetryError ||
        (error is ServerAddressError && !error.notAServer) ->
        PairingFailureDisposition.RETAIN_ATTEMPT
    cameFromScanner -> PairingFailureDisposition.DROP_SCANNED_ATTEMPT
    else -> PairingFailureDisposition.RESET_TYPED_ATTEMPT
}

/**
 * The routes pairing with [connection] may dial: inside the consent boundary
 * the invite carries — or the one `Session.pair` sets for a typed or discovered
 * computer — exactly the ones `pairFirstReachable` probes
 * ([Connection.pairingEndpoints]). A server pairing dials the first of them.
 */
internal fun pairingRoutes(connection: Connection): List<CompanionEndpoint> {
    val invited = if (connection.allowedRouteKinds == null) {
        connection.establishingRoutePolicyFromInvite()
    } else {
        connection
    }
    return invited.pairingEndpoints
}

@Composable
private fun SetupSection() {
    PairSection(stringResource(R.string.mobile_on_your_computer_9a621537)) {
        Text(stringResource(R.string.mobile_open_openmausbot_settings_phone_38cfc5bb), style = SeiraType.body, color = Seira.colors.ink)
        Text(stringResource(R.string.mobile_choose_set_up_a_phone_8fd474a9), style = SeiraType.body, color = Seira.colors.ink)
    }
}

@Composable
private fun DiscoverySection(
    discovery: DiscoveryState,
    searchedLongEnough: Boolean,
    skip: DiscoveredService?,
    onChoose: (DiscoveredService) -> Unit,
) {
    val colors = Seira.colors
    val active = discovery as? DiscoveryState.Active
    val others = active?.found.orEmpty().filter { it != skip }
    PairSection(stringResource(R.string.mobile_on_this_network_4c9f524a)) {
        val problem = active?.failure
        when {
            problem != null -> Text(localizedMobileCopy(problem), style = SeiraType.callout, color = colors.ink3)

            skip == null && (active == null || active.found.isEmpty()) -> {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = colors.ink2)
                    Text(stringResource(R.string.mobile_looking_5e65f10e), color = colors.ink2, style = SeiraType.body)
                }
                if (searchedLongEnough) {
                    // NSD is multicast: it does not cross subnets, and guest
                    // networks usually block it between clients even within one.
                    // Different Wi-Fi on the two devices is by far the most
                    // common reason this list stays empty.
                    Text(
                        text = stringResource(R.string.mobile_pair_discovery_empty),
                        style = SeiraType.callout,
                        color = colors.ink3,
                    )
                    // The honest answer when a network refuses to cooperate.
                    Text(
                        text = stringResource(R.string.mobile_pair_tailscale_hint),
                        style = SeiraType.callout,
                        color = colors.ink3,
                    )
                }
            }

            else -> Unit
        }

        others.forEach { service ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable(role = Role.Button) { onChoose(service) },
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(service.name, style = SeiraType.bodyMedium, color = colors.ink)
            }
        }
    }
}

@Composable
private fun ManualSection(
    address: String,
    onAddressChange: (String) -> Unit,
    onContinue: () -> Unit,
) {
    PairSection(stringResource(R.string.mobile_or_enter_the_address_f74dcf1c)) {
        OutlinedTextField(
            value = address,
            onValueChange = onAddressChange,
            placeholder = { Text(stringResource(R.string.mobile_https_mac_example_or_192_168_1_42__e277eb2d)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
        PairPrimaryButton(
            text = stringResource(R.string.mobile_continue_2e026239),
            onClick = onContinue,
            enabled = address.isNotBlank(),
        )
        Text(
            text = stringResource(R.string.mobile_pair_manual_address_hint),
            style = SeiraType.callout,
            color = Seira.colors.ink3,
        )
    }
}

@Composable
private fun ConfirmationCard(
    confirmation: PairingConfirmation,
    kind: Int?,
    code: String,
    onCodeChange: (String) -> Unit,
    pairing: Boolean,
    onSubmit: (String) -> Unit,
    onCancel: () -> Unit,
) {
    val colors = Seira.colors
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Name and address sit above the branch, as they do in `PairingView.swift`:
        // the user is confirming which computer at which address, and that is the
        // same question whether the credential came from a QR code or the six
        // digits are about to be typed.
        PairComputerRow(confirmation.name, kind?.let { stringResource(it) })
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.mobile_address_d70f93df), color = colors.ink2, style = SeiraType.body)
            Spacer(Modifier.weight(1f))
            Text(confirmation.address, style = SeiraType.body, fontFamily = FontFamily.Monospace, color = colors.ink)
        }
        if (confirmation.otherAddresses > 0) {
            Text(
                stringResource(R.string.mobile_pairing_other_addresses, confirmation.otherAddresses),
                color = colors.ink3,
                style = SeiraType.callout,
            )
        }
        Text(localizedMobileCopy(confirmation.notice), style = SeiraType.callout, color = colors.ink3)

        when (val step = confirmation.step) {
            is PairingConfirmation.Step.Confirm -> PairPrimaryButton(
                text = stringResource(
                    if (pairing) R.string.pair_status_connecting else R.string.mobile_pair_with_this_computer_8420e8b3,
                ),
                onClick = { onSubmit(step.credential) },
                enabled = !pairing,
            )

            PairingConfirmation.Step.EnterCode -> {
                OutlinedTextField(
                    value = code,
                    onValueChange = onCodeChange,
                    placeholder = { Text(stringResource(R.string.mobile_pairing_code_placeholder)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    textStyle = MaterialTheme.typography.headlineSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                // Twelve symbols that are not a server code almost always hold
                // a 0, O, 1 or I the server never prints. Say so rather than
                // leave Connect greyed out without a reason — the hint
                // `PairingView.swift` shows under the same condition.
                if (code.length >= 12 && !PairingInvite.isPairingCode(code)) {
                    Text(
                        text = stringResource(R.string.mobile_pairing_code_server_symbols_hint),
                        style = SeiraType.callout,
                        color = colors.ink3,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                PairPrimaryButton(
                    text = stringResource(R.string.mobile_connect_b65463cb),
                    onClick = { onSubmit(code) },
                    enabled = PairingInvite.isPairingCode(code) && !pairing,
                )
            }

            // The app was restarted between the scan and the confirmation. The
            // token may already have reached the computer, and §6 is absolute
            // about never sending one twice, so there is nothing to retry here —
            // only the computer's name and address, and the way back.
            PairingConfirmation.Step.Rescan -> Unit
        }

        PairTextButton(
            text = stringResource(R.string.mobile_choose_a_different_computer_9e2a62ed),
            onClick = onCancel,
            enabled = !pairing,
        )
    }
}
