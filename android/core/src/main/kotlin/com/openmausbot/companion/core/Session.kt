package com.openmausbot.companion.core

import java.net.URI
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient

/**
 * Stream lifecycle, pairing restore, and actions — port of `ios/App/Session.swift`.
 *
 * Pure JVM: storage, device name, notifications, and HTTP are injected so the
 * state machine can be unit-tested with virtual time (`kotlinx-coroutines-test`).
 */
class Session(
    private val scope: CoroutineScope,
    private val connectionStore: ConnectionStore,
    private val tokenStore: TokenStore,
    /**
     * The durable first-pair education marker. Deliberately has no default: the
     * marker is only useful if it outlives the process, and a default would let
     * a wiring slip in `OpenMausApp` silently swap durability for an in-memory
     * boolean that dies with the app — the exact failure the marker exists to
     * prevent, and one no runtime assertion would ever notice.
     */
    private val onboardingStore: OnboardingStore,
    private val deviceNameProvider: () -> String,
    private val notificationSink: NotificationSink = NoOpNotificationSink,
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val clientFactory: (Connection, String?) -> CompanionClient = { connection, token ->
        CompanionClient(connection, token, httpClient)
    },
    private val pairFn: suspend (Connection, String, String, String) -> PairingOutcome =
        { connection, credential, deviceName, pairRequestId ->
            CompanionClient.pairFirstReachable(
                connection = connection,
                credential = credential,
                deviceName = deviceName,
                pairRequestId = pairRequestId,
                client = httpClient,
            )
        },
    /** Test seam: override the SSE Flow without subclassing [CompanionClient]. */
    private val eventsFn: (CompanionClient, String?, Boolean) -> Flow<StreamFrame> = { client, since, screens ->
        client.events(since, screens)
    },
    /** Test seam: override fleet hydrate. */
    private val hydrateFn: suspend (CompanionClient, Int?) -> Fleet = { client, messages ->
        client.fleet(messages)
    },
    /** Test seam: override the engine list read for composer wording. */
    private val instancesFn: suspend (CompanionClient) -> List<Instance> = { client ->
        client.instances()
    },
    /** Test seam: override the authenticated endpoint snapshot. */
    private val metadataFn: suspend (CompanionClient) -> CompanionConnectionMetadata = { client ->
        client.connectionMetadata()
    },
    /** Test seam: the computer's running Live call, read after a fresh hello. */
    private val liveCallFn: suspend (CompanionClient) -> LiveCallState? = { client ->
        client.liveCall()
    },
    /** Test seam for the handoff between a completed transfer and its caller. */
    private val afterAttachmentDownload: suspend () -> Unit = {},
    private val networkMonitor: NetworkMonitor = NetworkMonitor.Unknown,
    /**
     * Picks the route a stream starts on: the first, in stored order, that answers. Null keeps
     * the stored order, which is what a session without a network to probe wants.
     */
    private val routeChoiceFn: (suspend (Connection) -> RouteChoice)? = null,
    /** What each computer accepted for push. Durable in the app, so a restart does not register again. */
    private val pushStore: PushRegistrationStore = InMemoryPushRegistrationStore(),
    /** The clock and the proof of a switch the phone starts. */
    private val workspaceSwitch: WorkspaceSwitchSeams = WorkspaceSwitchSeams(),
) {
    sealed interface Status {
        data object Unpaired : Status
        data object Connecting : Status
        data object Live : Status
        data object Unauthorized : Status
        data class Offline(val message: String) : Status
    }

    sealed interface RestoreState {
        data object Pending : RestoreState
        data object Ready : RestoreState
        data object Unpaired : RestoreState
    }

    private val _state = MutableStateFlow(CompanionState())
    val state: StateFlow<CompanionState> = _state.asStateFlow()

    /**
     * Engines that can take a message INTO a turn that is already running.
     * Loaded once per hydrate and only ever used to word the composer: an
     * empty set means "we do not know", and the wording falls back to the
     * weaker promise, which is the one that is always true.
     */
    private val _steeringInstanceIds = MutableStateFlow<Set<String>>(emptySet())
    val steeringInstanceIds: StateFlow<Set<String>> = _steeringInstanceIds.asStateFlow()

    private val _connection = MutableStateFlow<Connection?>(null)
    val connection: StateFlow<Connection?> = _connection.asStateFlow()

    private val _connections = MutableStateFlow<List<Connection>>(emptyList())
    /** All locally paired computers. Only [connection] has an active runtime. */
    val connections: StateFlow<List<Connection>> = _connections.asStateFlow()

    private val _status = MutableStateFlow<Status>(Status.Unpaired)
    val status: StateFlow<Status> = _status.asStateFlow()

    private val _restoreState = MutableStateFlow<RestoreState>(RestoreState.Pending)
    val restoreState: StateFlow<RestoreState> = _restoreState.asStateFlow()

    private val _routeAdvice = MutableStateFlow<TailscaleAdvice?>(null)
    /** Set while no route of the computer answers and the cause may be Tailscale. */
    val routeAdvice: StateFlow<TailscaleAdvice?> = _routeAdvice.asStateFlow()

    private val _actionError = MutableStateFlow<String?>(null)
    var actionError: String?
        get() = _actionError.value
        set(value) { _actionError.value = value }
    val actionErrorFlow: StateFlow<String?> = _actionError.asStateFlow()

    private val _focusedMessageId = MutableStateFlow<String?>(null)
    val focusedMessageId: StateFlow<String?> = _focusedMessageId.asStateFlow()

    private val _pairingInvite = MutableStateFlow<PairingInvite?>(null)
    val pairingInvite: StateFlow<PairingInvite?> = _pairingInvite.asStateFlow()

    private val _pairingRequested = MutableStateFlow(false)
    /** Pairing may be opened while the current computer stays live. */
    val pairingRequested: StateFlow<Boolean> = _pairingRequested.asStateFlow()

    private var registry = ConnectionRegistry()
    @Volatile
    private var client: CompanionClient? = null

    /** The one switch this phone started and has not finished. Used once, whatever its result. */
    private class SwitchAttempt(val guard: WorkspaceSwitchGuard, val connectionId: String, val switch: PhoneWorkspaceSwitch)
    private var switchAttempt: SwitchAttempt? = null

    /**
     * A browser-live transport on the route the session is already using.
     *
     * Exposed rather than the client itself: the browser screen needs exactly
     * this and nothing else, and handing out the client would let any caller
     * reach every route the companion has.
     */
    fun browserLive(): BrowserLiveTransport? = client?.browserLive()
    private var token: String? = null
    private var rotation = CandidateRotation(emptyList())
    private var streamJob: Job? = null
    private var endpointRefreshJob: Job? = null
    private var streamGeneration = 0

    /**
     * Guards the identity of the paired runtime: [client], the active connection id and [_runtimeEpoch].
     * A request captures all of them in one step ([bound]). A result is checked and written to the state in one
     * step ([commit]). Every point that retires a runtime changes them inside the same lock. The lock is never
     * held across a network call, only across the short capture, check and write.
     */
    private val runtimeLock = Any()

    private val _runtimeEpoch = MutableStateFlow(0)

    /**
     * Counts the paired runtimes this session has served. Every point that drops the old runtime's state
     * (unpair, computer switch, re-pair, workspace switch, restore) moves it on, so a result that comes back later
     * can tell that the runtime it was asked of is gone. Screens watch it to close what they showed for the old one.
     */
    val runtimeEpoch: StateFlow<Int> = _runtimeEpoch.asStateFlow()

    /** The paired runtime a request was sent to: the pairing id and the epoch that pairing had at that time. */
    data class RuntimeBinding(val connectionId: String?, val epoch: Int)

    /** A client together with the runtime it belongs to, captured in one step. */
    private data class Bound(val client: CompanionClient, val binding: RuntimeBinding)

    private fun bound(): Bound? = synchronized(runtimeLock) {
        val active = client ?: return null
        Bound(active, RuntimeBinding(_connection.value?.id, _runtimeEpoch.value))
    }

    /** The runtime the phone is serving now, or null when no computer is paired and ready. */
    fun runtimeBinding(): RuntimeBinding? = bound()?.binding

    /** True while [binding] is the runtime the phone is serving. */
    fun isCurrent(binding: RuntimeBinding): Boolean = synchronized(runtimeLock) { binding.isCurrentLocked() }

    private fun RuntimeBinding.isCurrentLocked(): Boolean =
        connectionId != null && client != null && _connection.value?.id == connectionId && _runtimeEpoch.value == epoch

    private fun RuntimeBinding.stillCurrent(): Boolean = isCurrent(this)

    /** Moves the epoch on. Call only inside [runtimeLock], together with the change that retires the runtime. */
    private fun retireRuntimeLocked() {
        _runtimeEpoch.value += 1
    }

    /** Commit a result to the state only while its runtime is the active one. */
    private fun RuntimeBinding.commit(update: (CompanionState) -> CompanionState): Boolean = synchronized(runtimeLock) {
        if (!isCurrentLocked()) return false
        _state.update(update)
        true
    }

    /** Commit a result and move the focused message in one step, only while its runtime is the active one. */
    private fun RuntimeBinding.commitFocused(messageId: String, update: (CompanionState) -> CompanionState): Boolean =
        synchronized(runtimeLock) {
            if (!isCurrentLocked()) return false
            _state.update(update)
            _focusedMessageId.value = messageId
            true
        }

    /** Show an error only while its runtime is the active one. */
    private fun RuntimeBinding.reportError(message: String?) = synchronized(runtimeLock) {
        if (isCurrentLocked()) _actionError.value = message
    }

    /** Mark the connection unauthorized only while the runtime that was refused is the active one. */
    private fun RuntimeBinding.markUnauthorized() = synchronized(runtimeLock) {
        if (isCurrentLocked()) _status.value = Status.Unauthorized
    }

    private var reconnectDelaySeconds: Long = 0
    /** Resumed streams in a row that closed with nothing after their hello. */
    private var emptyResumes = 0
    private var screenWatchers = 0
    private val gate = Mutex()
    private val pushGate = Mutex()
    @Volatile
    private var pushToken: String? = null
    private val notificationGate = Mutex()
    private val attachmentDownloads = AttachmentDownloadCache(scope)
    private val restored = CompletableDeferred<Unit>()
    /** QR credentials authoritatively rejected or redeemed — never start a new request (§6). */
    private val spentQrCredentials = mutableSetOf<String>()

    /** Sealed credential cards by request, in memory only, so a retry resends the same bytes. */
    private val preparedCredentials = mutableMapOf<String, PreparedPhoneCredential>()
    private val _credentialResetGeneration = MutableStateFlow(0)
    /** Grows when the phone leaves the screen or the computer: a credential card then clears what was typed. */
    val credentialResetGeneration: StateFlow<Int> = _credentialResetGeneration.asStateFlow()
    /** In-flight sends, guarded by itself. A key is here exactly while its state is [CredentialSend.Sending]. */
    private val credentialSendJobs = mutableMapOf<PreparedPhoneCredential, Job>()
    private val _credentialSends = MutableStateFlow<Map<PreparedPhoneCredential, CredentialSend>>(emptyMap())
    /** Where each sealed card's send stands. A send runs in this session, so a card that leaves the screen keeps it. */
    val credentialSends: StateFlow<Map<PreparedPhoneCredential, CredentialSend>> = _credentialSends.asStateFlow()

    /**
     * One pairing attempt at a time, plus the invite that arrived during it.
     *
     * [gate] already serialises the redemption, but it is a suspending Mutex and
     * a deep link lands synchronously on whatever thread delivered the Intent.
     * This monitor is the one both sides can hold, so "an attempt is in flight",
     * "an invite is waiting", and the published invite are read and written as a
     * single decision.
     */
    private val inviteLock = Any()
    private var pairingInFlight = false
    private var deferredInvite: PairingInvite? = null

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    private val networkWatch = scope.launch {
        networkMonitor.changes.debounce(NETWORK_CHANGE_DEBOUNCE_MILLIS).collect {
            gate.withLock { restartStreamLocked() }
        }
    }

    init {
        // No Exception other than cancellation leaves this launch: it is a root
        // coroutine with no caller to catch for it, so [restoreLocked] reports a
        // failed read instead of throwing it. An Error still propagates, on
        // purpose — a fatal one is not this boundary's to swallow.
        scope.launch {
            try {
                restore()
            } finally {
                restored.complete(Unit)
            }
        }
    }

    /** Wait until the launch-time restore attempt has finished (tests / connect). */
    suspend fun awaitRestored() {
        restored.await()
    }

    /** Rebuild the last connection at launch — three outcomes match iOS Keychain restore. */
    private suspend fun restore() = gate.withLock {
        restoreLocked()
    }

    /**
     * Redeem a one-time pairing credential. Persists only the long-lived device
     * token + connection — never the QR credential/code.
     *
     * A paired phone may add another computer. The active runtime is only
     * replaced after the new credential has committed; another [pair] still
     * cannot overlap this redeem+persist critical section.
     *
     * A route failure is retryable with the same in-memory request id because
     * it is either preflight-only or an ambiguous replay of the same logical
     * redemption. An authoritative response, or receiving the durable token,
     * burns a high-entropy QR before any local save. Six-digit codes remain
     * retryable for compatibility with manual pairing.
     *
     * This is also the one active attempt: from the moment it is marked in
     * flight until [settlePairingAttempt], a deep link waits in memory rather
     * than replacing the credential being redeemed.
     */
    suspend fun pair(
        connection: Connection,
        credential: String,
        pairRequestId: String = UUID.randomUUID().toString(),
    ) {
        awaitRestored()
        gate.withLock {
            if (pairingInFlight) throw PairingInProgressException()
            // An unsafe address has not submitted the code. Let the user correct it and retry.
            if (PairingInvite.normalizedServerCode(credential) != null) connection.requireServerTransport()
            if (isQrCredential(credential) && credential in spentQrCredentials) {
                _actionError.value = SPENT_QR_MESSAGE
                clearInviteIfCredential(credential)
                throw SpentPairingCredentialException()
            }

            // From here this is *the* active attempt. A deep link that arrives
            // now waits in memory instead of taking over the credential slot.
            synchronized(inviteLock) { pairingInFlight = true }
            try {
                val qr = isQrCredential(credential)
                val deviceName = deviceNameProvider()
                // A parsed QR already carries this policy. Manual/discovered entry establishes the
                // same boundary here, before pairFirstReachable can probe or redeem on any route.
                val invited = if (connection.allowedRouteKinds == null) {
                    connection.establishingRoutePolicyFromInvite()
                } else {
                    connection
                }
                val (pairedConnection, pairedToken) = try {
                    redeemPairing(invited, credential, deviceName, pairRequestId)
                } catch (error: Throwable) {
                    if (error is kotlinx.coroutines.CancellationException) throw error
                    // Nothing authoritative was heard, or the credential never left the phone:
                    // the same one stays redeemable.
                    val routeFailure = error is PairingRouteError ||
                        error is ServerPairingRetryError ||
                        error is ServerAddressError
                    if (qr && !routeFailure) burnQrCredential(credential)
                    _actionError.value = if (qr && !routeFailure) qrFailureMessage(error) else error.message
                    throw error
                }
                if (qr) burnQrCredential(credential)
                var stored = pairedConnection
                val winner = stored.activeEndpoint
                    ?: CompanionEndpoint.direct(stored.host, stored.port, priority = 10_000)
                registry.matchingConnection(stored)?.let { existing -> stored = stored.copy(id = existing.id) }
                val firstPairing = registry.connections.isEmpty()
                val updatedRegistry = registry.upsert(stored)

                try {
                    tokenStore.save(stored.id, pairedToken)
                    // The education marker goes down before the connection
                    // becomes restorable. A process that stops between the two
                    // leaves an orphan marker, which the router ignores while
                    // unpaired; the reverse order would leave a restorable
                    // pairing that skips first-pair education for good.
                    PairingCommitSequence.persist(
                        markNotificationOnboardingPending = {
                            if (firstPairing) onboardingStore.setNotificationOnboardingPending(true)
                        },
                        saveConnection = { connectionStore.saveRegistry(updatedRegistry) },
                    )
                } catch (error: Throwable) {
                    if (error is kotlinx.coroutines.CancellationException) throw error
                    _actionError.value = if (qr) qrFailureMessage(error) else error.message
                    throw error
                }

                stopActiveRuntimeLocked()
                registry = updatedRegistry
                _connections.value = registry.connections
                token = pairedToken
                // The route that just redeemed leads this session; later launches return to the
                // desktop's security-prioritized typed order.
                rotation = CandidateRotation(liveRoutes(stored, winner))
                val pairedClient = clientFactory(stored, pairedToken)
                synchronized(runtimeLock) {
                    retireRuntimeLocked()
                    _connection.value = stored
                    client = pairedClient
                    _state.value = CompanionState()
                }
                _restoreState.value = RestoreState.Ready
                _pairingRequested.value = false
            } finally {
                settlePairingAttempt()
            }
        }
        connect()
    }

    private suspend fun redeemPairing(
        invited: Connection,
        credential: String,
        deviceName: String,
        requestId: String,
    ): Pair<Connection, String> {
        val serverCode = PairingInvite.normalizedServerCode(credential)
        if (serverCode != null) {
            val outcome = try {
                CompanionClient.pairWithServerFirstReachable(
                    invited, serverCode, deviceName, requestId, httpClient, networkMonitor::vpnActive,
                )
            } catch (error: java.io.IOException) {
                if (error is ServerAddressError) throw error
                if (error is APIError.Status && error.code < 500 && error.code != 429) throw error
                throw ServerPairingRetryError(error)
            }
            check(outcome.response.environment.environmentId == outcome.environment.environmentId) {
                "The server changed while pairing. Scan a new code and try again."
            }
            return outcome.connection.copy(
                name = outcome.response.environment.label.ifEmpty { invited.name },
                serverEnvironmentId = outcome.response.environment.environmentId,
                serverScopes = outcome.response.session.scopes,
                secretPublicKey = null,
                companionDeviceId = null,
            ) to outcome.response.token
        }
        val outcome = pairFn(invited, credential, deviceName, requestId)
        val paired = outcome.response
        val winner = outcome.connection.activeEndpoint
            ?: CompanionEndpoint.direct(outcome.connection.host, outcome.connection.port, priority = 10_000)
        // A desktop QR may have consented to every local address of the computer for this one
        // walk; the device token is bound to the one that answered.
        var stored = outcome.connection.pinningRouteConsent(winner, invited)
        if (paired.serverName.isNotEmpty()) stored = stored.copy(name = paired.serverName)
        stored = stored.applyingPairingAdvertisement(paired.hosts, paired.endpoints)
        stored = winner?.let(stored::promoting) ?: stored.promoting(stored.host)
        stored = stored.copy(companionDeviceId = paired.device.id)
        return stored to paired.token
    }

    suspend fun pair(
        invite: PairingInvite,
        pairRequestId: String = UUID.randomUUID().toString(),
    ) = pair(invite.connection, invite.credential, pairRequestId)

    /**
     * Accept a deep-link invite after restore has settled. A paired phone can
     * add another computer; the current runtime remains intact until that
     * invitation commits successfully.
     */
    fun receivePairingURL(url: String) {
        if (restored.isCompleted) {
            acceptPairingURL(url)
            return
        }
        scope.launch {
            restored.await()
            acceptPairingURL(url)
        }
    }

    fun receivePairingURI(uri: URI) = receivePairingURL(uri.toString())

    /** The screen took the published invite. A waiting one is not touched. */
    fun consumePairingInvite() = synchronized(inviteLock) {
        _pairingInvite.value = null
    }

    fun beginPairing() {
        _pairingRequested.value = true
    }

    fun endPairing() {
        _pairingRequested.value = false
        consumePairingInvite()
    }

    private fun acceptPairingURL(url: String) {
        synchronized(inviteLock) {
            val invite = PairingInvite.parse(url)
            if (invite == null) {
                // A link this app does not know is neither a pairing nor the
                // person's mistake, so it is dropped without a word. A pair link
                // that does not read is someone trying to pair.
                if (PairingInvite.isPairLink(url)) refusePairingLink(INVALID_PAIRING_LINK_MESSAGE)
                return
            }
            if (isQrCredential(invite.credential) && invite.credential in spentQrCredentials) {
                // Still refused. Reopening the same link used to raise the same
                // dialog over whatever screen the phone was on, every time,
                // until the process died; now it lands beside "Scan QR code".
                refusePairingLink(SPENT_QR_MESSAGE)
                return
            }
            // An attempt already in flight owns the screen and the credential
            // slot. This invite waits in memory until that attempt settles;
            // publishing it now would let the finishing attempt erase a
            // credential it never redeemed, and the user would be sent back to
            // the computer for a third QR code (§6).
            if (pairingInFlight) {
                deferredInvite = invite
                return
            }
            _pairingInvite.value = invite
            _pairingRequested.value = true
        }
    }

    /**
     * Refuse a pairing link on the pairing form, where the way forward is,
     * rather than in a modal over whatever screen the phone was on.
     * `PairingScreen` shows [actionError] inline and the root keeps its dialog
     * off that route. The request goes first so the route has moved by the
     * time the message lands.
     */
    private fun refusePairingLink(message: String) {
        _pairingRequested.value = true
        _actionError.value = message
    }

    /**
     * Close the single active attempt and answer for any invite that arrived
     * while it was in flight.
     *
     * Port of the `defer` block of `submit(_:credential:)` in
     * `ios/App/PairingView.swift`: the waiting invite is presented only while
     * pairing is still requested (`pairingRequested`); a successful pairing
     * clears that flag first and therefore consumes the waiting invite, because
     * §6 gives a one-time credential no second life.
     */
    private fun settlePairingAttempt() {
        synchronized(inviteLock) {
            pairingInFlight = false
            val waiting = deferredInvite
            deferredInvite = null
            if (_pairingRequested.value.not()) {
                _pairingInvite.value = null
                return
            }
            val invite = waiting ?: return
            if (isQrCredential(invite.credential) && invite.credential in spentQrCredentials) return
            _pairingInvite.value = invite
        }
    }

    /** Hold the paired-but-unproven state and say why the phone is offline. */
    private fun markRestoreInconclusive(error: Throwable) {
        _restoreState.value = RestoreState.Pending
        _status.value = Status.Offline(storeFailureMessage(error))
    }

    private fun storeFailureMessage(error: Throwable): String =
        error.message?.takeIf { it.isNotBlank() } ?: STORAGE_UNAVAILABLE_MESSAGE

    /**
     * Held under [inviteLock] so adding to the spent set and dropping the invite
     * are one step: an accept that had already read the set must not publish an
     * invite this call is in the middle of burning.
     */
    private fun burnQrCredential(credential: String) = synchronized(inviteLock) {
        spentQrCredentials += credential
        clearInviteIfCredential(credential)
    }

    private fun clearInviteIfCredential(credential: String) = synchronized(inviteLock) {
        val current = _pairingInvite.value
        if (current?.credential == credential) {
            _pairingInvite.value = null
        }
    }

    private fun qrFailureMessage(error: Throwable): String {
        val base = error.message?.takeIf { it.isNotBlank() } ?: "Pairing failed."
        return "$base Start pairing again on your computer and rescan the new QR code."
    }

    fun signOut() {
        attachmentSendIds.clear()
        streamJob?.cancel()
        streamJob = null
        scope.launch {
            gate.withLock { unpairLocked() }
            connect()
        }
    }

    /** Suspending unpair for tests / callers that need completion. */
    suspend fun signOutAndAwait() {
        streamJob?.cancel()
        streamJob = null
        gate.withLock { unpairLocked() }
        connect()
    }

    /**
     * "Pair again" on the revoked screen: unpair the computer that refused this
     * phone and open pairing.
     *
     * A link opened while that screen was up waited behind it — recovery
     * outranks everything in [OnboardingRouter] — and it is what the person came
     * back with. [signOut] empties it with the rest of the queue, which left an
     * empty form, so it is lifted out and put back inside the same [gate]
     * section: no attempt can release or burn an invite in between.
     */
    fun pairAgain() {
        attachmentSendIds.clear()
        streamJob?.cancel()
        streamJob = null
        scope.launch {
            gate.withLock { pairAgainLocked() }
            connect()
        }
    }

    /** Suspending [pairAgain] for tests / callers that need completion. */
    suspend fun pairAgainAndAwait() {
        streamJob?.cancel()
        streamJob = null
        gate.withLock { pairAgainLocked() }
        connect()
    }

    private suspend fun pairAgainLocked() {
        val held = synchronized(inviteLock) { _pairingInvite.value }
        unpairLocked()
        synchronized(inviteLock) {
            if (held != null && !(isQrCredential(held.credential) && held.credential in spentQrCredentials)) {
                _pairingInvite.value = held
            }
            _pairingRequested.value = true
        }
    }

    /** Remove only the selected computer. Other saved computers remain usable. */
    private suspend fun unpairLocked() {
        val id = _connection.value?.id ?: registry.activeConnectionId
        if (id != null) unregisterPush(id)
        val leaving = id?.let { _connection.value?.takeIf { active -> active.id == it } ?: registry.connection(it) }
        val leavingToken = leaving?.takeIf { it.pairedWithServer }?.let { server ->
            token?.takeIf { _connection.value?.id == server.id } ?: storedTokenOrNull(server.id)
        }
        stopActiveRuntimeLocked()
        if (leaving != null && leavingToken != null) revokeServerSession(leaving, leavingToken)
        if (id != null) tokenStore.remove(id)
        registry = id?.let(registry::remove) ?: ConnectionRegistry()
        _connections.value = registry.connections
        connectionStore.saveRegistry(registry)
        if (registry.connections.isEmpty()) {
            // The pairing that earned the education step is gone, so its marker
            // goes too. Removing one of several computers must not consume the
            // first-pair marker for the others.
            onboardingStore.setNotificationOnboardingPending(false)
        }
        _connection.value = null
        _status.value = Status.Unpaired
        _restoreState.value = RestoreState.Unpaired
        restoreSelectedConnectionLocked()
        emptyInviteQueue()
        // Two branches, matching iOS signOut:
        // - Had a computer (id != null): forgetConnection — leave pairingRequested
        //   alone. Pair again does pairAgain() then beginPairing(); a late clear
        //   here would overwrite the new true after the first suspension above.
        // - Nothing to forget (id == null): clearActiveConnection — zero the flag.
        if (id == null) {
            _pairingRequested.value = false
        }
    }

    /** Switches active runtime only after the chosen computer's token is readable. */
    fun switchComputer(id: String) {
        scope.launch { activateComputer(id) }
    }

    /** [switchComputer] for a caller that must wait until the chosen computer is the active one. */
    suspend fun activateComputer(id: String) {
        awaitRestored()
        gate.withLock {
            val saved = registry.connection(id) ?: return@withLock
            if (_connection.value?.id == id) {
                restartStreamLocked()
                return@withLock
            }
            when (val stored = tokenStore.read(id)) {
                is TokenStore.ReadResult.Found -> {
                    stopActiveRuntimeLocked()
                    registry = registry.select(id) ?: registry
                    _connections.value = registry.connections
                    connectionStore.saveRegistry(registry)
                    configureActiveConnectionLocked(saved, stored.token)
                }
                is TokenStore.ReadResult.Unavailable -> {
                    _actionError.value = if (stored.locked) {
                        "Unlock this phone, then try switching computers again."
                    } else {
                        stored.message
                    }
                }
                TokenStore.ReadResult.Missing -> {
                    _actionError.value =
                        "This saved connection is no longer available on this phone. Remove it and pair again."
                }
            }
        }
        connect()
    }

    fun forgetConnection(id: String) {
        scope.launch {
            awaitRestored()
            unregisterPush(id)
            gate.withLock {
                if (registry.connection(id) == null) return@withLock
                val wasActive = registry.activeConnectionId == id
                val leaving = registry.connection(id)
                val leavingToken = leaving?.takeIf { it.pairedWithServer }?.let { storedTokenOrNull(it.id) }
                if (wasActive) stopActiveRuntimeLocked()
                forgetPreparedCredentials { it.connectionId == id }
                if (leaving != null && leavingToken != null) revokeServerSession(leaving, leavingToken)
                tokenStore.remove(id)
                registry = registry.remove(id)
                _connections.value = registry.connections
                connectionStore.saveRegistry(registry)
                if (registry.connections.isEmpty()) onboardingStore.setNotificationOnboardingPending(false)
                if (wasActive) {
                    _connection.value = null
                    _status.value = Status.Unpaired
                    _restoreState.value = RestoreState.Unpaired
                    restoreSelectedConnectionLocked()
                }
            }
            connect()
        }
    }

    private suspend fun storedTokenOrNull(id: String): String? = try {
        (tokenStore.read(id) as? TokenStore.ReadResult.Found)?.token
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        null
    }

    /** Ends the server session in the background. The local removal never waits for it. */
    private fun revokeServerSession(connection: Connection, token: String) {
        if (!connection.pairedWithServer) return
        scope.launch {
            try {
                withTimeoutOrNull(REVOKE_TIMEOUT_MILLIS) { clientFactory(connection, token).logout() }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
            }
        }
    }

    private fun stopActiveRuntimeLocked() {
        forgetPreparedCredentials { true }
        _credentialResetGeneration.update { it + 1 }
        attachmentDownloads.clear()
        streamGeneration += 1
        streamJob?.cancel()
        streamJob = null
        endpointRefreshJob?.cancel()
        endpointRefreshJob = null
        reconnectDelaySeconds = 0
        emptyResumes = 0
        screenWatchers = 0
        token = null
        rotation = CandidateRotation(emptyList())
        synchronized(runtimeLock) {
            retireRuntimeLocked()
            client = null
            _state.value = CompanionState()
        }
        notificationSink.setBadge(0)
    }

    /**
     * Unpairing takes the invite queue with it. An invite that outlived the
     * binding it was meant for comes back on a later pairing screen and
     * redeems a credential the computer retired long ago.
     *
     * There is never a waiting invite left to clear here: [settlePairingAttempt]
     * releases or drops it inside the same [gate] section sign-out is waiting on.
     */
    private fun emptyInviteQueue() = synchronized(inviteLock) {
        _pairingInvite.value = null
    }

    /** True while the stream to the saved computer [connectionId] is Live. */
    fun holdsLiveStream(connectionId: String): Boolean =
        _connection.value?.id == connectionId && _status.value is Status.Live

    /**
     * The push token this phone holds now, or null when it holds none. A new
     * token marks every saved computer for registration; the active one
     * registers at once when its stream is Live, the others at their next one.
     */
    fun setPushToken(token: String?) {
        scope.launch { applyPushToken(token) }
    }

    internal suspend fun applyPushToken(token: String?) {
        val value = token?.takeIf { it.isNotBlank() }
        pushGate.withLock {
            val previous = pushToken
            pushToken = value
            if (previous != null && previous != value) pushStore.forgetAll()
        }
        registerPush()
    }

    private suspend fun registerPush() {
        val value = pushToken ?: return
        val active = client ?: return
        if (_status.value !is Status.Live) return
        val pairing = active.connection.id
        pushGate.withLock {
            val signature = PushSignature.of(value, pairing)
            if (pushStore.accepted(pairing) == signature) return
            try {
                if (active.registerPush(value, pairing).device != null) pushStore.save(pairing, signature)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                // The next Live stream tries again.
            }
        }
    }

    /**
     * Asks a computer to forget this phone before its credential is removed.
     * An unreachable computer cannot hold the removal up for long.
     */
    private suspend fun unregisterPush(id: String) {
        val registered = pushStore.accepted(id) != null
        if (pushToken == null && !registered) return
        val saved = registry.connection(id)
        val caller = when {
            saved == null -> null
            _connection.value?.id == id -> client
            else -> try {
                (tokenStore.read(id) as? TokenStore.ReadResult.Found)?.let { clientFactory(saved, it.token) }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                null
            }
        }
        pushStore.forget(id)
        if (caller == null) return
        // Dispatchers.IO keeps the deadline on the wall clock, not on a test scheduler.
        withContext(Dispatchers.IO) {
            withTimeoutOrNull(PUSH_UNREGISTER_TIMEOUT_MILLIS) {
                try {
                    caller.unregisterPush()
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Exception) {
                    // Gone or unreachable: the removal goes on.
                }
            }
        }
    }

    /** Called when the app comes to the front, and once at launch. */
    fun connect() {
        scope.launch {
            restored.await()
            gate.withLock {
                if (client == null && _restoreState.value is RestoreState.Pending) {
                    restoreLocked()
                }
                if (client == null) return@withLock
                if (streamJob != null) {
                    if (routeChoiceFn != null && _status.value is Status.Offline) restartStreamLocked()
                    return@withLock
                }
                reconnectDelaySeconds = 0
                streamGeneration += 1
                val generation = streamGeneration
                // Publish the handle while still holding `gate`, exactly as
                // restartStreamLocked() does. On a scope that starts children
                // eagerly or on another thread, a stream that fails without ever
                // suspending can reach its `finally` before the launching
                // coroutine's next line: it would clear a streamJob still null
                // and the finished job would then be published behind it, after
                // which connect() — which only tests `streamJob != null` — never
                // reopens the stream. Holding the lock makes that `finally` wait.
                streamJob = launchStream(generation)
            }
        }
    }

    private fun launchStream(generation: Int): Job = scope.launch {
        try {
            chooseReachableRoute(generation)
            runStream(generation)
        } finally {
            gate.withLock {
                if (streamGeneration == generation) {
                    streamJob = null
                }
            }
        }
    }

    /**
     * Start the stream on the first stored route that answers, and remember it for the next
     * launch. When none answers the stream still starts on the stored order, and [routeAdvice]
     * says why when that may be Tailscale.
     */
    private suspend fun chooseReachableRoute(generation: Int) {
        val choose = routeChoiceFn ?: return
        val connection = _connection.value ?: return
        if (connection.orderedEndpoints.size < 2) return
        val choice = try {
            choose(connection)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            return
        }
        val winner = choice.endpoint
        if (winner == null) {
            _routeAdvice.value = ConnectionAdvice.tailscaleAdvice(choice.failures, networkMonitor.vpnActive())
            return
        }
        _routeAdvice.value = null
        gate.withLock {
            if (streamGeneration != generation) return
            val current = _connection.value ?: return
            if (current.id != connection.id) return
            val activeToken = token ?: return
            rotation = CandidateRotation(liveRoutes(current, winner))
            client = clientFactory(current.dialing(winner), activeToken)
            if (current.activeEndpoint?.url != winner.url) persistActiveConnectionLocked(current.promoting(winner))
        }
    }

    /**
     * Rebuild the saved pairing, reporting a failed read instead of throwing it.
     *
     * Cancellation still propagates, and so does anything that is not an
     * `Exception`: an `Error` is left to the handler it was always headed for.
     *
     * Two of the three callers are fire-and-forget launches, where a thrown
     * failure has no one to catch it and reaches the uncaught handler. A read
     * that could not complete is also inconclusive: it did not establish that
     * this phone is unpaired, so the pairing is left standing (`Pending` keeps
     * the session in a protected pending state) and the phone reads offline. That extends the
     * answer `ios/App/Session.swift` already gives for a Keychain it cannot
     * open, and that a locked token already gets here, to a store that throws.
     */
    private suspend fun restoreLocked() {
        try {
            restoreFromStoresLocked()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            markRestoreInconclusive(error)
        }
    }

    private suspend fun restoreFromStoresLocked() {
        val restoredRegistry = connectionStore.loadRegistry()
        registry = restoredRegistry.registry.normalized()
        _connections.value = registry.connections
        if (restoredRegistry.migratedLegacyConnection) {
            // The old record is still intact until a v1 write succeeds. A
            // read-only/transiently failing store must not turn an otherwise
            // usable legacy pairing into an offline state merely because this
            // optional migration could not be committed today.
            try {
                connectionStore.saveRegistry(registry)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                // Keep the in-memory registry; the next launch retries the
                // one-way migration from the untouched legacy record.
            }
        }
        restoreSelectedConnectionLocked()
    }

    /** Restore the selected record, dropping only records whose token is truly missing. */
    private suspend fun restoreSelectedConnectionLocked() {
        while (true) {
            val saved = registry.activeConnection
            if (saved == null) {
                _connection.value = null
                _restoreState.value = RestoreState.Unpaired
                _status.value = Status.Unpaired
                return
            }
            val stored = try {
                tokenStore.read(saved.id)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                TokenStore.ReadResult.Unavailable(locked = false, message = storeFailureMessage(error))
            }
            when (stored) {
                is TokenStore.ReadResult.Unavailable -> {
                    _connection.value = saved
                    _restoreState.value = RestoreState.Pending
                    _status.value = Status.Offline(
                        if (stored.locked) "Unlock this phone to reach your computer." else stored.message,
                    )
                    return
                }
                TokenStore.ReadResult.Missing -> {
                    registry = registry.remove(saved.id)
                    _connections.value = registry.connections
                    connectionStore.saveRegistry(registry)
                }
                is TokenStore.ReadResult.Found -> {
                    configureActiveConnectionLocked(saved, stored.token)
                    return
                }
            }
        }
    }

    private fun configureActiveConnectionLocked(saved: Connection, storedToken: String) {
        token = storedToken
        // The record stays paired even when the client cannot be built from it, so the
        // connection moves first and the old client goes with the old runtime.
        synchronized(runtimeLock) {
            retireRuntimeLocked()
            _connection.value = saved
            client = null
        }
        // A restored pairing walks the desktop's advertised priority, and it walks
        // it credential-safely: a connection already on a protected route never
        // reaches for a local one. Surviving a failover is not a promotion — the
        // computer still decides which protected route leads.
        rotation = CandidateRotation(
            if (saved.pairedWithServer) liveRoutes(saved, saved.activeEndpoint) else saved.orderedEndpoints,
        )
        val first = rotation.currentEndpoint?.let(saved::dialing) ?: saved
        val configuredClient = clientFactory(first, storedToken)
        synchronized(runtimeLock) { client = configuredClient }
        _restoreState.value = RestoreState.Ready
        _status.value = Status.Connecting
    }

    /** Update only the active record; never overwrite the other saved computers. */
    private suspend fun persistActiveConnectionLocked(updated: Connection) {
        registry = registry.upsert(updated, makeActive = false)
        _connections.value = registry.connections
        _connection.value = updated
        connectionStore.saveRegistry(registry)
    }

    /**
     * Pull-to-refresh: reopen the stream and wait until status leaves connecting
     * or 10s — so the spinner means what it appears to mean.
     *
     * Restarts under the session mutex (not a fire-and-forget enqueue) so a
     * caller on any dispatcher observes Connecting before the wait loop runs.
     */
    suspend fun refresh() {
        awaitRestored()
        gate.withLock {
            if (client == null && _restoreState.value is RestoreState.Pending) {
                restoreLocked()
            }
            if (client == null) return@withLock
            streamJob?.cancel()
            streamJob = null
            reconnectDelaySeconds = 0
            streamGeneration += 1
            val generation = streamGeneration
            _status.value = Status.Connecting
            streamJob = launchStream(generation)
        }
        withTimeoutOrNull(10_000) {
            while (_status.value is Status.Connecting && currentCoroutineContext().isActive) {
                delay(120)
            }
        }
    }

    fun watchScreen(ofBotId: String) {
        scope.launch {
            gate.withLock {
                screenWatchers += 1
                if (screenWatchers == 1) restartStreamLocked()
            }
        }
    }

    fun stopWatchingScreen(ofBotId: String) {
        scope.launch {
            gate.withLock {
                screenWatchers = maxOf(0, screenWatchers - 1)
                if (screenWatchers == 0) {
                    _state.update { it.clearScreen(ofBotId) }
                    restartStreamLocked()
                }
            }
        }
    }

    private val _workspaceEpoch = MutableStateFlow(0)

    /** Counts the times the computer moved to another workspace; a screen that shows the old one leaves when it changes. */
    val workspaceEpoch: StateFlow<Int> = _workspaceEpoch.asStateFlow()

    /** The picker reads through this and nothing else: it holds no pin, moves none, and starts no switch. */
    val workspaceSource: WorkspaceSource = object : WorkspaceSource {
        override suspend fun workspaces(pairingId: String): List<PhoneWorkspace>? {
            val active = client?.takeIf { _connection.value?.id == pairingId } ?: throw StalePairing()
            val list = active.workspaces()
            // The pairing may have changed while the read ran: the list of the old computer is dropped.
            if (_connection.value?.id != pairingId || client !== active) throw StalePairing()
            return list
        }

        override suspend fun workspaceChanged(pairingId: String) = reloadAfterWorkspaceChange(pairingId)
    }

    private class StalePairing : Exception("The computer changed while the list was read.")

    /**
     * The computer now serves another workspace. Forget the bots, threads, unread count and prepared sends of the old
     * one, read the new one from the start, and tell the screens through [workspaceEpoch].
     */
    suspend fun reloadAfterWorkspaceChange(pairingId: String) {
        gate.withLock {
            // Only the pairing that saw the change may reload: a stale picker never resets another computer.
            if (client == null || _connection.value?.id != pairingId) return@withLock
            forgetPreparedCredentials { true }
            _credentialResetGeneration.update { it + 1 }
            attachmentDownloads.clear()
            emptyResumes = 0
            _steeringInstanceIds.value = emptySet()
            synchronized(runtimeLock) {
                retireRuntimeLocked()
                _state.value = CompanionState()
            }
            notificationSink.setBadge(0)
            _workspaceEpoch.update { it + 1 }
            streamJob?.cancel()
            reconnectDelaySeconds = 0
            streamGeneration += 1
            val generation = streamGeneration
            _status.value = Status.Connecting
            streamJob = launchStream(generation)
        }
    }

    private fun restartStream() {
        scope.launch {
            gate.withLock { restartStreamLocked() }
        }
    }

    private fun restartStreamLocked() {
        if (streamJob == null) return
        streamJob?.cancel()
        streamJob = null
        if (client == null) return
        reconnectDelaySeconds = 0
        streamGeneration += 1
        val generation = streamGeneration
        // Launch without holding the mutex across runStream — the job handle is
        // published immediately so a concurrent connect() sees it.
        streamJob = launchStream(generation)
    }

    /** Called when the app leaves the screen — deliberate disconnect so the cursor is known. */
    fun disconnect() {
        stopCredentialSends { true }
        _credentialResetGeneration.update { it + 1 }
        streamJob?.cancel()
        streamJob = null
        endpointRefreshJob?.cancel()
        endpointRefreshJob = null
    }

    /** [generation] is the one this stream was launched under; what it starts in the background checks it before writing. */
    private suspend fun runStream(generation: Int) {
        while (currentCoroutineContext().isActive) {
            val activeClient = client ?: return
            _status.value = Status.Connecting
            var receivedHello = false
            var framesAfterHello = 0
            // Resuming from the cursor replays whatever followed it. When that is
            // a frame the route cannot carry (one past the sidecar's event
            // ceiling ends the stream cleanly), every resume is hello and then
            // nothing, forever, until the process dies (MOCA-179). After two of
            // those in a row, start fresh: no cursor, a full reload, a new one.
            val cursor = if (emptyResumes >= EMPTY_RESUMES_BEFORE_FRESH_START) null else _state.value.cursor
            if (cursor == null) emptyResumes = 0
            try {
                activeClient.connection.serverEnvironmentId?.let { expected ->
                    // Fail closed before sending the bearer if the address serves a new workspace.
                    if (activeClient.environment().environmentId != expected) {
                        _status.value = Status.Unauthorized
                        return
                    }
                }
                // Frames arrive in windows (`inBatches`): a busy fleet sends
                // about seventy a second, and one publish per frame kept the
                // home list re-deriving on nearly every drawn frame.
                eventsFn(activeClient, cursor, screenWatchers > 0)
                    .inBatches()
                    .collect { batch ->
                        currentCoroutineContext().ensureActive()

                        val first = batch.first()
                        val hello = first.frame as? Frame.Hello
                        if (hello != null) {
                            receivedHello = true
                            if (!hello.resumed) {
                                hydrate(generation)
                                _state.update { it.resetCursor(hello.cursor) }
                            }
                            _status.value = Status.Live
                            _routeAdvice.value = null
                            promoteWorkingRoute()
                            refreshConnectionMetadata(activeClient)
                            scope.launch { registerPush() }
                        } else {
                            // A frame after hello is the stream working. Only that
                            // resets the backoff, so hello-then-close slows down.
                            reconnectDelaySeconds = 0
                            framesAfterHello += batch.size
                            _state.update { state -> batch.fold(state) { next, frame -> next.apply(frame).advance(frame.seq) } }
                            batch.forEach { frame ->
                                (frame.frame as? Frame.Notify)?.let {
                                    notificationSink.deliver(it.notification, frame.seq)
                                }
                            }
                            notificationSink.setBadge(_state.value.unreadCount)
                        }
                    }
                // A live stream may close normally and should reopen on the working route.
                // An empty/comment-only response never connected: retrying it forever would
                // strand the phone even when another advertised route can reach the computer.
                if (!receivedHello) throw MissingStreamHelloException()
                emptyResumes = if (framesAfterHello == 0) emptyResumes + 1 else 0
                _status.value = Status.Offline("Lost the connection.")
            } catch (error: Throwable) {
                if (!currentCoroutineContext().isActive || error is kotlinx.coroutines.CancellationException) {
                    return
                }
                val apiError = error as? APIError
                if (apiError?.isUnauthorized == true) {
                    _status.value = Status.Unauthorized
                    return
                }
                val message = failureMessage(error)
                _status.value = Status.Offline(_routeAdvice.value?.text ?: message)
            }

            if (!currentCoroutineContext().isActive) return
            reconnectDelaySeconds = if (reconnectDelaySeconds == 0L) 1L else minOf(reconnectDelaySeconds * 2, 15L)
            delay(reconnectDelaySeconds * 1_000)
        }
    }

    private suspend fun hydrate(generation: Int) {
        val activeClient = client ?: return
        val fleet = hydrateFn(activeClient, 50)
        _state.update { it.hydrate(fleet) }
        notificationSink.setBadge(_state.value.unreadCount)
        // Deliberately off hydrate's critical path: this only words the
        // composer, and the cursor commit — and with it the whole stream —
        // must not wait on a request that says nothing about the transcript.
        // An older harness omits the flag and every engine reads as
        // non-steering, which is the conservative wording.
        scope.launch {
            _steeringInstanceIds.value = try {
                instancesFn(activeClient)
                    .filter { it.capabilities?.queueing == true }
                    .map { it.instanceId }
                    .toSet()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                emptySet()
            }
        }
        // The fleet snapshot does not carry the Live call. A phone that
        // reconnects mid-call reads it here; one that reconnects after the
        // computer restarted learns the call is gone the same way. An older
        // harness answers 404 and the state is left alone. Off the critical
        // path, like the engine list above.
        scope.launch {
            val readAt = _state.value.liveCallRevision
            val call = try {
                liveCallFn(activeClient)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                return@launch
            }
            // The stream this read belongs to may be gone by now: another
            // computer, or a restart whose own read (or frame) is newer. A
            // late answer must not overwrite either, nor a `live.call` frame
            // that reached the line while it was out.
            gate.withLock {
                if (streamGeneration == generation) _state.update { it.applyLiveCallLookup(call, readAt) }
            }
        }
    }

    /**
     * Turn a stream failure into advice a person can act on and, when the failure belongs to the
     * route rather than to the pairing, move the dial so the retry that follows tries somewhere
     * new. The trust ratchet in [CandidateRotation] decides what "somewhere new" may be, so the
     * banner only names a route the client was actually rebuilt for — it can no longer promise a
     * switch a policy guard then refuses.
     *
     * A 401 never reaches here: the unauthorized path returns before this is called.
     */
    private suspend fun failureMessage(error: Throwable): String {
        val moved = gate.withLock { advanceRouteLocked(error) }
            ?: return error.message?.takeIf { it.isNotBlank() } ?: "Could not reach the computer."
        ConnectionAdvice.gatewayStatus(error)?.let { status ->
            return ConnectionAdvice.message(status, moved.failedAddress, moved.next)
        }
        val failure = ConnectionAdvice.classify(error)
        return if (failure == ConnectionFailure.OTHER) {
            error.message?.takeIf { it.isNotBlank() }
                ?: ConnectionAdvice.message(failure, moved.failedAddress, moved.failedPort, moved.next)
        } else {
            ConnectionAdvice.message(failure, moved.failedAddress, moved.failedPort, moved.next)
        }
    }

    /**
     * Advance the walk and rebuild the client for the route it landed on, as one
     * step under [gate]. Null when there is no pairing to move.
     *
     * `rotation` and `client` are one fact in two fields — which route the next
     * attempt uses, and the client that will make it. Every other writer of that
     * pair ([updateAddress], [refreshConnectionMetadata], [unpairLocked]) already
     * changes them under [gate], and each of those writers *suspends* on a store
     * in the middle. `Dispatchers.Main.immediate` does not help there: a
     * suspension is exactly where another coroutine on the same thread runs. A
     * failure that advanced the walk in that window used to be overwritten a line
     * later by a refresh that had read the old rotation, leaving `client` dialing
     * one route while `rotation.currentEndpoint` named another — and
     * [promoteWorkingRoute] then persists the one that did not carry the stream.
     */
    private fun advanceRouteLocked(error: Throwable): RouteMove? {
        val connection = _connection.value ?: return null
        val failed = rotation.currentEndpoint
            ?: connection.activeEndpoint
            ?: CompanionEndpoint.direct(connection.host, connection.port, priority = 10_000)
        var next: String? = null
        val candidate = rotation.advanceEndpoint(error)
        val activeToken = token
        if (candidate != null && activeToken != null) {
            client = clientFactory(connection.dialing(candidate), activeToken)
            next = candidate.displayAddress
        }
        return RouteMove(
            failedAddress = failed?.displayAddress ?: connection.host,
            failedPort = failed?.port ?: connection.port,
            next = next,
        )
    }

    /** What [advanceRouteLocked] decided, for the banner to describe. */
    private data class RouteMove(
        val failedAddress: String,
        val failedPort: Int,
        val next: String?,
    )

    /**
     * Persist the route that carried a live stream.
     *
     * A legacy host list promotes the winner for the next launch. A typed list is *not*
     * reordered: the desktop's advertised priority keeps deciding which protected route leads,
     * so a route that merely survived a failover does not outrank a hosted route the computer
     * put first. The one thing recording a protected winner changes is that cleartext routes it
     * superseded stop leading — see [Connection.orderedEndpoints].
     *
     * Under [gate], and that is the point rather than decoration. This used to read `_connection`,
     * write it, and *then* suspend in [ConnectionStore.save] with nothing held. Every other writer
     * of the stored connection — a manual address edit, a metadata refresh, a sign-out — runs
     * inside [gate], so one of them could take the whole section during that suspension and save
     * its own connection first; the promotion's older save then landed on top of it. The measured
     * case is the address edit: the phone came back on the address the user had just replaced,
     * because memory and disk disagreed and the next launch believes the disk.
     *
     * [gate] is safe to hold here: [runStream] is always its own coroutine and never enters this
     * function holding the mutex.
     */
    private suspend fun promoteWorkingRoute() = gate.withLock {
        val winner = rotation.currentEndpoint ?: return@withLock
        val updated = _connection.value ?: return@withLock
        if (updated.activeEndpoint?.url == winner.url) return@withLock
        val promoted = updated.promoting(winner)
        persistActiveConnectionLocked(promoted)
    }

    /**
     * Learn routes the computer enabled after this phone paired. The snapshot is authenticated
     * with the device token already in hand and carries no account or pairing credential, so an
     * already-paired phone can discover hosted HTTPS without another QR code.
     *
     * Failure is deliberately non-fatal: an older sidecar answers 404 and a transient refresh
     * error must not tear down a healthy event stream.
     *
     * The live route is not swapped underneath the stream either, and that is worth being exact
     * about: the replacement order takes effect **on the next launch and on the next route
     * change** — not on every reconnect. [runStream] re-reads `client` each lap but nothing here
     * rebuilds it, so a stream that simply ends and reopens comes back on the same authority it
     * was already using. That is deliberate. The live route is at the head of the walk precisely
     * because the advertised head just failed; re-preferring it after every clean reconnect would
     * pay that failure's timeout again and again, and would move a working session onto another
     * authority for no reason. A route change — a real failure that advances the walk — reads the
     * refreshed list, and a launch reads the persisted order, which is where the new policy lands.
     *
     * And it is the computer's order that lands there. This request is how the desktop restates
     * its transport policy, so nothing local may quietly outrank it; if it could, the refresh
     * would be decorative.
     */
    private fun refreshConnectionMetadata(source: CompanionClient) {
        val connectionId = _connection.value?.id ?: return
        val workingEndpoint = rotation.currentEndpoint ?: source.connection.activeEndpoint
        endpointRefreshJob?.cancel()
        endpointRefreshJob = scope.launch {
            // Best-effort from end to end, and this is a root coroutine: a store that cannot
            // write is as survivable here as a sidecar that answers 404, and neither has a
            // caller left to catch for it.
            try {
                val metadata = metadataFn(source)
                gate.withLock {
                    val current = _connection.value ?: return@withLock
                    // The stream that asked for this snapshot may already have been replaced by
                    // a sign-out, a manual address edit or a route advance. Applying it then
                    // would reorder a walk that no longer belongs to this client.
                    if (current.id != connectionId) return@withLock
                    if (client?.connection?.baseUrl != source.connection.baseUrl) return@withLock
                    val updated = current.reconciling(metadata)
                    persistActiveConnectionLocked(updated)
                    rotation = CandidateRotation(liveRoutes(updated, workingEndpoint))
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                return@launch
            }
        }
    }

    /**
     * [winner] first, then the stored policy order — the walk a live session may take without
     * abandoning the route that is already carrying it.
     *
     * The head here is a transient cursor for one session, not a stored preference: what gets
     * written down is [Connection.orderedEndpoints], where the desktop's priority governs.
     */
    private fun liveRoutes(
        connection: Connection,
        winner: CompanionEndpoint?,
    ): List<CompanionEndpoint> {
        val routes = connection.orderedEndpoints
        return if (winner == null) routes else listOf(winner) + routes.filterNot { it.url == winner.url }
    }

    /** Replace the stored address by hand, keeping the pairing and its token. */
    fun updateAddress(text: String): Boolean {
        val parsed = Connection.parse(text) ?: return false
        val current = _connection.value ?: return false
        val endpoint = parsed.activeEndpoint
            ?: CompanionEndpoint.direct(parsed.host, parsed.port, priority = 0)
            ?: return false
        val updated = current.resettingRoutePolicy(endpoint)
        scope.launch {
            gate.withLock {
                persistActiveConnectionLocked(updated)
                rotation = CandidateRotation(liveRoutes(updated, endpoint))
                val activeToken = token
                if (activeToken != null) {
                    client = clientFactory(updated, activeToken)
                }
                restartStreamLocked()
            }
        }
        return true
    }

    suspend fun updateAddressAndAwait(text: String): Boolean {
        val parsed = Connection.parse(text) ?: return false
        val current = _connection.value ?: return false
        val endpoint = parsed.activeEndpoint
            ?: CompanionEndpoint.direct(parsed.host, parsed.port, priority = 0)
            ?: return false
        val updated = current.resettingRoutePolicy(endpoint)
        gate.withLock {
            persistActiveConnectionLocked(updated)
            rotation = CandidateRotation(liveRoutes(updated, endpoint))
            val activeToken = token
            if (activeToken != null) {
                client = clientFactory(updated, activeToken)
            }
            restartStreamLocked()
        }
        return true
    }

    // MARK: - Workspace switch

    /**
     * Opens [workspaceId] on the computer and moves the pin only as the guard allows.
     *
     * The path is inert until the app gives the session a proof of the paired computer's key
     * ([WorkspaceSwitchSeams.proof]). Without it the first line returns [WorkspaceSwitchResult.Inactive] and no request
     * leaves the phone. This slice is plumbing: it does not guarantee that a bearer reaches the pinned computer. The
     * identity read before a request is only a check at one moment, not authentication of the peer; that needs the
     * proof of seira#540 and seira#546. A pairing with no pin has no trust anchor: nothing starts and the person pairs
     * again. The target identity is read from the authenticated list before anything starts.
     */
    suspend fun switchWorkspace(workspaceId: String): WorkspaceSwitchResult {
        if (workspaceSwitch.proof == null) return WorkspaceSwitchResult.Inactive
        val active = client ?: return WorkspaceSwitchResult.NeedsPairing
        val current = _connection.value ?: return WorkspaceSwitchResult.NeedsPairing
        val pin = current.serverEnvironmentId ?: return WorkspaceSwitchResult.NeedsPairing
        switchAttempt = null
        val listed = active.workspaces() ?: return WorkspaceSwitchResult.NeedsPairing
        val guard = WorkspaceSwitchGuard(pin, workspaceSwitch.now)
        val target = listed.firstOrNull { it.id == workspaceId }?.environmentId
        if (guard.begin(current.id, workspaceId, target) is WorkspaceSwitchStart.NeedsPairing) {
            return WorkspaceSwitchResult.NeedsPairing
        }
        try {
            val response = active.startWorkspaceSwitch(workspaceId)
            val accepted = response.switch
            if (accepted == null || !guard.bind(current.id, accepted.id, accepted.workspaceId, accepted.environmentId)) {
                guard.cancel()
                return WorkspaceSwitchResult.Rejected(WorkspaceSwitchOutcome.Reason.WRONG_OPERATION)
            }
            val attempt = SwitchAttempt(guard, current.id, accepted)
            return when {
                accepted.isEnded -> { guard.cancel(); WorkspaceSwitchResult.Ended(accepted.state) }
                accepted.isDone -> finishSwitch(active, attempt)
                else -> {
                    switchAttempt = attempt
                    WorkspaceSwitchResult.Waiting(response.workspaces)
                }
            }
        } catch (error: Throwable) {
            guard.cancel()
            throw error
        }
    }

    /** Reads the switch that waits once. Call it again later while it answers [WorkspaceSwitchResult.Waiting]. */
    suspend fun finishWorkspaceSwitch(): WorkspaceSwitchResult {
        if (workspaceSwitch.proof == null) return WorkspaceSwitchResult.Inactive
        val active = client ?: return WorkspaceSwitchResult.NeedsPairing
        val attempt = switchAttempt ?: return WorkspaceSwitchResult.Rejected(WorkspaceSwitchOutcome.Reason.NO_PENDING_SWITCH)
        // A pairing change ends the pending switch before any status request leaves the phone.
        if (_connection.value?.id != attempt.connectionId) {
            switchAttempt = null
            attempt.guard.cancel()
            return WorkspaceSwitchResult.Rejected(WorkspaceSwitchOutcome.Reason.PAIRING_CHANGED)
        }
        val status = try {
            active.workspaceSwitch(attempt.switch.id)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // A failed read, including a client that refuses an address that shows another identity, ends the switch.
            switchAttempt = null
            attempt.guard.cancel()
            return WorkspaceSwitchResult.Rejected(WorkspaceSwitchOutcome.Reason.VERIFICATION_FAILED)
        }
        return when {
            status == null -> { switchAttempt = null; attempt.guard.cancel(); WorkspaceSwitchResult.Ended("expired") }
            status.isEnded -> { switchAttempt = null; attempt.guard.cancel(); WorkspaceSwitchResult.Ended(status.state) }
            status.isDone -> finishSwitch(active, SwitchAttempt(attempt.guard, attempt.connectionId, status))
            else -> WorkspaceSwitchResult.Waiting(emptyList())
        }
    }

    private suspend fun finishSwitch(active: CompanionClient, attempt: SwitchAttempt): WorkspaceSwitchResult {
        switchAttempt = null
        val proof = workspaceSwitch.proof ?: return WorkspaceSwitchResult.Inactive
        val completion = try {
            proof(active, attempt.switch)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
        return gate.withLock {
            val current = _connection.value
            val pairing = if (current?.id == attempt.connectionId) attempt.connectionId else "changed"
            when (val outcome = attempt.guard.complete(pairing, completion)) {
                is WorkspaceSwitchOutcome.Rejected -> WorkspaceSwitchResult.Rejected(outcome.reason)
                is WorkspaceSwitchOutcome.Repinned -> {
                    val updated = checkNotNull(current).copy(serverEnvironmentId = outcome.environmentId)
                    persistActiveConnectionLocked(updated)
                    token?.let { client = clientFactory(updated, it) }
                    restartStreamLocked()
                    WorkspaceSwitchResult.Switched(outcome.environmentId)
                }
            }
        }
    }

    // MARK: - Actions

    suspend fun send(text: String, to: Chat) {
        perform {
            val receipt = when (to) {
                is Chat.BotChat -> it.sendToBot(to.bot.id, text, to.threadId)
                is Chat.RoomChat -> it.sendToRoom(to.room.id, text, to.threadId)
            }
            record(receipt, text, to.threadId)
        }
    }

    /**
     * A send the harness held rather than ran has to stay on screen, or the
     * words simply vanish from the phone until the turn settles. [text] is
     * what the person typed; the harness echoes back only an id.
     */
    private fun RuntimeBinding.record(receipt: SendReceipt, text: String, fallbackThreadId: String) {
        val queued = receipt as? SendReceipt.Queued ?: return
        val threadId = queued.threadId.ifEmpty { fallbackThreadId }
        commit { it.rememberQueued(QueuedSend(queued.queueId, text), threadId) }
    }

    /**
     * Take back a held message before its turn settles. The row goes only
     * once the harness confirms, so a failed cancel leaves the words on
     * screen still waiting — which is what is actually true.
     */
    /** True only when the computer confirmed cancellation, so an
     * edit never hands back words that already joined a turn. */
    suspend fun cancelQueued(send: QueuedSend, chat: Chat): Boolean {
        val (activeClient, binding) = bound() ?: return false
        val destination = when (chat) {
            is Chat.BotChat -> MessageDestination.Bot(chat.bot.id, chat.threadId)
            is Chat.RoomChat -> MessageDestination.Room(chat.room.id, chat.threadId)
        }
        try {
            val cancelled = activeClient.cancelQueued(send.queueId, destination)
            if (!binding.commit { it.forgetQueued(send.queueId, chat.threadId) }) return false
            return cancelled
        } catch (error: CancellationException) {
            throw error
        } catch (error: APIError) {
            if (!binding.stillCurrent()) return false
            if (error.isUnauthorized) binding.markUnauthorized()
            binding.reportError(error.message)
            return false
        }
    }

    /**
     * Send a held queued message into the turn that is running, in place of
     * stopping the turn. A steered or vanished entry leaves the queue here;
     * one the turn cannot take stays and is reported as [SteerResult.StillQueued].
     * Null when offline or when the computer refused (the reason is the action error).
     */
    suspend fun steerQueued(send: QueuedSend, chat: Chat): SteerResult? {
        val (activeClient, binding) = bound() ?: return null
        val destination = when (chat) {
            is Chat.BotChat -> MessageDestination.Bot(chat.bot.id, chat.threadId)
            is Chat.RoomChat -> MessageDestination.Room(chat.room.id, chat.threadId)
        }
        try {
            val result = activeClient.steerQueued(send.queueId, destination)
            if (result !is SteerResult.StillQueued) {
                val gone = (result as? SteerResult.Steered)?.queueIds.orEmpty() + send.queueId
                if (!binding.commit { state -> gone.fold(state) { next, id -> next.forgetQueued(id, chat.threadId) } }) return null
            } else if (!binding.stillCurrent()) {
                return null
            }
            return result
        } catch (error: CancellationException) {
            throw error
        } catch (error: APIError) {
            if (!binding.stillCurrent()) return null
            if (error.isUnauthorized) binding.markUnauthorized()
            binding.reportError(error.message)
            return null
        }
    }

    /**
     * Update Claude Code for the engine a failed turn ran on. The answer is
     * for the card that asked, not the app-wide error banner: it shows the
     * version on success and the harness's own words (already written for a
     * person — "stop the running turn first") on failure.
     */
    suspend fun updateClaude(instanceId: String): ClaudeUpdateResult {
        val (activeClient, binding) = bound() ?: return ClaudeUpdateResult.Failed("This computer is offline.")
        return try {
            ClaudeUpdateResult.Updated(activeClient.updateClaude(instanceId))
        } catch (error: CancellationException) {
            throw error
        } catch (error: APIError) {
            if (error.isUnauthorized) binding.markUnauthorized()
            ClaudeUpdateResult.Failed(error.message ?: "Claude Code could not be updated.")
        }
    }

    /**
     * An ambiguous network failure may happen after the server accepted a
     * message. Reusing the id for the exact same retained draft makes Retry
     * idempotent instead of sending the attachment twice.
     */
    private data class AttachmentDraftKey(
        val destination: MessageDestination,
        val text: String,
        val attachmentIds: List<String>,
    )

    private val attachmentSendIds = LinkedHashMap<AttachmentDraftKey, String>()

    /**
     * Send a composer draft with app-owned attachments — the port of
     * `Session.send(text:attachments:to:)`. The destination includes the exact
     * active thread at tap time, so neither a desktop task switch nor an upload
     * delay can move the message elsewhere. Callers only clear their draft when
     * this returns true.
     */
    suspend fun send(text: String, attachments: List<PendingMessageAttachment>, to: Chat): Boolean {
        val (activeClient, binding) = bound() ?: run {
            _actionError.value = "This computer is offline."
            return false
        }
        val connectionId = binding.connectionId
        binding.reportError(null)
        return try {
            AttachmentPolicy.validate(attachments)
            if (text.isBlank() && attachments.isEmpty()) {
                binding.reportError("Write a message or attach a file first.")
                return false
            }
            if (attachments.any { it.kind == PendingMessageAttachment.Kind.IMAGE }) {
                val capable = try {
                    activeClient.imageCapableInstanceIds()
                } catch (error: APIError.Status) {
                    if (error.code != 404) throw error
                    binding.reportError("Update Seira on this computer before sending images.")
                    return false
                }
                if (!imageSupported(to, capable)) {
                    binding.reportError(imageCompatibilityMessage(to))
                    return false
                }
            }
            val destination = when (to) {
                is Chat.BotChat -> MessageDestination.Bot(to.bot.id, to.bot.threadId)
                is Chat.RoomChat -> MessageDestination.Room(to.room.id, to.room.threadId)
            }
            val key = AttachmentDraftKey(destination, text, attachments.map { it.id })
            if (attachmentSendIds.size >= 20 && key !in attachmentSendIds) attachmentSendIds.clear()
            val sendId = attachmentSendIds.getOrPut(key) { UUID.randomUUID().toString() }

            val uploaded = attachments.map { attachment ->
                currentCoroutineContext().ensureActive()
                val mime = AttachmentPolicy.normalizedMime(attachment.mime)
                when (attachment.kind) {
                    PendingMessageAttachment.Kind.IMAGE -> SharedAttachmentReference(
                        path = activeClient.uploadImage(attachment.data, mime, attachment.id),
                        kind = SharedAttachmentKind.IMAGE,
                        displayName = attachment.name,
                    )
                    PendingMessageAttachment.Kind.FILE -> {
                        val file = activeClient.uploadFile(attachment.data, attachment.name, mime, attachment.id)
                        SharedAttachmentReference(file.path, SharedAttachmentKind.FILE, file.name)
                    }
                }
            }
            val message = SharedMessageComposer.compose(
                instruction = text,
                text = emptyList(),
                urls = emptyList(),
                attachments = uploaded,
            )
            val receipt = activeClient.send(message, destination, sendId)
            // The row shows what was typed, not what was sent: `message`
            // carries the <attached-file …> tags the harness reads, and a
            // held message is a person's own words waiting, not transport.
            // An attachment-only send has no words, so it falls back.
            binding.record(receipt, text.trim().ifEmpty { message }, to.threadId)
            attachmentSendIds.remove(key)
            binding.reportError(null)
            true
        } catch (error: CancellationException) {
            throw error
        } catch (error: APIError) {
            if (error.isUnauthorized) {
                binding.markUnauthorized()
            }
            binding.reportError(error.message)
            false
        } catch (error: Throwable) {
            binding.reportError(error.message)
            false
        }
    }

    private fun imageSupported(chat: Chat, capableInstances: Set<String>): Boolean = when (chat) {
        is Chat.BotChat -> chat.bot.modelSelection.instanceId in capableInstances
        is Chat.RoomChat -> chat.room.memberIds.isNotEmpty() && chat.room.memberIds.all { id ->
            val bot = _state.value.bot(id) ?: return@all false
            bot.modelSelection.instanceId in capableInstances
        }
    }

    private fun imageCompatibilityMessage(chat: Chat): String = when (chat) {
        is Chat.BotChat ->
            "${chat.bot.name}'s current model doesn't support images. Choose another model or remove the image."
        is Chat.RoomChat ->
            "Every bot that may answer in this channel must use a model that supports images."
    }

    /**
     * Download one desktop path through its originating transcript message.
     * Where the bytes are kept for viewing is the app's business (it owns a
     * cache directory); this only fetches and reports.
     */
    suspend fun downloadFile(
        threadId: String,
        messageId: String,
        path: String,
        reportError: Boolean = true,
        cacheResult: Boolean = false,
    ): DownloadedFile? {
        val (activeClient, binding) = bound() ?: run {
            if (reportError) _actionError.value = "This computer is offline."
            return null
        }
        val connectionId = binding.connectionId
        if (reportError) binding.reportError(null)
        return try {
            val id = connectionId ?: throw APIError.Transport("This computer is offline.")
            val download = attachmentDownloads.getOrLoad(
                AttachmentDownloadKey(id, threadId, messageId, path),
                retainResult = cacheResult,
            ) {
                activeClient.downloadFile(threadId, messageId, path)
            }
            afterAttachmentDownload()
            currentCoroutineContext().ensureActive()
            // A computer switch invalidates the meaning of every local path.
            // The cache is cleared during the switch, but a transfer that won
            // the completion race must still never surface old-computer bytes.
            if (_connection.value?.id != id) return null
            download
        } catch (error: CancellationException) {
            throw error
        } catch (error: APIError) {
            if (error.isUnauthorized) {
                binding.markUnauthorized()
            }
            if (reportError) binding.reportError(error.message)
            null
        } catch (error: Throwable) {
            if (reportError) binding.reportError(error.message)
            null
        }
    }

    /**
     * Executes one idempotent share operation against the selected computer.
     * A network/gateway failure may advance only through [CandidateRotation],
     * which retains the HTTPS trust ratchet. Callers supply stable upload/send
     * ids, so replaying this lambda cannot create duplicate server objects.
     */
    suspend fun <T> withShareClient(connectionId: String, action: suspend (CompanionClient) -> T): T {
        val first = client ?: throw IllegalStateException("Connect to the selected computer before sharing.")
        if (_connection.value?.id != connectionId) {
            throw IllegalStateException("The selected computer changed. Choose the destination again.")
        }
        try {
            return action(first)
        } catch (error: CancellationException) {
            throw error
        } catch (error: APIError) {
            val retry = gate.withLock {
                if (_connection.value?.id != connectionId) null else {
                    // Only the live computer's 401 may mark this session revoked.
                    if (error.isUnauthorized) _status.value = Status.Unauthorized
                    advanceRouteLocked(error)
                    client
                }
            }
            if (retry != null && retry !== first && _connection.value?.id == connectionId) {
                return action(retry)
            }
            throw error
        } catch (error: Throwable) {
            val retry = gate.withLock {
                if (_connection.value?.id != connectionId) null else {
                    advanceRouteLocked(error)
                    client
                }
            }
            if (retry != null && retry !== first && _connection.value?.id == connectionId) {
                return action(retry)
            }
            throw error
        }
    }

    /**
     * One share/upload against a saved computer without moving the live stream.
     *
     * The active pairing keeps its HTTPS ratchet via [withShareClient]. A share
     * aimed at another saved computer walks only that computer's credential-
     * approved routes and must not mark this session unauthorized: a 401 there
     * is about that pairing, not the one currently on screen.
     */
    suspend fun <T> withPairedShareClient(
        connectionId: String,
        action: suspend (CompanionClient) -> T,
    ): T {
        awaitRestored()
        if (_connection.value?.id == connectionId) {
            return withShareClient(connectionId, action)
        }
        val saved = registry.connection(connectionId)
            ?: throw APIError.Transport("This saved connection is no longer available on this phone. Remove it and pair again.")
        val pairedToken = when (val stored = tokenStore.read(connectionId)) {
            is TokenStore.ReadResult.Found -> stored.token
            is TokenStore.ReadResult.Unavailable -> throw APIError.Transport(
                if (stored.locked) {
                    "Unlock this phone, then try sharing again."
                } else {
                    stored.message
                },
            )
            TokenStore.ReadResult.Missing -> throw APIError.Transport(
                "This saved connection is no longer available on this phone. Remove it and pair again.",
            )
        }
        val endpoints = saved.automaticEndpoints
        if (endpoints.isEmpty()) {
            throw APIError.Transport(
                "Couldn't reach ${saved.name}. Keep Seira open on the computer, then try again.",
            )
        }
        var lastError: Throwable? = null
        for (endpoint in endpoints) {
            val candidate = clientFactory(saved.dialing(endpoint), pairedToken)
            try {
                return action(candidate)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                lastError = error
                if (!canRetryShareOnAnotherRoute(error)) throw error
            }
        }
        throw lastError ?: APIError.Transport(
            "Couldn't reach ${saved.name}. Keep Seira open on the computer, then try again.",
        )
    }

    private fun canRetryShareOnAnotherRoute(error: Throwable): Boolean {
        val api = generateSequence(error) { it.cause }.filterIsInstance<APIError>().firstOrNull()
        return if (api != null) {
            ConnectionAdvice.shouldRetryPairingOnAnotherRoute(api)
        } else {
            ConnectionAdvice.shouldTryAnotherHost(error)
        }
    }

    /**
     * Answers [card] with [choice]. Returns what the harness did with the answer
     * ("answered", "unavailable", …), or null when the server named nothing or
     * the answer did not land (then [actionError] says why).
     */
    suspend fun answer(
        chat: Chat,
        card: OptionCard,
        choice: String,
        rememberingPermission: Boolean = true,
    ): String? {
        val requestId = card.requestId ?: return null
        if (
            rememberingPermission &&
            card.shouldRememberPermission(choice) &&
            chat is Chat.BotChat
        ) {
            alwaysAllow(chat.bot, card)
        }
        return answer(
            threadId = chat.threadId,
            requestId = requestId,
            choice = choice,
            isPermission = card.isPermission,
            reviewedSha256 = card.skillRequest?.reviewedSha256,
            behavior = card.responseBehavior(choice),
        )
    }

    /** Allows [card] for the rest of the provider session. Sends nothing unless the card offers it. */
    suspend fun allowForSession(chat: Chat, card: OptionCard, advancedMode: Boolean): String? {
        val requestId = card.requestId ?: return null
        if (!card.offersSessionAllow(advancedMode)) return null
        var outcome: String? = null
        perform {
            outcome = it.respond(
                threadId = chat.threadId,
                requestId = requestId,
                behavior = "allow",
                always = true,
            )
        }
        return outcome
    }

    suspend fun answer(
        threadId: String,
        requestId: String,
        choice: String,
        isPermission: Boolean,
        reviewedSha256: String? = null,
        behavior: String? = null,
    ): String? {
        var outcome: String? = null
        perform {
            val behavior = behavior ?: OptionCard.responseBehavior(choice, isPermission)
            outcome = it.respond(
                threadId = threadId,
                requestId = requestId,
                behavior = behavior,
                message = choice.takeIf { behavior == "answer" },
                reviewedSha256 = reviewedSha256.takeIf { behavior == "allow" },
            )
        }
        return outcome
    }

    /**
     * A question answered in words, from its card's field or the composer:
     * the same respond route the card's buttons use, but reporting back so
     * the caller can keep the words when they did not land.
     */
    suspend fun answerInWords(chat: Chat, card: OptionCard, answer: String): TypedAnswerResult {
        val (activeClient, binding) = bound() ?: return TypedAnswerResult.Failed("This computer is offline.")
        val requestId = card.requestId ?: return TypedAnswerResult.Gone
        return try {
            val outcome = activeClient.respond(
                threadId = chat.threadId,
                requestId = requestId,
                behavior = "answer",
                message = answer,
            )
            if (outcome == "unavailable") TypedAnswerResult.Gone else TypedAnswerResult.Answered
        } catch (error: CancellationException) {
            throw error
        } catch (error: APIError) {
            if (error.isUnauthorized) {
                binding.markUnauthorized()
            }
            TypedAnswerResult.Failed(error.message ?: "Couldn't send this message. Try again.")
        } catch (error: Throwable) {
            TypedAnswerResult.Failed(error.message ?: "Couldn't send this message. Try again.")
        }
    }

    suspend fun alwaysAllow(bot: Bot, card: OptionCard) {
        val key = card.allowKey ?: return
        perform { it.alwaysAllow(bot.id, key, bot.threadId) }
    }

    suspend fun createBot(spec: NewBot = NewBot()): Bot? {
        val (activeClient, binding) = bound() ?: return null
        return try {
            val bot = activeClient.createBot(spec)
            if (!binding.commit { it.apply(Frame.Bot(bot)) }) return null
            bot
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            binding.reportError(error.message)
            null
        }
    }

    /** True once the computer removed the bot; the bot leaves the list at once. */
    suspend fun deleteBot(bot: Bot): Boolean = mutateTask(false) { client ->
        client.deleteBot(bot.id)
        commit { it.apply(Frame.BotDeleted(bot.id)) }
        true
    }

    suspend fun createRoom(name: String?, memberIds: List<String>): Room? {
        val (activeClient, binding) = bound() ?: return null
        return try {
            val room = activeClient.createRoom(name, memberIds)
            if (!binding.commit { it.apply(Frame.Room(room)) }) return null
            room
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            binding.reportError(error.message)
            null
        }
    }

    /**
     * Create or extend a derived sidebar section in one server transaction.
     * The desktop has no standalone section resource yet, so we merge the
     * returned bot snapshots and the department names, and let SSE reconcile
     * later desktop-side changes. [reportsError] false leaves [actionError]
     * alone, for a caller that words its own failure message.
     */
    suspend fun assignSection(name: String, botIds: List<String>, reportsError: Boolean = true): List<Bot>? {
        val (activeClient, binding) = bound() ?: return null
        return try {
            val assignment = activeClient.assignSection(name, botIds)
            val committed = binding.commit { state ->
                val merged = assignment.bots.fold(state) { current, bot -> current.apply(Frame.Bot(bot)) }
                assignment.sections?.let { merged.withDeclaredSections(it) } ?: merged
            }
            if (committed) assignment.bots else null
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            if (reportsError) binding.reportError(error.message)
            null
        }
    }

    /**
     * Press a button on a widget card. A widget in a room is view-only, so a
     * press there is refused without a request. A refusal carries the computer's
     * own sentence; a transport failure carries nothing and may be retried.
     */
    suspend fun pressWidget(chat: Chat, press: WidgetPress): WidgetPressResult {
        val botChat = chat as? Chat.BotChat ?: return WidgetPressResult.Refused(null)
        val (activeClient, binding) = bound() ?: return WidgetPressResult.Failed
        return try {
            activeClient.sendWidgetAction(botChat.bot.id, botChat.threadId, press)
            WidgetPressResult.Sent
        } catch (error: CancellationException) {
            throw error
        } catch (error: APIError.Status) {
            if (error.isUnauthorized) {
                binding.markUnauthorized()
                WidgetPressResult.Failed
            } else {
                WidgetPressResult.Refused(error.serverMessage)
            }
        } catch (error: Throwable) {
            WidgetPressResult.Failed
        }
    }

    suspend fun interrupt(bot: Bot) {
        perform { it.interrupt(bot.id, bot.threadId) }
    }

    /** Stop the turn running in this conversation: a bot's thread or a room. */
    suspend fun interrupt(chat: Chat) {
        when (chat) {
            is Chat.BotChat -> interrupt(chat.bot)
            is Chat.RoomChat -> perform { it.interruptRoom(chat.room.id, chat.room.threadId) }
        }
    }

    /** The parts of a burst, or null when the computer cannot be asked right now. */
    suspend fun burstView(burstId: String): BurstView? {
        val activeClient = client ?: return null
        return try {
            activeClient.burst(burstId)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            null
        }
    }

    /** Press one button of a burst card. The card shows the failure; nothing is reported twice. */
    suspend fun burstAction(burstId: String, action: BurstAction, edit: BurstEdit? = null): BurstOutcome {
        val (activeClient, binding) = bound() ?: return BurstOutcome.Failed(OFFLINE_MESSAGE)
        return try {
            activeClient.burstAction(burstId, action, edit)
            BurstOutcome.Done
        } catch (error: CancellationException) {
            throw error
        } catch (error: APIError) {
            if (error.isUnauthorized) {
                binding.markUnauthorized()
            }
            BurstOutcome.Failed((error as? APIError.Status)?.serverMessage)
        } catch (_: Throwable) {
            BurstOutcome.Failed(null)
        }
    }

    // ── Live calls ──

    /**
     * Ask the computer for a Live session for this phone's offer. Throws
     * [APIError] so the call bar can show the computer's own words; a 401
     * marks this session revoked the way every other call does. The private
     * `perform {}` helper is deliberately not used here or below: it writes
     * `actionError`, which raises the global "Something went wrong" dialog
     * over a bar that has its own place for errors.
     */
    suspend fun startLiveCall(botId: String, threadId: String, sdp: String): LiveCallStart =
        liveCallRequest { source ->
            val answer = source.startLiveCall(botId, threadId, sdp)
            if (_connection.value?.id != source.connection.id) {
                if (answer is LiveCallStart.Started) runCatching { source.endLiveCall(answer.call.callId) }
                throw APIError.Transport("The computer changed while the call was starting.")
            }
            answer
        }

    /**
     * Hang up on the computer — this phone's own call, or another device's
     * from the remote bar. Quiet: a lost answer changes nothing the phone
     * can act on. The ended call is mirrored into [state] so a bar reading
     * the computer's side sees it at once, but only while that call still
     * reads as running there ([CompanionState.applyLiveCallEnd]).
     *
     * A 404 means the computer no longer runs that call. If the line here
     * still shows it running, a frame was missed: the line is read again
     * rather than guessed at, as the desktop and the iPhone do.
     */
    suspend fun endLiveCall(callId: String): LiveCallState? {
        val binding = runtimeBinding() ?: return null
        val ended = try {
            liveCallRequest { it.endLiveCall(callId) }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            val gone = (error as? APIError.Status)?.code == 404
            if (gone && binding.stillCurrent() && _state.value.showsRunningLiveCall(callId)) {
                liveCall()
            }
            return null
        }
        if (!binding.commit { it.applyLiveCallEnd(callId, ended) }) return null
        return ended
    }

    /**
     * The computer's current Live call, mirrored into [state] unless a
     * `live.call` frame or a hang-up answer reached the line while the read
     * was out ([CompanionState.applyLiveCallLookup]). Null when there is
     * none, or the read failed.
     */
    suspend fun liveCall(): LiveCallState? {
        val binding = runtimeBinding() ?: return null
        val readAt = _state.value.liveCallRevision
        val call = try {
            liveCallRequest { liveCallFn(it) }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            return null
        }
        if (!binding.commit { it.applyLiveCallLookup(call, readAt) }) return null
        return call
    }

    suspend fun liveSettings(): LiveSettings? = configStatus()?.live

    /** Throws [APIError] so the settings sheet can say why a change did not stick. */
    suspend fun updateLiveSettings(patch: LiveSettingsPatch): LiveSettings =
        liveCallRequest { it.updateLiveSettings(patch) }

    private suspend fun <T> liveCallRequest(body: suspend (CompanionClient) -> T): T {
        val (activeClient, binding) = bound() ?: throw APIError.Transport(OFFLINE_MESSAGE)
        return try {
            body(activeClient)
        } catch (error: APIError) {
            // Only the live computer's 401 may mark this session revoked.
            if (error.isUnauthorized) binding.markUnauthorized()
            throw error
        }
    }

    /**
     * Seal [value] for the computer that asked, on the caller's thread, so no coroutine
     * holds the value. A request already sealed returns its kept envelope unchanged.
     *
     * Security invariant: nothing is sealed unless this pairing has the computer's key and
     * the route in use protects credentials.
     */
    fun prepareCredential(value: String, chat: Chat, message: Message): PreparedPhoneCredential {
        val activeClient = client ?: throw APIError.Transport(OFFLINE_MESSAGE)
        val connection = _connection.value ?: throw APIError.Transport(OFFLINE_MESSAGE)
        val secret = message.secret
        val publicKey = connection.secretPublicKey
        val deviceId = connection.companionDeviceId
        val target = secret?.target?.takeIf(String::isNotEmpty)
        val requestKey = secret?.requestKey?.takeIf(String::isNotEmpty)
        if (!connection.pairedForSecrets || publicKey == null || deviceId == null || target == null || requestKey == null) {
            throw PhoneSecretException(PhoneSecretException.Reason.UNAVAILABLE)
        }
        if (!connection.dialsProtectedRoute || !activeClient.connection.dialsProtectedRoute) {
            throw PhoneSecretException(PhoneSecretException.Reason.INSECURE_TRANSPORT)
        }
        if (target in PhoneSecretCrypto.COMPUTER_ONLY_TARGETS) {
            throw PhoneSecretException(PhoneSecretException.Reason.COMPUTER_ONLY)
        }
        val botId = credentialBotId(chat, message)
            ?: throw PhoneSecretException(PhoneSecretException.Reason.INVALID_REQUEST)
        val keyId = PhoneSecretCrypto.publicKeyId(publicKey)
            ?: throw PhoneSecretException(PhoneSecretException.Reason.INVALID_PUBLIC_KEY)
        val context = PhoneSecretRequestContext(deviceId, botId, chat.threadId, message.id, target, requestKey)
        val identity = credentialIdentity(connection.id, keyId, context)
        synchronized(preparedCredentials) { preparedCredentials[identity] }?.let { return it }
        val envelope = PhoneSecretCrypto.encrypt(value, publicKey, context)
        val prepared = PreparedPhoneCredential(identity, connection.id, botId, message.id, envelope)
        return synchronized(preparedCredentials) { preparedCredentials.getOrPut(identity) { prepared } }
    }

    /** The envelope kept for this card on the current computer, if any. */
    fun preparedCredential(chat: Chat, message: Message): PreparedPhoneCredential? {
        val connection = _connection.value ?: return null
        val secret = message.secret ?: return null
        val deviceId = connection.companionDeviceId ?: return null
        val keyId = connection.secretPublicKey?.let(PhoneSecretCrypto::publicKeyId) ?: return null
        val botId = credentialBotId(chat, message) ?: return null
        val context = PhoneSecretRequestContext(
            deviceId,
            botId,
            chat.threadId,
            message.id,
            secret.target ?: return null,
            secret.requestKey ?: return null,
        )
        val identity = credentialIdentity(connection.id, keyId, context)
        return synchronized(preparedCredentials) { preparedCredentials[identity] }
    }

    internal val preparedCredentialCount: Int
        get() = synchronized(preparedCredentials) { preparedCredentials.size }

    fun discardPreparedCredential(prepared: PreparedPhoneCredential) {
        forgetCredentialSends { it === prepared }
        synchronized(preparedCredentials) {
            if (preparedCredentials[prepared.identity] === prepared) preparedCredentials.remove(prepared.identity)
        }
    }

    /**
     * Send [prepared] in this session's scope and publish the outcome in [credentialSends]. A new
     * send of the same envelope replaces the one in flight.
     */
    fun sendCredential(prepared: PreparedPhoneCredential) {
        synchronized(credentialSendJobs) {
            credentialSendJobs.remove(prepared)?.cancel()
            _credentialSends.update { it + (prepared to CredentialSend.Sending) }
            val job = scope.launch(start = CoroutineStart.LAZY) {
                val outcome = try {
                    CredentialSend.Sent(provideCredential(prepared))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    CredentialSend.Failed(error)
                }
                finishCredentialSend(prepared, coroutineContext.job, outcome)
            }
            credentialSendJobs[prepared] = job
            job.start()
        }
    }

    /** Stop the send of [prepared] and drop its error. The envelope stays, so a retry is the same operation. */
    fun stopCredentialSend(prepared: PreparedPhoneCredential) {
        stopCredentialSends { it === prepared }
    }

    private fun finishCredentialSend(prepared: PreparedPhoneCredential, job: Job, outcome: CredentialSend) {
        synchronized(credentialSendJobs) {
            if (credentialSendJobs[prepared] !== job) return
            credentialSendJobs.remove(prepared)
            _credentialSends.update { it + (prepared to outcome) }
        }
    }

    /** Cancels matching sends in flight and drops their errors. A send the computer answered stays. */
    private fun stopCredentialSends(predicate: (PreparedPhoneCredential) -> Boolean) {
        synchronized(credentialSendJobs) {
            val stopped = _credentialSends.value.filter { (prepared, send) -> send !is CredentialSend.Sent && predicate(prepared) }.keys
            if (stopped.isEmpty()) return
            stopped.forEach { credentialSendJobs.remove(it)?.cancel() }
            _credentialSends.update { it - stopped }
        }
    }

    private fun forgetCredentialSends(predicate: (PreparedPhoneCredential) -> Boolean) {
        synchronized(credentialSendJobs) {
            val forgotten = _credentialSends.value.keys.filter(predicate)
            if (forgotten.isEmpty()) return
            forgotten.forEach { credentialSendJobs.remove(it)?.cancel() }
            _credentialSends.update { it - forgotten.toSet() }
        }
    }

    /**
     * Send a sealed card to the computer it was sealed for. Throws [APIError] with the
     * computer's own words, or [PhoneSecretException] when the pairing or route changed.
     */
    suspend fun provideCredential(prepared: PreparedPhoneCredential): CredentialProvided {
        val activeClient = client ?: throw APIError.Transport(OFFLINE_MESSAGE)
        val envelope = prepared.envelope
        val connection = _connection.value?.takeIf {
            it.id == prepared.connectionId &&
                it.pairedForSecrets &&
                it.companionDeviceId == envelope.deviceId &&
                it.secretPublicKey?.let(PhoneSecretCrypto::publicKeyId) == envelope.keyId
        } ?: throw PhoneSecretException(PhoneSecretException.Reason.UNAVAILABLE)
        if (!connection.dialsProtectedRoute || !activeClient.connection.dialsProtectedRoute) {
            throw PhoneSecretException(PhoneSecretException.Reason.INSECURE_TRANSPORT)
        }
        return try {
            activeClient.provideCredential(prepared.botId, prepared.messageId, envelope)
        } catch (error: APIError) {
            if (error.isUnauthorized) {
                gate.withLock {
                    if (_connection.value?.id == prepared.connectionId) _status.value = Status.Unauthorized
                }
            }
            throw error
        }
    }

    private fun credentialBotId(chat: Chat, message: Message): String? = when (chat) {
        is Chat.BotChat -> chat.bot.id
        is Chat.RoomChat -> message.from?.botId
    }

    private fun credentialIdentity(connectionId: String, keyId: String, context: PhoneSecretRequestContext): String =
        listOf(
            connectionId,
            keyId,
            context.botId,
            context.threadId,
            context.messageId,
            context.target,
            context.requestKey,
        ).joinToString("\u0000")

    private fun forgetPreparedCredentials(predicate: (PreparedPhoneCredential) -> Boolean) {
        forgetCredentialSends(predicate)
        synchronized(preparedCredentials) { preparedCredentials.values.removeAll(predicate) }
    }

    suspend fun markRead(chat: Chat) {
        perform(quietly = true) {
            when (chat) {
                is Chat.BotChat -> it.markBotRead(chat.bot.id, chat.threadId)
                is Chat.RoomChat -> it.markRoomRead(chat.room.id, chat.threadId)
            }
        }
    }

    suspend fun loadThread(threadId: String) {
        val (activeClient, binding) = bound() ?: return
        try {
            val page = activeClient.messages(threadId, limit = 50)
            binding.commit { it.merge(page, threadId) }
        } catch (error: Throwable) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            binding.reportError(error.message)
        }
    }

    suspend fun loadThreadIfNeeded(threadId: String) {
        if (!_state.value.hasLoadedPage(threadId)) loadThread(threadId)
    }

    suspend fun loadOlder(threadId: String) {
        val (activeClient, binding) = bound() ?: return
        val oldest = _state.value.transcript(threadId).firstOrNull() ?: return
        try {
            val page = activeClient.messages(threadId, before = oldest.id, limit = 50)
            binding.commit { it.prepend(page, threadId) }
        } catch (error: Throwable) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            binding.reportError(error.message)
        }
    }

    suspend fun image(threadId: String, messageId: String): ByteArray? =
        try {
            client?.image(threadId, messageId)
        } catch (_: Throwable) {
            null
        }

    suspend fun search(query: String): List<SearchHit> {
        val trimmed = query.trim()
        if (trimmed.length < 2) return emptyList()
        val (activeClient, binding) = bound() ?: return emptyList()
        return try {
            activeClient.search(trimmed).takeIf { binding.stillCurrent() } ?: emptyList()
        } catch (error: Throwable) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            binding.reportError(error.message)
            emptyList()
        }
    }

    suspend fun open(hit: SearchHit): Chat? {
        val (activeClient, binding) = bound() ?: return null
        return try {
            val botId = hit.botId
            if (botId != null) {
                if (_state.value.bot(botId)?.forTask(hit.threadId) == null && !refreshNavigationState(activeClient, binding)) return null
                val bot = _state.value.bot(botId)?.forTask(hit.threadId)
                    ?: throw APIError.Status(404, THREAD_GONE_MESSAGE)
                if (!hit.onActivePath) {
                    val leaf = activeClient.setActiveBranch(bot.id, hit.messageId, hit.threadId)
                    if (!binding.commit { it.apply(Frame.Thread(hit.threadId, leaf)) }) return null
                }
                val page = activeClient.messagesAround(hit.threadId, hit.messageId)
                if (!binding.commitFocused(hit.messageId) { it.merge(page, hit.threadId) }) return null
                return _state.value.chat(ChatTarget.Bot(bot.id, hit.threadId))
            }
            val groupId = hit.groupId
            if (groupId != null) {
                var room = _state.value.rooms.firstOrNull { it.id == groupId } ?: return null
                if (room.threadId != hit.threadId) {
                    room = activeClient.switchRoomTask(room.id, hit.threadId)
                    if (!binding.commit { it.apply(Frame.Room(room)) }) return null
                }
                val page = activeClient.messagesAround(hit.threadId, hit.messageId)
                if (!binding.commitFocused(hit.messageId) { it.merge(page, hit.threadId) }) return null
                return _state.value.chat(ChatTarget.Room(groupId, hit.threadId))
            }
            null
        } catch (error: Throwable) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            binding.reportError(error.message)
            null
        }
    }

    suspend fun openNotification(target: NotificationTarget): Chat? {
        awaitRestored()
        return notificationGate.withLock {
            val (activeClient, binding) = bound() ?: run {
                _actionError.value = "Pair this phone with your computer to open that task."
                return@withLock null
            }

            try {
                _state.value.roomOwningTask(target.threadId)?.let { room ->
                    return@withLock openRoomNotification(activeClient, binding, room, target.threadId)
                }

                if (_state.value.bot(target.botId)?.forTask(target.threadId) == null) {
                    if (!refreshNavigationState(activeClient, binding)) return@withLock null
                    _state.value.roomOwningTask(target.threadId)?.let { room ->
                        return@withLock openRoomNotification(activeClient, binding, room, target.threadId)
                    }
                }

                val selected = _state.value.bot(target.botId)
                    ?: throw APIError.Status(404, "That agent no longer exists.")
                Chat.BotChat(selected.forTask(target.threadId)
                    ?: throw APIError.Status(404, THREAD_GONE_MESSAGE))
            } catch (error: Throwable) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                if (!binding.stillCurrent()) return@withLock null
                binding.reportError(error.message)
                null
            }
        }
    }

    /** Refresh only navigation metadata; keep already loaded scrollback and live tails. */
    private suspend fun refreshNavigationState(activeClient: CompanionClient, binding: RuntimeBinding): Boolean {
        val before = _state.value
        val fleet = hydrateFn(activeClient, 50)
        currentCoroutineContext().ensureActive()
        return gate.withLock {
            // A newer live frame wins over this HTTP snapshot, including when
            // a fresh connection has not established an SSE cursor yet.
            val committed = binding.commit { current ->
                if (current !== before) {
                    current
                } else {
                    current.copy(bots = fleet.bots, rooms = fleet.groups)
                        .let { next -> fleet.sections?.let { next.withDeclaredSections(it) } ?: next }
                }
            }
            if (!committed) return@withLock false
            notificationSink.setBadge(_state.value.unreadCount)
            true
        }
    }

    /** A room notification can name any channel task, not just the active one. */
    private suspend fun openRoomNotification(
        activeClient: CompanionClient,
        binding: RuntimeBinding,
        room: Room,
        threadId: String,
    ): Chat.RoomChat {
        if (room.threadId == threadId) return Chat.RoomChat(room)
        val switched = activeClient.switchRoomTask(room.id, threadId)
        currentCoroutineContext().ensureActive()
        if (!binding.commit { it.apply(Frame.Room(switched)) }) throw CancellationException("The active computer changed.")
        return Chat.RoomChat(_state.value.rooms.firstOrNull { it.id == room.id } ?: switched)
    }

    fun consumeFocus(messageId: String) {
        if (_focusedMessageId.value == messageId) _focusedMessageId.value = null
    }

    /**
     * A tapped "Opened thread #Title on Scout" chip. Lands on that thread by
     * the route a thread row uses, which only changes what this phone is
     * looking at — a bot mid-turn keeps working where it was. A thread the
     * computer no longer has reports that it is gone, keeping the current
     * conversation in place.
     *
     * @return the bot pinned to the exact thread; null when it is gone,
     *   opening fails, or the connection changes.
     */
    suspend fun openThread(ref: ThreadRef): Bot? {
        currentCoroutineContext().ensureActive()
        val (activeClient, binding) = bound() ?: run {
            _actionError.value = "Pair this phone with your computer to open that thread."
            return null
        }
        binding.reportError(null)
        return try {
            if (_state.value.bot(ref.botId)?.forTask(ref.threadId) == null) {
                if (!refreshNavigationState(activeClient, binding)) return null
            }
            val selected = _state.value.bot(ref.botId)
                ?: throw APIError.Status(404, "That agent no longer exists.")
            selected.forTask(ref.threadId) ?: throw APIError.Status(404, THREAD_GONE_MESSAGE)
        } catch (error: Throwable) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            currentCoroutineContext().ensureActive()
            if (!binding.stillCurrent()) return null
            binding.reportError(error.message)
            null
        }
    }

    /**
     * The one policy behind every task mutation below: offline yields
     * [offline], cancellation is not an error and propagates, and anything
     * else is reported as the action error and also yields [offline]. Each
     * method differs only in its endpoint and the frame that commits its
     * result, so no Bot-versus-Room copy can drift again. Internal rather
     * than private only so SessionTaskCrudTest can pin the cancellation
     * contract directly.
     */
    internal suspend fun <T> mutateTask(offline: T, mutation: suspend RuntimeBinding.(CompanionClient) -> T): T {
        val (activeClient, binding) = bound() ?: return offline
        return try {
            val result = binding.mutation(activeClient)
            currentCoroutineContext().ensureActive()
            // A result for a runtime that is gone is not this computer's answer.
            if (binding.stillCurrent()) result else offline
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            binding.reportError(error.message)
            // Cancellation that lands while the failure is being reported
            // still wins: a cancelled caller throws instead of observing the
            // offline value.
            currentCoroutineContext().ensureActive()
            offline
        }
    }

    suspend fun createTask(forBot: Bot, title: String?): Bot? = mutateTask(null) { client ->
        client.createTask(forBot.id, title).also { updated ->
            commit { it.apply(Frame.Bot(updated)) }
        }
    }

    suspend fun switchTask(task: BotTask, forBot: Bot): Bot? {
        if (task.threadId == forBot.threadId) return forBot
        return mutateTask(null) { client ->
            client.switchTask(forBot.id, task.threadId).also { updated ->
                commit { it.apply(Frame.Bot(updated)) }
            }
        }
    }

    suspend fun renameTask(task: BotTask, forBot: Bot, title: String): Boolean = mutateTask(false) { client ->
        client.renameTask(forBot.id, task.threadId, title)
        refresh()
        true
    }

    /** Starts a summary of the older messages in [forBot]'s thread. True when the computer took the request. */
    suspend fun compact(forBot: Bot, threadId: String? = forBot.threadId): Boolean = mutateTask(false) { client ->
        client.compact(forBot.id, threadId)
        true
    }

    suspend fun pinTask(task: BotTask, chat: Chat, pinned: Boolean): Boolean = mutateTask(false) { client ->
        when (chat) {
            is Chat.BotChat -> client.setTaskPinned(chat.bot.id, task.threadId, pinned)
            is Chat.RoomChat -> client.setRoomTaskPinned(chat.room.id, task.threadId, pinned, task.title)
        }
        refresh()
        true
    }

    suspend fun archiveTask(task: BotTask, forBot: Bot, archivedAt: Double?): Boolean = mutateTask(false) { client ->
        client.setTaskArchived(forBot.id, task.threadId, archivedAt)
        refresh()
        true
    }

    suspend fun deleteTask(task: BotTask, forBot: Bot): Bot? = mutateTask(null) { client ->
        client.deleteTask(forBot.id, task.threadId).also { updated ->
            commit { it.apply(Frame.Bot(updated)) }
        }
    }

    /**
     * Snooze or wake one thread. The PATCH's bot frame also lands on the
     * stream; the refresh keeps the sheet from waiting for it, exactly as
     * rename does.
     */
    suspend fun snoozeTask(task: BotTask, forBot: Bot, snoozedUntil: Long?): Boolean {
        val (activeClient, binding) = bound() ?: return false
        return try {
            activeClient.snoozeTask(forBot.id, task.threadId, snoozedUntil)
            refresh()
            true
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            binding.reportError(error.message)
            false
        }
    }

    suspend fun createTask(forRoom: Room, title: String?): Room? = mutateTask(null) { client ->
        client.createRoomTask(forRoom.id, title).also { updated ->
            commit { it.apply(Frame.Room(updated)) }
        }
    }

    suspend fun switchTask(task: BotTask, forRoom: Room): Room? {
        if (task.threadId == forRoom.threadId) return forRoom
        return mutateTask(null) { client ->
            client.switchRoomTask(forRoom.id, task.threadId).also { updated ->
                commit { it.apply(Frame.Room(updated)) }
            }
        }
    }

    suspend fun renameTask(task: BotTask, forRoom: Room, title: String): Boolean = mutateTask(false) { client ->
        client.renameRoomTask(forRoom.id, task.threadId, title)
        refresh()
        true
    }

    suspend fun deleteTask(task: BotTask, forRoom: Room): Room? = mutateTask(null) { client ->
        client.deleteRoomTask(forRoom.id, task.threadId).also { updated ->
            commit { it.apply(Frame.Room(updated)) }
        }
    }

    suspend fun updateProfile(patch: BotProfilePatch, forBot: Bot): Bot? {
        val (activeClient, binding) = bound() ?: return null
        return try {
            val updated = activeClient.updateProfile(forBot.id, patch)
            currentCoroutineContext().ensureActive()
            if (!binding.commit { it.apply(Frame.Bot(updated)) }) return null
            updated
        } catch (error: Throwable) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            binding.reportError(error.message)
            null
        }
    }

    suspend fun updateLook(botId: String, patch: BotLookPatch): Bot? {
        val (activeClient, binding) = bound() ?: return null
        if (patch.isEmpty) return _state.value.bot(botId)
        return try {
            val updated = activeClient.updateLook(botId, patch)
            currentCoroutineContext().ensureActive()
            if (!binding.commit { it.apply(Frame.Bot(updated)) }) return null
            updated
        } catch (error: Throwable) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            null
        }
    }

    /**
     * The model catalog lives on the paired computer because availability
     * depends on which engines are installed and signed in there.
     */
    suspend fun modelInstances(): List<Instance> {
        val (activeClient, binding) = bound() ?: return emptyList()
        return try {
            activeClient.instances()
        } catch (error: Throwable) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            binding.reportError(error.message)
            emptyList()
        }
    }

    suspend fun updateModel(selection: ModelSelection, forBot: Bot, quietly: Boolean = false): Bot? {
        val (activeClient, binding) = bound() ?: return null
        return try {
            val updated = activeClient.updateModel(forBot.id, selection, forBot.threadId)
            currentCoroutineContext().ensureActive()
            // The Bot can carry approval information of the computer it came from: it is kept only for that computer.
            if (!binding.commit { it.apply(Frame.Bot(updated)) }) return null
            updated.forTask(forBot.threadId)
        } catch (error: Throwable) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            if (!quietly) binding.reportError(error.message)
            null
        }
    }

    /** Pin or mark unread. True only after the computer answers; the bot then shows its new state. */
    suspend fun setBotFlags(botId: String, pinned: Boolean? = null, unread: Boolean? = null): Boolean {
        val (activeClient, binding) = bound() ?: return false
        return try {
            val updated = activeClient.setBotFlags(botId, pinned, unread)
            currentCoroutineContext().ensureActive()
            binding.commit { it.apply(Frame.Bot(updated)) }
        } catch (error: Throwable) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            false
        }
    }

    /**
     * Mark every unread conversation of one bot read, one request for each.
     * True only after every request succeeds; a partial failure reloads the bot.
     */
    suspend fun markBotAllRead(botId: String): Boolean {
        // The client, its binding and the unread ids come from one runtime: capture all three under one lock.
        val (activeClient, binding, unreadIds) = synchronized(runtimeLock) {
            val active = client ?: return false
            val ids = _state.value.bot(botId)?.unreadThreadIds() ?: return false
            Triple(active, RuntimeBinding(_connection.value?.id, _runtimeEpoch.value), ids)
        }
        if (unreadIds.isEmpty()) return true
        var allRead = true
        for (threadId in unreadIds) {
            // Stop the batch as soon as its runtime retires: no request goes to the next computer's bot.
            if (!binding.stillCurrent()) return false
            try {
                activeClient.markBotRead(botId, threadId)
                currentCoroutineContext().ensureActive()
            } catch (error: Throwable) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                allRead = false
            }
        }
        if (!binding.stillCurrent()) return false
        if (!allRead) scope.launch { refresh() }
        return allRead
    }

    /** Move one bot to a department; an empty name means General. True only after the computer answers. */
    suspend fun moveToDepartment(botId: String, name: String): Boolean =
        assignSection(name, listOf(botId), reportsError = false) != null

    suspend fun setWorksOn(bot: Bot, ownServer: Boolean): WorksOnResult {
        val (activeClient, binding) = bound() ?: return WorksOnResult.Refused(WorksOnRefusal.Failed)
        return try {
            val updated = activeClient.setWorksOn(bot.id, ownServer)
            currentCoroutineContext().ensureActive()
            if (!binding.commit { it.apply(Frame.Bot(updated)) }) return WorksOnResult.Refused(WorksOnRefusal.Failed)
            WorksOnResult.Changed(updated)
        } catch (error: Throwable) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            if (!binding.stillCurrent()) return WorksOnResult.Refused(WorksOnRefusal.Failed)
            WorksOnResult.Refused(WorksOnChoice.refusal(error))
        }
    }

    /**
     * Change how much one conversation may do before it asks. A refusal comes
     * back typed so the screen can answer in words; it is not the app-wide error.
     * [expected] is the runtime the screen showed when it was opened; when the phone serves another one now, the
     * change is refused before any request is sent.
     */
    suspend fun setApprovalMode(
        bot: Bot,
        threadId: String,
        mode: ApprovalMode,
        acknowledgeLocalAuto: Boolean = false,
        expected: RuntimeBinding,
    ): ApprovalModeResult {
        // The answer of a computer that is no longer the paired one says nothing about this one.
        val retired = ApprovalModeResult.Refused(ApprovalModeRefusal.Other(null))
        val (activeClient, binding) = bound() ?: return retired
        // A sheet shown for one computer never changes the mode of another: no request leaves the phone.
        if (expected != binding) return retired
        return try {
            val updated = activeClient.setApprovalMode(bot.id, threadId, mode, acknowledgeLocalAuto)
            currentCoroutineContext().ensureActive()
            if (!binding.commit { it.apply(Frame.Bot(updated)) }) return retired
            ApprovalModeResult.Changed(updated, updated.approvalModeIn(threadId))
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            if (!binding.stillCurrent()) return retired
            ApprovalModeResult.Refused(ApprovalModeRefusal.from(error))
        }
    }

    /** The level a conversation runs at now, from what the phone already holds. */
    fun approvalMode(bot: Bot, threadId: String): ApprovalMode? =
        _state.value.bot(bot.id)?.approvalModeIn(threadId)

    suspend fun botActivity(bot: Bot, limit: Int? = null): List<ActivityRow>? =
        mutateTask(null) { it.activity(bot.id, limit) }

    suspend fun botSkills(bot: Bot): List<BotSkill>? = mutateTask(null) { it.skills(bot.id) }

    suspend fun botData(bot: Bot): BotData? = mutateTask(null) { it.botData(bot.id) }

    suspend fun botDataPage(bot: Bot, request: DataPageRequest): DataPage? =
        mutateTask(null) { it.dataPage(bot.id, request) }

    suspend fun teamMap(): TeamMap? = mutateTask(null) { it.teamMap() }

    suspend fun teamMemory(section: String): TeamMemoryPage? = mutateTask(null) { it.teamMemory(section) }

    suspend fun addTeamMemory(
        section: String,
        kind: TeamMemoryKind,
        name: String,
        detail: String = "",
        aliases: List<String>? = null,
    ): TeamMemoryEdit? = mutateTask(null) { it.addTeamMemory(section, kind, name, detail, aliases) }

    suspend fun updateTeamMemory(
        section: String,
        entry: TeamMemoryEntry,
        name: String? = null,
        detail: String? = null,
        aliases: List<String>? = null,
        kind: TeamMemoryKind? = null,
        pinned: Boolean? = null,
        accept: Boolean = false,
    ): TeamMemoryEdit? = mutateTask(null) { client ->
        val revision = entry.revision ?: throw APIError.Status(428, RELOAD_MEMORY_MESSAGE)
        client.updateTeamMemory(section, entry.id, revision, name, detail, aliases, kind, pinned, accept)
    }

    suspend fun deleteTeamMemory(section: String, entry: TeamMemoryEntry): TeamMemoryEdit? = mutateTask(null) { client ->
        val revision = entry.revision ?: throw APIError.Status(428, RELOAD_MEMORY_MESSAGE)
        client.deleteTeamMemory(section, entry.id, revision)
    }

    suspend fun deleteConnectorAccount(connectorId: String, accountId: String): Boolean = mutateTask(false) { client ->
        client.deleteConnectorAccount(connectorId, accountId)
        true
    }

    suspend fun computerControl(bot: Bot, threadId: String? = null): ComputerControlState? =
        mutateTask(null) { it.computerControl(bot.id, threadId) }

    suspend fun controlComputer(
        bot: Bot,
        action: ComputerControlAction,
        leaseId: String? = null,
        threadId: String? = null,
        target: String? = null,
    ): ComputerControlState? = mutateTask(null) { it.computerControl(bot.id, action, leaseId, threadId, target) }

    /** This pairing may read the model list and continue at a limit reset. */
    val canAdminister: Boolean get() = _connection.value?.canAdminister ?: false

    private val _modelListForbidden = MutableStateFlow(false)

    /** This phone may not read the model list (a Team member phone). */
    val modelListForbidden: StateFlow<Boolean> = _modelListForbidden.asStateFlow()

    private val _limitResetForbidden = MutableStateFlow(false)

    /** This phone may not continue a turn at the limit reset. */
    val limitResetForbidden: StateFlow<Boolean> = _limitResetForbidden.asStateFlow()

    /**
     * The models a person can switch to. A 403 means this phone has no access;
     * it is remembered for the session and raises no error banner.
     */
    suspend fun switchableModels(): ModelListResult {
        val (activeClient, binding) = bound() ?: return ModelListResult.Failed
        if (!canAdminister) return ModelListResult.Forbidden.also { _modelListForbidden.value = true }
        return try {
            val instances = activeClient.instances()
            // The list and the forbidden flag of a computer that is gone say nothing about this one.
            if (!binding.stillCurrent()) return ModelListResult.Failed
            ModelListResult.Loaded(instances).also { _modelListForbidden.value = false }
        } catch (error: Throwable) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            if (!binding.stillCurrent()) return ModelListResult.Failed
            if (error is APIError.Status && error.code == 403) {
                _modelListForbidden.value = true
                ModelListResult.Forbidden
            } else {
                ModelListResult.Failed
            }
        }
    }

    /**
     * Send the person's last message again in this conversation, as the
     * desktop "Try again" does. False when there is nothing to send.
     */
    suspend fun retryLast(chat: Chat.BotChat): Boolean {
        val bot = chat.bot.let { _state.value.bot(it.id)?.forTask(chat.threadId) ?: it }
        val last = FailedTurnActions.lastUserMessage(_state.value.transcript(chat.threadId)) ?: return false
        edit(last, bot, last.text.orEmpty())
        return true
    }

    /** Ask the computer to continue this turn when the limit resets. A 403 hides the action. */
    suspend fun continueAtReset(chat: Chat.BotChat): Boolean {
        val (activeClient, binding) = bound() ?: return false
        if (!canAdminister) return false
        return try {
            activeClient.limitResetContinue(chat.bot.id, chat.threadId)
            true
        } catch (error: Throwable) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            if (error is APIError.Status && error.code == 403) {
                _limitResetForbidden.value = true
            } else {
                binding.reportError(error.message)
            }
            false
        }
    }

    suspend fun uploadAvatar(
        data: ByteArray,
        mime: String,
        forBot: Bot,
        crop: AvatarCrop,
    ): Bot? {
        val (activeClient, binding) = bound() ?: return null
        return try {
            val avatarUrl = activeClient.uploadAvatar(data, mime)
            currentCoroutineContext().ensureActive()
            val current = _state.value.bot(forBot.id) ?: forBot
            updateProfile(
                BotProfilePatch(
                    avatarUrl = BotProfilePatch.AvatarURL.Set(avatarUrl),
                    avatarCrop = crop,
                ),
                current,
            )
        } catch (error: Throwable) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            binding.reportError(error.message)
            null
        }
    }

    suspend fun generateAvatar(prompt: String, forBot: Bot): Bot? {
        val (activeClient, binding) = bound() ?: return null
        return try {
            val updated = activeClient.generateAvatar(forBot.id, prompt)
            currentCoroutineContext().ensureActive()
            if (!binding.commit { it.apply(Frame.Bot(updated)) }) return null
            updated
        } catch (error: Throwable) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            binding.reportError(error.message)
            null
        }
    }

    suspend fun avatarData(forBot: Bot): ByteArray? {
        val path = forBot.avatarUrl ?: return null
        return try {
            client?.avatar(path)
        } catch (error: Throwable) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            null
        }
    }

    suspend fun voiceOptions(): List<Voice> {
        val (activeClient, binding) = bound() ?: return emptyList()
        return try {
            activeClient.voices()
        } catch (error: Throwable) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            binding.reportError(error.message)
            emptyList()
        }
    }

    suspend fun previewVoice(voiceId: String, forBot: Bot): ByteArray? {
        val (activeClient, binding) = bound() ?: return null
        return try {
            activeClient.previewVoice("Hello, I'm ${forBot.name}.", voiceId)
        } catch (error: Throwable) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            binding.reportError(error.message)
            null
        }
    }

    suspend fun configStatus(): ConfigStatus? = try {
        client?.config()
    } catch (error: Throwable) {
        if (error is kotlinx.coroutines.CancellationException) throw error
        null
    }

    /**
     * Switch the workspace's voice engine. The sheet reloads the voice list
     * afterwards, because every engine names its own voices.
     */
    suspend fun switchVoiceProvider(provider: VoiceProvider): ConfigStatus? {
        val (activeClient, binding) = bound() ?: return null
        return try {
            activeClient.updateVoiceProvider(provider)
        } catch (error: Throwable) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            binding.reportError(error.message)
            null
        }
    }

    suspend fun loadConnectorCatalog(): ConnectorCatalog? {
        val (activeClient, binding) = bound() ?: return null
        return try {
            activeClient.connectorCatalog()
        } catch (error: Throwable) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            binding.reportError(error.message)
            null
        }
    }

    suspend fun loadAllConnectorStatuses(): ConnectorStatuses? {
        val (activeClient, binding) = bound() ?: return null
        return try {
            activeClient.allConnectorStatuses()
        } catch (error: Throwable) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            binding.reportError(error.message)
            null
        }
    }

    suspend fun authorizeConnector(slug: String, alias: String?): URI? {
        val (activeClient, binding) = bound() ?: return null
        return try {
            activeClient.authorizeConnector(slug, alias)
        } catch (error: Throwable) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            binding.reportError(error.message)
            null
        }
    }

    private suspend fun <T> cardCall(block: suspend (CompanionClient) -> T): Result<T> {
        val activeClient = client ?: return Result.failure(APIError.BadUrl)
        return try {
            Result.success(block(activeClient))
        } catch (error: Throwable) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            Result.failure(error)
        }
    }

    suspend fun authorizeConnectorCard(botId: String, messageId: String, threadId: String): Result<URI> =
        cardCall { it.authorizeConnectorCard(botId, messageId, threadId) }

    suspend fun connectorCardStatus(botId: String, messageId: String, threadId: String): Result<ConnectorCardStatus> =
        cardCall { it.connectorCardStatus(botId, messageId, threadId) }

    suspend fun resumeConnectorCard(botId: String, messageId: String, threadId: String): Result<Unit> =
        cardCall { it.resumeConnectorCard(botId, messageId, threadId) }

    suspend fun dismissConnectorCard(botId: String, messageId: String, threadId: String): Result<Unit> =
        cardCall { it.dismissConnectorCard(botId, messageId, threadId) }

    suspend fun answerSignIn(botId: String, messageId: String, threadId: String, choice: SignInChoice): Result<Unit> =
        cardCall { it.answerSignIn(botId, messageId, threadId, choice) }

    private val _aiUsageHiddenFor = MutableStateFlow<String?>(null)
    val aiUsageHiddenFor: StateFlow<String?> = _aiUsageHiddenFor.asStateFlow()

    val aiUsageHidden: Boolean
        get() = AiUsageRules.hidden(_aiUsageHiddenFor.value, _connection.value?.id)

    suspend fun aiUsage(refresh: Boolean = false): AiUsageResult {
        val (activeClient, binding) = bound() ?: return AiUsageResult.Failed
        val connectionId = binding.connectionId
        return try {
            val report = activeClient.engineLimits(refresh)
            if (!binding.stillCurrent()) return AiUsageResult.Failed
            if ((report == null || report.engines.isEmpty()) && connectionId != null) {
                _aiUsageHiddenFor.value = connectionId
            }
            if (report == null) AiUsageResult.NotAvailable else AiUsageResult.Report(report)
        } catch (error: CancellationException) {
            throw error
        } catch (error: APIError) {
            if (!binding.stillCurrent()) return AiUsageResult.Failed
            if (error.isUnauthorized) binding.markUnauthorized()
            AiUsageResult.Failed
        }
    }

    suspend fun loadRoutines(): RoutinesResponse {
        val (activeClient, binding) = bound() ?: return RoutinesResponse(emptyList(), emptyList())
        return try {
            activeClient.routines()
        } catch (error: Throwable) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            binding.reportError(error.message)
            RoutinesResponse(emptyList(), emptyList())
        }
    }

    /**
     * The overview read. [quiet] suppresses the global action error for
     * supplementary callers — the profile sheet's grants fetch — where a
     * failed read should leave one section absent, not error the profile.
     */
    suspend fun loadOverview(botId: String, quiet: Boolean = false): BotOverview? {
        val (activeClient, binding) = bound() ?: return null
        return try {
            val overview = activeClient.overview(botId)
            currentCoroutineContext().ensureActive()
            overview.takeIf { binding.stillCurrent() }
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            if (!quiet) binding.reportError(error.message)
            null
        }
    }

    suspend fun loadRoutineRunAvailability(): RoutineRunAvailability? {
        val (activeClient, binding) = bound() ?: return null
        return try {
            coroutineScope {
                val config = async { activeClient.config() }
                val instances = async { activeClient.instances() }
                RoutineRunAvailability(config.await(), instances.await())
            }
        } catch (error: Throwable) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            binding.reportError(error.message)
            null
        }
    }

    suspend fun saveRoutine(input: RoutineInput, id: String?): Routine? {
        val (activeClient, binding) = bound() ?: return null
        return try {
            if (id == null) activeClient.createRoutine(input) else activeClient.updateRoutine(id, input)
        } catch (error: Throwable) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            binding.reportError(error.message)
            null
        }
    }

    suspend fun setRoutineEnabled(routine: Routine, enabled: Boolean): Routine? {
        val (activeClient, binding) = bound() ?: return null
        return try {
            activeClient.setRoutineEnabled(routine.id, enabled)
        } catch (error: Throwable) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            binding.reportError(error.message)
            null
        }
    }

    suspend fun runRoutine(routine: Routine): RoutineRun? {
        val (activeClient, binding) = bound() ?: return null
        return try {
            activeClient.runRoutine(routine.id)
        } catch (error: Throwable) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            binding.reportError(error.message)
            null
        }
    }

    suspend fun deleteRoutine(routine: Routine): Boolean {
        val (activeClient, binding) = bound() ?: return false
        return try {
            activeClient.deleteRoutine(routine.id)
            true
        } catch (error: Throwable) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            binding.reportError(error.message)
            false
        }
    }

    suspend fun react(to: Message, inThreadId: String, emoji: String) {
        val (activeClient, binding) = bound() ?: return
        try {
            val patched = activeClient.toggleReaction(inThreadId, to.id, emoji)
            binding.commit { it.apply(Frame.MessagePatch(inThreadId, patched)) }
        } catch (error: Throwable) {
            binding.reportError(error.message)
        }
    }

    /**
     * Edit and retry. The edited text replaces the old message on screen the
     * moment it is sent, hiding the old answer, and the computer's fork takes
     * over as soon as either its response or its stream frames land. A failed
     * edit simply drops the stand-in, so the old branch returns.
     */
    suspend fun edit(message: Message, forBot: Bot, text: String) {
        if (client == null) return
        val threadId = forBot.threadId
        val pending = PendingEdit(message.id, text, baseLeafId = _state.value.botForThread(threadId)?.activeLeafId)
        _state.update { it.copy(pendingEdits = it.pendingEdits + (threadId to pending)) }
        try {
            perform { activeClient ->
                val fork = activeClient.edit(forBot.id, message.id, text, threadId, pending.requestId)
                if (fork != null) commit { it.adoptEdit(fork, threadId, expectedPending = pending) }
            }
        } finally {
            _state.update {
                if (it.pendingEdits[threadId] == pending) it.copy(pendingEdits = it.pendingEdits - threadId) else it
            }
        }
    }

    suspend fun switchVersion(to: Message, forBot: Bot) {
        val (activeClient, binding) = bound() ?: return
        try {
            val leaf = activeClient.setActiveBranch(forBot.id, to.id, forBot.threadId)
            binding.commit { it.apply(Frame.Thread(forBot.threadId, leaf)) }
        } catch (error: Throwable) {
            binding.reportError(error.message)
        }
    }

    suspend fun export(threadId: String, format: String): ExportedTranscript? {
        val (activeClient, binding) = bound() ?: return null
        return try {
            val exported = activeClient.export(threadId, format)
            // A transcript of a retired runtime is never handed to the screen.
            if (!binding.stillCurrent()) return null
            ExportedTranscript(exported.data, exported.filename, exported.contentType)
        } catch (error: Throwable) {
            binding.reportError(error.message)
            null
        }
    }

    private suspend fun perform(quietly: Boolean = false, body: suspend RuntimeBinding.(CompanionClient) -> Unit) {
        val (activeClient, binding) = bound() ?: return
        try {
            binding.body(activeClient)
        } catch (error: APIError) {
            // Only the live computer's 401 may mark this session revoked.
            if (error.isUnauthorized) {
                binding.markUnauthorized()
            } else if (!quietly) {
                binding.reportError(error.message)
            }
        } catch (error: Throwable) {
            if (!quietly) binding.reportError(error.message)
        }
    }

    companion object {
        const val STORAGE_UNAVAILABLE_MESSAGE =
            "This phone couldn't read its saved connection just now."
        const val SPENT_QR_MESSAGE =
            "That pairing code was already used. Start pairing again on your computer and rescan the new QR code."
        const val INVALID_PAIRING_LINK_MESSAGE =
            "That pairing invitation is not valid. Start pairing again on your computer."
        const val THREAD_GONE_MESSAGE = "That thread is no longer on your computer."
        const val OFFLINE_MESSAGE = "This computer is offline."
        const val RELOAD_MEMORY_MESSAGE = "Reload this memory and try again."
        const val QUEUED_STEER_UNSUPPORTED_MESSAGE =
            "This computer is too old to send a queued message into a running turn. Update Seira on it."

        /** Empty resumes in a row before the stream starts over without a cursor. */
        internal const val EMPTY_RESUMES_BEFORE_FRESH_START = 2
        private const val NETWORK_CHANGE_DEBOUNCE_MILLIS = 1_000L
        private const val PUSH_UNREGISTER_TIMEOUT_MILLIS = 4_000L
        private const val REVOKE_TIMEOUT_MILLIS = 10_000L

        /** High-entropy QR token — distinct from a retryable six-digit code. */
        fun isQrCredential(credential: String): Boolean =
            DesktopWireIds.PAIRING_TOKEN_PREFIXES.any { credential.startsWith(it) } ||
                !(credential.length == 6 && credential.all { it in '0'..'9' })
    }
}

/** Thrown only when another pairing redemption is already running. */
class PairingInProgressException : IllegalStateException("Another pairing attempt is already in progress.")

/** Thrown when a burned QR credential is presented again. */
class SpentPairingCredentialException : IllegalStateException(Session.SPENT_QR_MESSAGE)

/** What became of an in-chat Claude Code update. */
/** What became of an answer typed in words. */
sealed interface TypedAnswerResult {
    /** The computer took it as the answer; the card settles with it. */
    data object Answered : TypedAnswerResult

    /**
     * The question went away first and took nothing. The words are still the
     * person's to send as an ordinary message.
     */
    data object Gone : TypedAnswerResult

    /** Not delivered (offline, refused). Carries what to show. */
    data class Failed(val message: String) : TypedAnswerResult
}

sealed interface ClaudeUpdateResult {
    data class Updated(val version: String) : ClaudeUpdateResult
    data class Failed(val message: String) : ClaudeUpdateResult
}
