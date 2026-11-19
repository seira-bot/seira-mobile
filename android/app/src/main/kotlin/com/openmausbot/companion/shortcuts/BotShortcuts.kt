package com.openmausbot.companion.shortcuts

import android.content.Context
import android.content.pm.ShortcutInfo
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.app.Person
import androidx.core.content.LocusIdCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.openmausbot.companion.notifications.NotificationIntents

/**
 * Long-lived dynamic shortcuts: one per computer and bot.
 *
 * The same shortcut feeds the launcher's long-press menu and the system
 * conversation section that a notification joins through its shortcut id.
 * The platform may throw or refuse when a call comes too often, so every
 * call is best effort.
 */
class BotShortcuts(context: Context) {
    private val appContext = context.applicationContext

    fun shortcutId(computerId: String, bot: String): String = "${prefix(computerId)}${Uri.encode(bot)}"

    private fun characterFor(identity: BotIdentity, avatar: Bitmap?): Bitmap? =
        if (avatar == null) BotIcons.character(appContext, identity) else null

    fun person(identity: BotIdentity, key: String, avatar: Bitmap?): Person =
        Person.Builder()
            .setName(identity.name)
            .setKey(key)
            .setBot(true)
            .setIcon(IconCompat.createWithBitmap(BotIcons.circle(avatar, identity.name, identity.color, character = characterFor(identity, avatar))))
            .build()

    fun push(computerId: String, identity: BotIdentity, avatar: Bitmap?) {
        attempt { ShortcutManagerCompat.pushDynamicShortcut(appContext, build(computerId, identity, avatar)) }
    }

    /**
     * Publishes [recent] (most recent first) and drops the shortcuts of other
     * computers and of bots that [knownBotIds] no longer lists.
     */
    fun publish(
        computerId: String,
        recent: List<Pair<BotIdentity, Bitmap?>>,
        knownBotIds: Set<String>,
    ) {
        attempt {
            val mine = prefix(computerId)
            val stale = ShortcutManagerCompat.getDynamicShortcuts(appContext)
                .map { it.id }
                .filter { id ->
                    id.startsWith(ID_PREFIX) &&
                        (!id.startsWith(mine) || Uri.decode(id.removePrefix(mine)) !in knownBotIds)
                }
            if (stale.isNotEmpty()) {
                ShortcutManagerCompat.removeDynamicShortcuts(appContext, stale)
                ShortcutManagerCompat.removeLongLivedShortcuts(appContext, stale)
            }
        }
        recent.reversed().forEach { (identity, avatar) -> push(computerId, identity, avatar) }
    }

    fun reportUsed(shortcutId: String) {
        attempt { ShortcutManagerCompat.reportShortcutUsed(appContext, shortcutId) }
    }

    fun removeAll() {
        attempt {
            val tracked = runCatching {
                ShortcutManagerCompat.getShortcuts(
                    appContext,
                    ShortcutManagerCompat.FLAG_MATCH_DYNAMIC or
                        ShortcutManagerCompat.FLAG_MATCH_CACHED or
                        ShortcutManagerCompat.FLAG_MATCH_PINNED,
                )
            }.getOrElse { ShortcutManagerCompat.getDynamicShortcuts(appContext) }
                .map { it.id }
                .filter { it.startsWith(ID_PREFIX) }
            ShortcutManagerCompat.removeAllDynamicShortcuts(appContext)
            if (tracked.isNotEmpty()) {
                ShortcutManagerCompat.removeLongLivedShortcuts(appContext, tracked)
            }
        }
    }

    internal fun build(computerId: String, identity: BotIdentity, avatar: Bitmap?): ShortcutInfoCompat {
        val id = shortcutId(computerId, identity.botId)
        val name = identity.name.ifBlank { identity.botId }
        return ShortcutInfoCompat.Builder(appContext, id)
            .setShortLabel(name)
            .setIntent(
                NotificationIntents.openIntent(appContext, identity.botId, identity.threadId)
                    .putExtra(NotificationIntents.EXTRA_SHORTCUT_ID, id),
            )
            .setIcon(IconCompat.createWithAdaptiveBitmap(BotIcons.adaptive(avatar, name, identity.color, character = characterFor(identity, avatar))))
            .setPerson(person(identity, id, avatar))
            .setLongLived(true)
            .setLocusId(LocusIdCompat(id))
            .setCategories(setOf(ShortcutInfo.SHORTCUT_CATEGORY_CONVERSATION))
            .build()
    }

    private fun prefix(computerId: String): String = "$ID_PREFIX${Uri.encode(computerId)}:"

    private inline fun attempt(block: () -> Unit) {
        try {
            block()
        } catch (_: RuntimeException) {
            // The shortcut service rejects calls that exceed its rate limit.
        }
    }

    companion object {
        const val ID_PREFIX = "bot:"
    }
}
