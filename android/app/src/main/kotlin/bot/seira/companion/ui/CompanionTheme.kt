package bot.seira.companion.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDirection
import bot.seira.companion.ui.theme.LocalSeiraColors
import bot.seira.companion.ui.theme.SeiraColors
import bot.seira.companion.ui.theme.SeiraTypography
import bot.seira.companion.ui.theme.ThemeMode
import bot.seira.companion.ui.theme.resolvesDark

/** CSS's eight-digit form is RRGGBBAA; Compose expects the alpha first. */
internal fun cssHexToArgb(hex: String): Long {
    require(hex.length == 6 || hex.length == 8) { "Expected RRGGBB or RRGGBBAA: $hex" }
    return if (hex.length == 6) {
        "FF$hex".toLong(16)
    } else {
        (hex.takeLast(2) + hex.dropLast(2)).toLong(16)
    }
}

private fun SeiraColors.toColorScheme(): ColorScheme {
    val outline = ink.copy(alpha = 0.28f)
    return if (isDark) {
        darkColorScheme(
            primary = primary, onPrimary = onPrimary,
            primaryContainer = control, onPrimaryContainer = ink,
            secondary = accent, onSecondary = onAccent,
            secondaryContainer = control, onSecondaryContainer = ink,
            tertiary = success, onTertiary = Color.White,
            background = app, onBackground = ink,
            surface = app, onSurface = ink,
            surfaceVariant = tint, onSurfaceVariant = ink2,
            surfaceTint = app,
            inverseSurface = ink, inverseOnSurface = app, inversePrimary = onPrimary,
            error = danger, onError = Color.White,
            errorContainer = danger.copy(alpha = 0.16f), onErrorContainer = danger,
            outline = outline, outlineVariant = hairline,
            scrim = scrim,
            surfaceBright = control, surfaceDim = app,
            surfaceContainerLowest = app, surfaceContainerLow = tint, surfaceContainer = tint,
            surfaceContainerHigh = control, surfaceContainerHighest = control,
        )
    } else {
        lightColorScheme(
            primary = primary, onPrimary = onPrimary,
            primaryContainer = control, onPrimaryContainer = ink,
            secondary = accent, onSecondary = onAccent,
            secondaryContainer = control, onSecondaryContainer = ink,
            tertiary = success, onTertiary = Color.White,
            background = app, onBackground = ink,
            surface = app, onSurface = ink,
            surfaceVariant = tint, onSurfaceVariant = ink2,
            surfaceTint = app,
            inverseSurface = ink, inverseOnSurface = app, inversePrimary = onPrimary,
            error = danger, onError = Color.White,
            errorContainer = danger.copy(alpha = 0.12f), onErrorContainer = danger,
            outline = outline, outlineVariant = hairline,
            scrim = scrim,
            surfaceBright = app, surfaceDim = tint,
            surfaceContainerLowest = app, surfaceContainerLow = tint, surfaceContainer = tint,
            surfaceContainerHigh = control, surfaceContainerHighest = control,
        )
    }
}

/**
 * Reading direction comes from the words, not from the locale's layout.
 *
 * Compose's default is [TextDirection.Unspecified], and — unlike the platform's
 * own `TextView`, whose default is `TEXT_DIRECTION_FIRST_STRONG` — it resolves
 * straight off `LocalLayoutDirection`: an RTL locale gives *every* paragraph an
 * RTL base level, English or not. On an `ar-EG` device that produced three
 * measured defects, all of them the same bidi reordering:
 *
 *  - trailing punctuation jumped to the front — *"What do you mostly want help
 *    with?"* drew as *"?What do you mostly want help with"*, because a neutral
 *    `?` at the end of an LTR run inside an RTL paragraph resolves to the
 *    paragraph level and is reordered to the paragraph's leading edge;
 *  - a counted list came out backwards — the bot's *"1 2 3 … 20"* drew as
 *    *"16 15 … 1"*, because digits are weak, the spaces between them resolve to
 *    R, and the whole sequence lays out right to left;
 *  - one-line roster previews lost their opening characters — *"error: CUA
 *    Driver is not ready…"* drew as *"ror: CUA Driver…"*, the line being aligned
 *    to the right and ellipsised at what the paragraph thought was its start.
 *
 * [TextDirection.ContentOrLtr] resolves the base level from the first strong
 * character instead: English stays LTR with its punctuation in place, Arabic a
 * user types stays RTL, and text with no strong character at all — a bare
 * number, a timestamp, a path — falls back to LTR rather than to the locale.
 * That last fallback is the one place this goes past `TextView`'s
 * `FIRST_STRONG` (which falls back to the layout direction), and it is what
 * fixes the counted list.
 *
 * It is a choice about this product, not a fact about text, and it has a price:
 * a paragraph with no strong character is anchored LTR even when an Arabic user
 * typed it — Arabic-Indic digits, an emoji-only line, bare punctuation from the
 * composer. The choice is made for what this app actually carries: counts,
 * clock times, file paths, code and command output, read left to right whoever
 * is holding the phone. If the app ever ships an RTL translation, or grows text
 * where a neutral line should follow the reader rather than the content, this
 * is the line to come back to.
 *
 * This is only about the direction glyphs are *ordered* in. Where the box sits
 * is untouched: `Row`/`Column`, `padding(start=)`, `placeRelative` and the
 * auto-mirrored icons all keep reading `LocalLayoutDirection`, so the chrome,
 * the bubble sides and the table's mirrored columns still follow the locale.
 *
 * It is set on the theme's [Typography] rather than at every call site, so it
 * reaches whatever Compose draws under this theme — the message bubbles, the
 * roster previews, the labels — by way of `LocalTextStyle` and the
 * `MaterialTheme.typography` slots, with nothing new to remember at the next
 * call site.
 *
 * Its reach ends where the theme does. Notifications are drawn by SystemUI in
 * its own window, from strings that never pass through a Compose typography, so
 * the same policy has to be carried there inside the string itself — see
 * `notifications.NotificationText`, which anchors each notification paragraph
 * that would otherwise take the shade's base direction.
 */
internal fun TextStyle.readingFromContent(): TextStyle =
    copy(textDirection = TextDirection.ContentOrLtr)

@Composable
fun CompanionTheme(
    mode: ThemeMode = ThemeMode.DEFAULT,
    /** Forces light or dark for previews and UI tests. */
    darkTheme: Boolean? = null,
    content: @Composable () -> Unit,
) {
    val seira = SeiraColors.of(darkTheme ?: mode.resolvesDark())
    CompositionLocalProvider(LocalSeiraColors provides seira) {
        MaterialTheme(
            colorScheme = seira.toColorScheme(),
            typography = SeiraTypography,
            content = content,
        )
    }
}

/** SwiftUI's `Color.secondary` — the muted foreground everything quiet uses. */
val secondaryTint: Color
    @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
