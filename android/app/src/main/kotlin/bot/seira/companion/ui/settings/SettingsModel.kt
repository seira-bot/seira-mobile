package bot.seira.companion.ui.settings

internal enum class SettingsGroup {
    COMPUTER,
    APPEARANCE,
    NOTIFICATIONS,
    CHAT,
    WORKSPACE,
    PRIVACY,
    ADVANCED,
    HELP,
    SIGN_OUT,
    UPDATES,
}

internal enum class ChatRowKind { ACTIVITY, QUICK_REPLIES, DENSITY, VOICE, CAPTIONS }

internal object SettingsLayout {
    fun groups(
        showsComputer: Boolean,
        paired: Boolean,
        showsWorkspace: Boolean,
        showsPrivacy: Boolean,
        updatesAvailable: Boolean,
    ): List<SettingsGroup> = buildList {
        if (showsComputer) add(SettingsGroup.COMPUTER)
        add(SettingsGroup.APPEARANCE)
        add(SettingsGroup.NOTIFICATIONS)
        add(SettingsGroup.CHAT)
        if (showsWorkspace) add(SettingsGroup.WORKSPACE)
        if (showsPrivacy) add(SettingsGroup.PRIVACY)
        add(SettingsGroup.ADVANCED)
        add(SettingsGroup.HELP)
        if (paired) add(SettingsGroup.SIGN_OUT)
        if (updatesAvailable) add(SettingsGroup.UPDATES)
    }

    fun chatRows(paired: Boolean, callsEnabled: Boolean): List<ChatRowKind> = buildList {
        add(ChatRowKind.ACTIVITY)
        add(ChatRowKind.QUICK_REPLIES)
        add(ChatRowKind.DENSITY)
        if (paired && callsEnabled) {
            add(ChatRowKind.VOICE)
            add(ChatRowKind.CAPTIONS)
        }
    }

    /** With another computer saved, the sign-out row removes only the current one. */
    fun removesOneOfMany(computerCount: Int): Boolean = computerCount > 1
}
