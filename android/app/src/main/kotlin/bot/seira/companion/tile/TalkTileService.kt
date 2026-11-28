package bot.seira.companion.tile

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import bot.seira.companion.SeiraApp
import bot.seira.companion.R
import bot.seira.companion.core.Session
import bot.seira.companion.shortcuts.talkChief
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Quick Settings tile that opens a live call with the chief of staff. */
open class TalkTileService : TileService() {
    private var refreshJob: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        val app = application as? SeiraApp ?: return
        refreshJob?.cancel()
        refreshJob = app.appScope.launch {
            app.session.awaitRestored()
            val paired = app.session.restoreState.value !is Session.RestoreState.Unpaired
            val name = app.session.state.value.talkChief()?.name?.takeIf { it.isNotBlank() }
                ?: TalkTileState(this@TalkTileService).chiefName
            render(paired, name)
        }
    }

    override fun onStopListening() {
        refreshJob?.cancel()
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        val open = Runnable { startTalk(TalkIntents.intent(this)) }
        if (deviceLocked()) unlock(open) else open.run()
    }

    internal open fun deviceLocked(): Boolean = isLocked

    internal open fun unlock(action: Runnable) = unlockAndRun(action)

    @SuppressLint("StartActivityAndCollapseDeprecated")
    internal open fun startTalk(intent: Intent) {
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(
                PendingIntent.getActivity(
                    this,
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun render(paired: Boolean, chiefName: String?) {
        val tile = qsTile ?: return
        tile.state = tileStateFor(paired)
        tile.label = getString(R.string.tile_talk_label)
        if (Build.VERSION.SDK_INT >= 29) tile.subtitle = if (paired) chiefName else null
        tile.updateTile()
    }

    companion object {
        fun tileStateFor(paired: Boolean): Int = if (paired) Tile.STATE_INACTIVE else Tile.STATE_UNAVAILABLE
    }
}
