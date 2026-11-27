package com.openmausbot.companion.storage

import android.content.Context
import android.content.SharedPreferences
import com.openmausbot.companion.core.Chat
import com.openmausbot.companion.core.forTask
import com.openmausbot.companion.core.ActivityDetail
import com.openmausbot.companion.core.QuickReply
import com.openmausbot.companion.core.RosterDensity
import com.openmausbot.companion.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Phone-local controls for how conversations and the home list are presented.
 *
 * These are intentionally separate from pairing and from the encrypted token
 * store: they are presentation choices, not capabilities of the paired
 * computer. One instance is placed in [CompanionEnvironment], so Settings and
 * an already-open chat observe the same values immediately.
 */
class ChatPreferences(
    private val prefs: SharedPreferences,
) {
    constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE),
    )

    private val _activityDetail = MutableStateFlow(
        ActivityDetail.fromWire(prefs.getString(ACTIVITY_DETAIL, null)),
    )
    val activityDetail: StateFlow<ActivityDetail> = _activityDetail.asStateFlow()

    private val _quickReplies = MutableStateFlow(QuickReply.decode(prefs.getString(QUICK_REPLIES, "").orEmpty()))
    val quickReplies: StateFlow<List<QuickReply>> = _quickReplies.asStateFlow()

    private val _themeMode = MutableStateFlow(ThemeMode.fromWire(prefs.getString(THEME_MODE, null)))
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    /**
     * How much each home-list row says. Per device, like the desktop's sidebar
     * density: a phone and a laptop have different room for a list. A value
     * this build cannot read reads as the default.
     */
    private val _rosterDensity = MutableStateFlow(RosterDensity.fromWire(prefs.getString(ROSTER_DENSITY, null)))
    val rosterDensity: StateFlow<RosterDensity> = _rosterDensity.asStateFlow()

    /** Whether a Live call shows the spoken words. Per phone; Settings and the call screen share it. */
    private val _liveCaptions = MutableStateFlow(prefs.getBoolean(LIVE_CAPTIONS, true))
    val liveCaptions: StateFlow<Boolean> = _liveCaptions.asStateFlow()

    /**
     * Whether the phone shows the extra menus and tools of the computer's
     * Advanced mode. Per phone and off by default, like the desktop switch.
     */
    private val _advancedMode = MutableStateFlow(prefs.getBoolean(ADVANCED_MODE, false))
    val advancedMode: StateFlow<Boolean> = _advancedMode.asStateFlow()

    fun setAdvancedMode(on: Boolean) {
        if (_advancedMode.value == on && prefs.contains(ADVANCED_MODE)) return
        prefs.edit().putBoolean(ADVANCED_MODE, on).commit()
        _advancedMode.value = on
    }

    fun setLiveCaptions(on: Boolean) {
        if (_liveCaptions.value == on && prefs.contains(LIVE_CAPTIONS)) return
        prefs.edit().putBoolean(LIVE_CAPTIONS, on).commit()
        _liveCaptions.value = on
    }

    fun setActivityDetail(detail: ActivityDetail) {
        if (_activityDetail.value == detail && prefs.contains(ACTIVITY_DETAIL)) return
        // The value is small and changed only from Settings. Commit makes a
        // selection durable before a process recreation can observe it.
        prefs.edit().putString(ACTIVITY_DETAIL, detail.wireValue).commit()
        _activityDetail.value = detail
    }

    fun setRosterDensity(density: RosterDensity) {
        if (_rosterDensity.value == density && prefs.contains(ROSTER_DENSITY)) return
        prefs.edit().putString(ROSTER_DENSITY, density.wireValue).commit()
        _rosterDensity.value = density
    }

    fun setQuickReplies(replies: List<QuickReply>) {
        // An encoded empty list is meaningful: it hides the chip row. Do not
        // turn it into an absent key, which QuickReply.decode correctly treats
        // as a first-run default.
        val encoded = QuickReply.encode(replies)
        if (_quickReplies.value == replies && prefs.getString(QUICK_REPLIES, null) == encoded) return
        prefs.edit().putString(QUICK_REPLIES, encoded).commit()
        _quickReplies.value = replies
    }

    fun resetQuickReplies() = setQuickReplies(QuickReply.DEFAULTS)

    fun setThemeMode(mode: ThemeMode) {
        if (_themeMode.value == mode && prefs.getString(THEME_MODE, null) == mode.wireValue) return
        prefs.edit().putString(THEME_MODE, mode.wireValue).commit()
        _themeMode.value = mode
    }

    fun lastShareDestination(connectionId: String): String? =
        prefs.getString(destinationKey(connectionId), null)?.takeIf(String::isNotBlank)

    fun setLastShareDestination(connectionId: String, destinationId: String) {
        val key = destinationKey(connectionId)
        if (prefs.getString(key, null) == destinationId) return
        prefs.edit().putString(key, destinationId).commit()
    }

    /** Only a generic roster tap restores this preference; explicit links keep their target. */
    fun restoringThread(chat: Chat, connectionId: String?): Chat {
        if (chat !is Chat.BotChat || connectionId == null) return chat
        val threadId = prefs.getString(threadKey(connectionId, chat.id), null) ?: return chat
        return chat.bot.forTask(threadId)?.let(Chat::BotChat) ?: chat
    }

    fun rememberThread(chat: Chat, connectionId: String?) {
        if (chat !is Chat.BotChat || connectionId == null) return
        val key = threadKey(connectionId, chat.id)
        if (prefs.getString(key, null) != chat.threadId) {
            prefs.edit().putString(key, chat.threadId).commit()
        }
    }

    companion object {
        const val NAME = "openmaus.chat-preferences"
        const val FILE = "$NAME.xml"
        private const val ACTIVITY_DETAIL = "companion.prefs.activityDetail"
        private const val QUICK_REPLIES = "companion.prefs.quickReplies"
        private const val THEME_MODE = "companion.prefs.themeMode"
        private const val ROSTER_DENSITY = "companion.prefs.rosterDensity"
        private const val LIVE_CAPTIONS = "companion.prefs.liveCaptions"
        private const val ADVANCED_MODE = "companion.prefs.advancedMode"

        private fun threadKey(connectionId: String, botId: String): String =
            "thread.last-opened.${connectionId.length}:$connectionId$botId"

        private fun destinationKey(connectionId: String): String =
            "share.last-destination.$connectionId"
    }
}
