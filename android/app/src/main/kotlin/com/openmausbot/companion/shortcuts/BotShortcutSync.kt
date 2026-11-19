package com.openmausbot.companion.shortcuts

import com.openmausbot.companion.avatar.AvatarImageStore
import com.openmausbot.companion.core.Session
import com.openmausbot.companion.tile.TalkTileState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Keeps the launcher shortcuts and the tile's chief name in step with the roster. */
class BotShortcutSync(
    private val session: Session,
    private val avatars: AvatarImageStore,
    private val shortcuts: BotShortcuts,
    private val tile: TalkTileState,
    private val settleMs: Long = SETTLE_MS,
) {
    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    suspend fun run() = coroutineScope {
        launch {
            session.restoreState
                .map { it is Session.RestoreState.Unpaired }
                .distinctUntilChanged()
                .collect { unpaired ->
                    if (unpaired) {
                        shortcuts.removeAll()
                        tile.chiefName = null
                    }
                }
        }
        launch {
            combine(
                session.connection.map { it?.id }.distinctUntilChanged(),
                session.state.map { RecentBots.snapshot(it) }.distinctUntilChanged(),
            ) { computerId, snapshot -> computerId to snapshot }
                .debounce(settleMs)
                .collectLatest { (computerId, snapshot) ->
                    if (computerId == null || snapshot.knownBotIds.isEmpty()) return@collectLatest
                    publish(computerId, snapshot)
                }
        }
    }

    internal suspend fun publish(computerId: String, snapshot: ShortcutSnapshot) {
        snapshot.chiefName?.let { tile.chiefName = it }
        val state = session.state.value
        val items = snapshot.recent.map { identity ->
            val avatar = state.bot(identity.botId)?.let { bot ->
                withTimeoutOrNull(AVATAR_WAIT_MS) { avatars.bitmapFor(bot) }
            }
            identity to avatar
        }
        shortcuts.publish(computerId, items, snapshot.knownBotIds)
    }

    companion object {
        const val SETTLE_MS = 750L
        const val AVATAR_WAIT_MS = 2_000L
    }
}
