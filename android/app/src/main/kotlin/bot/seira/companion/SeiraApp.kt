package bot.seira.companion

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import bot.seira.companion.audio.LiveCallManager
import bot.seira.companion.audio.VoicePreviewPlayer
import bot.seira.companion.audio.VoiceNotePlayer
import bot.seira.companion.avatar.AvatarImageStore
import bot.seira.companion.core.CompanionClient
import bot.seira.companion.core.Session
import bot.seira.companion.network.AndroidNetworkMonitor
import bot.seira.companion.discovery.NsdDiscovery
import bot.seira.companion.lifecycle.AlwaysOnConnectionService
import bot.seira.companion.lifecycle.AlwaysOnConnectionState
import bot.seira.companion.lifecycle.ServiceProcessAnchor
import bot.seira.companion.lifecycle.SessionLingerController
import bot.seira.companion.lifecycle.installSessionLinger
import bot.seira.companion.glance.GlanceActionHandler
import bot.seira.companion.glance.GlanceNotifier
import bot.seira.companion.notifications.LocalNotificationPoster
import bot.seira.companion.notifications.NotificationActionHandler
import bot.seira.companion.push.PushDelivery
import bot.seira.companion.push.RemotePush
import bot.seira.companion.storage.PrefsPushRegistrationStore
import bot.seira.companion.shortcuts.BotShortcutSync
import bot.seira.companion.shortcuts.BotShortcuts
import bot.seira.companion.tile.TalkTileState
import bot.seira.companion.widget.UpdatesWidget
import bot.seira.companion.widget.WidgetFaces
import bot.seira.companion.widget.WidgetSnapshotStore
import bot.seira.companion.widget.WidgetSync
import bot.seira.companion.widget.WorkManagerFreshness
import androidx.glance.appwidget.updateAll
import bot.seira.companion.storage.ChatPreferences
import bot.seira.companion.permissions.CompanionPermissions
import bot.seira.companion.sharing.ShareInbox
import bot.seira.companion.storage.AlwaysOnPreferences
import bot.seira.companion.storage.DataStoreConnectionStore
import bot.seira.companion.storage.OnboardingPreferences
import bot.seira.companion.storage.KeystoreTokenStore
import bot.seira.companion.ui.FilePreviews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Application entry: owns the long-lived [Session] and wires
 * [ProcessLifecycleOwner] the way iOS `scenePhase` drives connect and linger.
 */
class SeiraApp : Application() {
    private val appJob = SupervisorJob()
    val appScope = CoroutineScope(appJob + Dispatchers.Main.immediate)

    lateinit var session: Session
        private set
    lateinit var notifications: LocalNotificationPoster
        private set
    lateinit var notificationActions: NotificationActionHandler
        private set
    lateinit var glanceActions: GlanceActionHandler
        private set
    lateinit var shortcuts: BotShortcuts
        private set
    @Volatile
    var pushDelivery: PushDelivery? = null
        private set
    lateinit var discovery: NsdDiscovery
        private set
    lateinit var permissions: CompanionPermissions
        private set

    /**
     * One instance for the whole process: [Session] writes the first-pair
     * education marker into it as part of committing a pairing, and the root
     * router reads it to decide whether that step is due.
     */
    lateinit var onboarding: OnboardingPreferences
        private set
    lateinit var avatars: AvatarImageStore
        private set
    lateinit var voicePreview: VoicePreviewPlayer
        private set
    lateinit var voiceNotes: VoiceNotePlayer
        private set
    lateinit var liveCalls: LiveCallManager
        private set
    lateinit var linger: SessionLingerController
        private set
    lateinit var shareInbox: ShareInbox
        private set
    lateinit var alwaysOn: AlwaysOnPreferences
        private set
    lateinit var chatPreferences: ChatPreferences
        private set

    override fun onCreate() {
        super.onCreate()
        shareInbox = ShareInbox()
        ShareInbox.cleanStale(cacheDir)
        // Launch stays non-blocking. The preview store awaits the shared
        // completion barrier on its own IO coroutine before writing anything.
        FilePreviews.startStaleCleanup(this, appScope)
        notifications = LocalNotificationPoster(this)
        discovery = NsdDiscovery(this)
        permissions = CompanionPermissions(this)
        onboarding = OnboardingPreferences(this)
        val http = okhttp3.OkHttpClient()
        session = Session(
            scope = appScope,
            httpClient = http,
            networkMonitor = AndroidNetworkMonitor(this),
            routeChoiceFn = { connection -> CompanionClient.firstReachableRoute(connection, http) },
            connectionStore = DataStoreConnectionStore(this),
            tokenStore = KeystoreTokenStore(this),
            onboardingStore = onboarding,
            deviceNameProvider = {
                // User-visible device name; Settings.Global.DEVICE_NAME on API 25+.
                android.provider.Settings.Global.getString(contentResolver, "device_name")
                    ?.takeIf { it.isNotBlank() }
                    ?: android.os.Build.MODEL
            },
            notificationSink = notifications,
            pushStore = PrefsPushRegistrationStore(this),
        )
        avatars = AvatarImageStore(fetch = session::avatarData)
        shortcuts = BotShortcuts(this)
        notifications.bind(session, avatars, shortcuts, appScope)
        notificationActions = NotificationActionHandler(session, notifications)
        val glance = GlanceNotifier(this, session.state, session.status, avatarFor = { bot -> avatars.bitmapFor(bot) })
        glanceActions = GlanceActionHandler(this, session, glance, glance::budget)
        glance.start(appScope)
        pushDelivery = PushDelivery(session, notifications)
        RemotePush(this, session, appScope).start()
        appScope.launch {
            BotShortcutSync(session, avatars, shortcuts, TalkTileState(this@SeiraApp)).run()
        }
        chatPreferences = ChatPreferences(this)
        val widgetStore = WidgetSnapshotStore.forContext(this)
        val widgetFaces = WidgetFaces(this, widgetStore)
        appScope.launch {
            WidgetSync(
                session = session,
                store = widgetStore,
                detail = chatPreferences.activityDetail,
                faces = widgetFaces::ensure,
                refresh = { UpdatesWidget().updateAll(this@SeiraApp) },
                freshness = WorkManagerFreshness(this@SeiraApp),
            ).run()
        }
        // Live calls hold media across screens, and end when the process
        // leaves the foreground — not when a rotation recreates the Activity —
        // so the manager is app-scoped and observes the process lifecycle, as
        // the linger controller below does.
        liveCalls = LiveCallManager(this, session, appScope)
        ProcessLifecycleOwner.get().lifecycle.addObserver(liveCalls)
        // A voice note or a voice preview asks for the audio focus, and the
        // call ends when it loses that focus: both players refuse while the
        // call holds the audio, whatever asks them to play.
        val liveCallHoldsAudio = { liveCalls.state.value.holdsMedia }
        voicePreview = VoicePreviewPlayer(this, liveCallHoldsAudio = liveCallHoldsAudio)
        voiceNotes = VoiceNotePlayer(this, liveCallHoldsAudio = liveCallHoldsAudio)

        // iOS resets the avatar cache inside signOut. Observe Unpaired here so
        // the platform cache cannot outlive the pairing that minted its URLs.
        appScope.launch {
            session.status
                .map { it is Session.Status.Unpaired }
                .distinctUntilChanged()
                .collect { unpaired ->
                    if (unpaired) avatars.clearBlocking()
                }
        }

        // Foreground reconnects; background lingers. Cutting the stream at
        // onStop would drop a turn that completes seconds after Home — the
        // notification is produced by this stream and nothing else.
        // `session.linger()` on iOS (`ios/App/CompanionApp.swift:26`).
        linger = installSessionLinger(
            lifecycle = ProcessLifecycleOwner.get().lifecycle,
            session = session,
            scope = appScope,
            anchor = ServiceProcessAnchor(this),
            alwaysOn = { AlwaysOnConnectionState.active },
        )

        alwaysOn = AlwaysOnPreferences(this)
        // Covers the case where the process was relaunched (not booted) while
        // the setting was on — e.g. the OS killed the whole app under memory
        // pressure and the user (or a notification tap) reopened it. A device
        // reboot is covered separately by AlwaysOnBootReceiver, which can run
        // before anything ever constructs this Application's Activity.
        if (BuildConfig.ALWAYS_ON && alwaysOn.enabled.value) {
            AlwaysOnConnectionService.start(this)
        }
    }
}
