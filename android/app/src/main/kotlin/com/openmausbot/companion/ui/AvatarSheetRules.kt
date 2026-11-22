package com.openmausbot.companion.ui

import com.openmausbot.companion.core.AvatarCrop
import com.openmausbot.companion.core.Bot

internal enum class AvatarTab {
    SEIRA,
    GENERATE,
    UPLOAD,
    ;

    companion object {
        fun restore(name: String?): AvatarTab? = entries.firstOrNull { it.name == name }
    }
}

/** What the avatar sheet decides from a bot's stored picture and crop. */
internal object AvatarSheetRules {
    fun showsPicture(bot: Bot): Boolean =
        bot.avatarUrl != null && (bot.avatarCrop ?: AvatarCrop.MASCOT) != AvatarCrop.MASCOT

    fun hasPicture(bot: Bot): Boolean = bot.avatarUrl != null

    fun canUsePicture(bot: Bot): Boolean = hasPicture(bot) && !showsPicture(bot)

    fun defaultTab(bot: Bot): AvatarTab = if (showsPicture(bot)) AvatarTab.UPLOAD else AvatarTab.SEIRA

    /** The server may answer a generation with the mascot crop; a new picture must show. */
    fun needsCircle(generated: Bot): Boolean = (generated.avatarCrop ?: AvatarCrop.MASCOT) == AvatarCrop.MASCOT
}
