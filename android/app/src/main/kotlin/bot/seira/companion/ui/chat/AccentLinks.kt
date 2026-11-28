package bot.seira.companion.ui.chat

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import bot.seira.companion.ui.theme.Seira

/** Draws the links inside [content] in the accent text colour. */
@Composable
internal fun AccentLinks(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.copy(primary = Seira.colors.accentText),
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes,
        content = content,
    )
}
