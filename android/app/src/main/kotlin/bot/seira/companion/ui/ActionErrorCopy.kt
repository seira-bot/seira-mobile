package bot.seira.companion.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import bot.seira.companion.R
import bot.seira.companion.core.Session

/** Maps the fixed English errors that the core raises to string resources. */
internal object ActionErrorCopy {
    fun resource(text: String): Int? = when (text) {
        Session.QUEUED_STEER_UNSUPPORTED_MESSAGE -> R.string.shell_error_queued_steer_unsupported
        Session.RELOAD_MEMORY_MESSAGE -> R.string.shell_error_reload_memory
        else -> null
    }
}

@Composable
internal fun localizedActionError(text: String): String =
    ActionErrorCopy.resource(text)?.let { stringResource(it) } ?: localizedMobileCopy(text)
