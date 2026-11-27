package bot.seira.companion.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import bot.seira.companion.core.Chat
import bot.seira.companion.core.ChatTarget
import bot.seira.companion.core.target

/** The three pages of the home screen, in the order of the bottom bar. */
enum class HomeTab { Chats, Inbox, Automations }

/**
 * Where the app can be. Five places, one stack, no dependency.
 *
 * `androidx.navigation-compose` would add a library, a graph DSL and an argument
 * encoding to express roster ⇄ chat ⇄ settings ⇄ routines — CONTRIBUTING asks not
 * to add a dependency where a short module will do, and this is the short module.
 *
 * A chat is addressed by its owner rather than by a captured `Chat`: the record
 * has to be re-read from `Session.state` on every frame anyway so busy/unread
 * stay current (iOS `ChatView.current` does the same). The owner is what makes
 * the address survive: a bot keeps its id when the desktop moves it to another
 * task, and it keeps it when the task that was open is deleted — a thread does
 * not.
 */
sealed interface Destination {
    data object Roster : Destination
    data object Settings : Destination

    /** Settings → Workspace → Tasks & Routines. */
    data object Routines : Destination

    /** Home → the routine calendar (MOCA-191). */
    data object Calendar : Destination

    /** Settings → Workspace → Connected Apps. */
    data object ConnectedApps : Destination

    /** Settings → Workspace → AI usage. */
    data object AiUsage : Destination

    /** Search across chats, bots and routines. */
    data object Search : Destination

    /** The paired computers, with the current one first. */
    data object Computers : Destination

    /** The team map: who works with whom. */
    data object TeamMap : Destination

    /** What the team remembers, by department. */
    data object TeamMemory : Destination

    /** A bot's profile page, opened on [tab]. Addressed by bot id for the same reason as [Computer]. */
    data class BotProfile(val botId: String, val tab: String = BotProfileTabs.ACTIVITY) : Destination

    /** A bot's computer, watch-only. Addressed by bot id for the same reason. */
    data class Computer(val botId: String) : Destination

    /** A bot's browser, watched and driven. Separate from [Computer] because
     * driving is a different capability from watching, granted per device. */
    data class Browser(val botId: String) : Destination

    /**
     * A bot's read-only "What this bot does" — who it is, what it does, what it
     * can reach, what it won't, and its most recent changes. Addressed by bot id
     * for the same reason as [Computer]: the server-authored copy is re-read
     * fresh rather than carried in the destination.
     */
    data class Overview(val botId: String) : Destination

    /**
     * A conversation, in one of the two ways something can name one.
     *
     * [Chat] is the addressed form: what the screens produce, and what a
     * notification tap becomes after Session resolves the exact task. [Thread]
     * remains for an address that still only knows a thread id — it becomes a
     * [Chat] the moment the fleet names the owner.
     */
    sealed interface Conversation : Destination

    data class Chat(val target: ChatTarget) : Conversation

    data class Thread(val threadId: String) : Conversation
}

/** Names of the tabs of [Destination.BotProfile]. */
object BotProfileTabs {
    const val ACTIVITY = "activity"
}

/**
 * The stack of destinations, plus the two choices the home screen keeps while a
 * chat is pushed above it: the selected [HomeTab] and the department filter of
 * the Chats tab. They live here, not in the home composable, because the roster
 * leaves composition when a chat opens and a local state would be lost.
 */
@Stable
class CompanionNavigator(
    initial: List<Destination> = listOf(Destination.Roster),
    homeTab: HomeTab = HomeTab.Chats,
    sectionFilter: String? = null,
) {
    var stack: List<Destination> by mutableStateOf(initial.ifEmpty { listOf(Destination.Roster) })
        private set

    /** The page of the home screen that is selected. */
    var homeTab: HomeTab by mutableStateOf(homeTab)
        private set

    /** The department the Chats tab shows alone, or null for all chats. */
    var sectionFilter: String? by mutableStateOf(sectionFilter)
        private set

    /** Select [tab]. The department filter stays as it was. */
    fun selectTab(tab: HomeTab) {
        homeTab = tab
    }

    /** Show the Chats tab, limited to the department [section] or, with null, all chats. */
    fun showChats(section: String? = null) {
        homeTab = HomeTab.Chats
        sectionFilter = section
    }

    val current: Destination get() = stack.last()
    val canGoBack: Boolean get() = stack.size > 1

    fun push(destination: Destination) {
        if (stack.last() == destination) return
        stack = stack + destination
    }

    /** The chat behind [chat], addressed by its owner. */
    fun open(chat: Chat) {
        push(Destination.Chat(chat.target))
    }

    fun pop() {
        if (canGoBack) stack = stack.dropLast(1)
    }

    /**
     * Whether a composer draft for [chatId] should survive the current
     * destination leaving composition.
     *
     * True while a [Destination.Chat] for that id is still on the stack —
     * including under [Destination.Computer]. False after a pop back to the
     * roster (or any stack rewrite that dropped the chat). That is the
     * push-vs-pop distinction: Computer removes `ChatScreen` but keeps the
     * chat entry underneath; roster pop does not.
     */
    fun retainsChatDraft(chatId: String): Boolean =
        stack.any { destination ->
            destination is Destination.Chat && when (val target = destination.target) {
                is ChatTarget.Bot -> target.botId == chatId
                is ChatTarget.Room -> target.roomId == chatId
            }
        }

    fun resetToRoster() {
        stack = listOf(Destination.Roster)
        homeTab = HomeTab.Chats
        sectionFilter = null
    }

    /** A notification tap lands on the thread, with the roster behind it. */
    fun openThread(threadId: String) {
        stack = listOf(Destination.Roster, Destination.Thread(threadId))
    }

    /**
     * A notification tap that Session has already resolved to a chat — roster
     * behind it, same shape as [openThread], addressed by the stable owner.
     */
    fun openFromNotification(chat: Chat) {
        stack = listOf(Destination.Roster, Destination.Chat(chat.target))
    }

    /**
     * The fleet said who owns [threadId]: the entry stops being a thread, in
     * place, so back still leads where it did and the task remains pinned.
     *
     * Guarded on the entry still being that thread — the reader can leave while
     * the fleet is landing, and this must not re-address whatever they left to.
     */
    fun resolveThread(threadId: String, target: ChatTarget) {
        selectTask(Destination.Thread(threadId), target)
    }

    /** Only an explicit successful action replaces the locally selected task. */
    fun selectTask(from: Destination.Conversation, target: ChatTarget) {
        if (stack.last() != from) return
        stack = stack.dropLast(1) + Destination.Chat(target)
    }

    companion object {
        private const val ROSTER = "roster"
        private const val SETTINGS = "settings"
        private const val ROUTINES = "routines"
        private const val CALENDAR = "calendar"
        private const val CONNECTED_APPS = "connected-apps"
        private const val AI_USAGE = "ai-usage"
        private const val SEARCH = "search"
        private const val COMPUTERS = "computers"
        private const val TEAM_MAP = "team-map"
        private const val TEAM_MEMORY = "team-memory"
        private const val BOT_PROFILE = "botprofile:"
        private const val TAB = "tab:"
        private const val SECTION = "section:"
        private const val THREAD = "thread:"
        private const val COMPUTER = "computer:"
        private const val BROWSER = "browser:"
        private const val OVERVIEW = "overview:"
        private const val BOT_CHAT = "botchat:"
        private const val ROOM_CHAT = "roomchat:"

        fun encode(stack: List<Destination>): List<String> = stack.map {
            when (it) {
                Destination.Roster -> ROSTER
                Destination.Settings -> SETTINGS
                Destination.Routines -> ROUTINES
                Destination.Calendar -> CALENDAR
                Destination.ConnectedApps -> CONNECTED_APPS
                Destination.AiUsage -> AI_USAGE
                Destination.Search -> SEARCH
                Destination.Computers -> COMPUTERS
                Destination.TeamMap -> TEAM_MAP
                Destination.TeamMemory -> TEAM_MEMORY
                is Destination.BotProfile -> BOT_PROFILE + join(it.botId, it.tab)
                is Destination.Thread -> THREAD + it.threadId
                is Destination.Computer -> COMPUTER + it.botId
                is Destination.Browser -> BROWSER + it.botId
                is Destination.Overview -> OVERVIEW + it.botId
                is Destination.Chat -> when (val target = it.target) {
                    is ChatTarget.Bot -> BOT_CHAT + join(target.botId, target.threadId)
                    is ChatTarget.Room -> ROOM_CHAT + join(target.roomId, target.threadId)
                }
            }
        }

        fun decode(raw: List<String>): List<Destination> = raw.mapNotNull {
            when {
                it == ROSTER -> Destination.Roster
                it == SETTINGS -> Destination.Settings
                it == ROUTINES -> Destination.Routines
                it == CALENDAR -> Destination.Calendar
                it == CONNECTED_APPS -> Destination.ConnectedApps
                it == AI_USAGE -> Destination.AiUsage
                it == SEARCH -> Destination.Search
                it == COMPUTERS -> Destination.Computers
                it == TEAM_MAP -> Destination.TeamMap
                it == TEAM_MEMORY -> Destination.TeamMemory
                it.startsWith(BOT_PROFILE) -> split(it.removePrefix(BOT_PROFILE))
                    ?.let { (owner, tab) -> Destination.BotProfile(owner, tab) }
                it.startsWith(THREAD) -> Destination.Thread(it.removePrefix(THREAD))
                it.startsWith(COMPUTER) -> Destination.Computer(it.removePrefix(COMPUTER))
                it.startsWith(BROWSER) -> Destination.Browser(it.removePrefix(BROWSER))
                it.startsWith(OVERVIEW) -> Destination.Overview(it.removePrefix(OVERVIEW))
                it.startsWith(BOT_CHAT) -> split(it.removePrefix(BOT_CHAT))
                    ?.let { (owner, thread) -> Destination.Chat(ChatTarget.Bot(owner, thread)) }
                it.startsWith(ROOM_CHAT) -> split(it.removePrefix(ROOM_CHAT))
                    ?.let { (owner, thread) -> Destination.Chat(ChatTarget.Room(owner, thread)) }
                else -> null
            }
        }

        /**
         * Two ids in one string. Length-prefixed rather than separated, because
         * the harness's ids are opaque and a chosen separator is a guess about
         * what they cannot contain.
         */
        private fun join(owner: String, threadId: String): String =
            "${owner.length}:$owner$threadId"

        private fun split(raw: String): Pair<String, String>? {
            val mark = raw.indexOf(':')
            if (mark <= 0) return null
            val length = raw.substring(0, mark).toIntOrNull() ?: return null
            val start = mark + 1
            if (length < 0 || start + length > raw.length) return null
            return raw.substring(start, start + length) to raw.substring(start + length)
        }

        /**
         * First entry of a bonded save. Carrying the generation inside the
         * saved value — not only as a [rememberSaveable] input — is what lets
         * a restore from a previous bond be recognised and rejected: after
         * recreation the registry key is still call position, so inputs alone
         * cannot compare against a generation that was never written.
         */
        private const val BOND_MARKER = "bond:"

        /**
         * Saver keyed to [bondGeneration]. Restoring a value whose saved
         * generation differs (or that has no marker) returns null so
         * [rememberSaveable] runs its init and the next bond starts on roster.
         */
        fun saver(bondGeneration: Int): Saver<CompanionNavigator, List<String>> = Saver(
            save = { encodeWithBond(it.stack, bondGeneration, it.homeTab, it.sectionFilter) },
            restore = { restoreForGeneration(it, bondGeneration) },
        )

        /**
         * Pure restore path for JVM tests: returns null when [raw] was saved
         * under a different bond generation (or is not a bonded save).
         */
        fun restoreForGeneration(raw: List<String>, expectedGeneration: Int): CompanionNavigator? {
            val header = raw.firstOrNull() ?: return null
            if (!header.startsWith(BOND_MARKER)) return null
            val saved = header.removePrefix(BOND_MARKER).toIntOrNull() ?: return null
            if (saved != expectedGeneration) return null
            val rest = raw.drop(1)
            val tab = rest.firstOrNull { it.startsWith(TAB) }
                ?.removePrefix(TAB)
                ?.let { name -> HomeTab.entries.firstOrNull { it.name == name } }
                ?: HomeTab.Chats
            val section = rest.firstOrNull { it.startsWith(SECTION) }?.removePrefix(SECTION)
            return CompanionNavigator(decode(rest), tab, section)
        }

        /**
         * Encode a stack together with the bond generation that owns it, and
         * the home tab and department filter that go with it.
         */
        fun encodeWithBond(
            stack: List<Destination>,
            bondGeneration: Int,
            homeTab: HomeTab = HomeTab.Chats,
            sectionFilter: String? = null,
        ): List<String> =
            listOf(BOND_MARKER + bondGeneration, TAB + homeTab.name) +
                listOfNotNull(sectionFilter?.let { SECTION + it }) +
                encode(stack)
    }
}

/**
 * @param bondGeneration bumped whenever the bond is left ([NotificationTapCoordinator.leavesBond]).
 *   Written into the saver value and passed as a [rememberSaveable] input: a
 *   configuration change that captured `Roster → Chat` while Unauthorized /
 *   Unpaired was landing cannot materialise that chat after sign-out → pair
 *   again (§6 / single computer), because restore rejects a mismatched
 *   generation.
 */
@Composable
fun rememberCompanionNavigator(bondGeneration: Int = 0): CompanionNavigator =
    rememberSaveable(bondGeneration, saver = CompanionNavigator.saver(bondGeneration)) {
        CompanionNavigator()
    }
