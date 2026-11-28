package bot.seira.companion.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import bot.seira.companion.ui.BotPalette

/** Colours of the full-screen Live call, which is darker than the app in dark mode. */
@Immutable
data class SeiraCallColors(
    val app: Color,
    val danger: Color,
    val control: Color,
)

@Immutable
data class SeiraColors(
    val isDark: Boolean,
    val app: Color,
    val tint: Color,
    val s1: Color,
    val s2: Color,
    val s3: Color,
    val ink: Color,
    val ink2: Color,
    val ink3: Color,
    val hairline: Color,
    val line2: Color,
    val hairlineChat: Color,
    val primary: Color,
    val onPrimary: Color,
    val accent: Color,
    val accentText: Color,
    val accentSoft: Color,
    val onAccent: Color,
    val success: Color,
    val successSoft: Color,
    val warning: Color,
    val warningText: Color,
    val warningSoft: Color,
    val danger: Color,
    val seiraName: Color,
    val lumiName: Color,
    val control: Color,
    val composer: Color,
    val sheet: Color,
    val bubble: Color,
    val grabber: Color,
    val glass: Color,
    val scrim: Color,
    val seg: Color,
    val thumb: Color,
    val switchTrackOff: Color,
    val switchKnobOn: Color,
    val switchKnobOff: Color,
    val shadowRing: Color,
    val shadowKey: Color,
    val call: SeiraCallColors,
) {
    companion object {
        private val LightInk = Color(0xFF121317)
        private val LightLine = Color(0xFF0A0B0D)
        private val DarkInk = Color(0xFFEDEEF0)

        val Light = SeiraColors(
            isDark = false,
            app = Color(0xFFF4F4F6),
            tint = Color(0xFFFFFFFF),
            s1 = Color(0xFFFFFFFF),
            s2 = Color(0xFFE9EAEE),
            s3 = Color(0xFFD9DBE1),
            ink = LightInk,
            ink2 = Color(0xFF454A53),
            ink3 = Color(0xFF5C626D),
            hairline = LightLine.copy(alpha = 0.10f),
            line2 = LightLine.copy(alpha = 0.20f),
            hairlineChat = LightLine.copy(alpha = 0.10f),
            primary = Color(0xFF121317),
            onPrimary = Color(0xFFFFFFFF),
            accent = Color(0xFFC42A4D),
            accentText = Color(0xFFA81E3D),
            accentSoft = Color(0xFFC42A4D).copy(alpha = 0.10f),
            onAccent = Color(0xFFFFFFFF),
            success = Color(0xFF0A6A44),
            successSoft = Color(0xFF0A6A44).copy(alpha = 0.10f),
            warning = Color(0xFF7D4E00),
            warningText = Color(0xFF7D4E00),
            warningSoft = Color(0xFF7D4E00).copy(alpha = 0.12f),
            danger = Color(0xFFB3261E),
            seiraName = Color(0xFFA81E3D),
            lumiName = Color(0xFF0B7480),
            control = Color(0xFFE9EAEE),
            composer = Color(0xFFFFFFFF),
            sheet = Color(0xFFFFFFFF),
            bubble = Color(0xFFE9EAEE),
            grabber = Color(0xFF101016).copy(alpha = 0.16f),
            glass = Color(0xFFFFFFFF).copy(alpha = 0.78f),
            scrim = Color(0xFF000000).copy(alpha = 0.62f),
            seg = Color(0xFFE9EAEE),
            thumb = Color(0xFFD9DBE1),
            switchTrackOff = LightInk.copy(alpha = 0.14f),
            switchKnobOn = Color(0xFFFFFFFF),
            switchKnobOff = Color(0xFFFFFFFF),
            shadowRing = Color(0xFF101016).copy(alpha = 0.08f),
            shadowKey = Color(0xFF101016).copy(alpha = 0.22f),
            call = SeiraCallColors(
                app = Color(0xFFF4F4F6),
                danger = Color(0xFFE5383B),
                control = Color(0xFFE9EAEE),
            ),
        )

        val Dark = SeiraColors(
            isDark = true,
            app = Color(0xFF0B0C0E),
            tint = Color(0xFF15171B),
            s1 = Color(0xFF15171B),
            s2 = Color(0xFF1F2228),
            s3 = Color(0xFF2C3038),
            ink = DarkInk,
            ink2 = Color(0xFFA8ADB5),
            ink3 = Color(0xFF8B919B),
            hairline = Color(0xFFFFFFFF).copy(alpha = 0.08f),
            line2 = Color(0xFFFFFFFF).copy(alpha = 0.17f),
            hairlineChat = Color(0xFFFFFFFF).copy(alpha = 0.08f),
            primary = Color(0xFFF2F3F5),
            onPrimary = Color(0xFF0B0C0E),
            accent = Color(0xFFFF3D67),
            accentText = Color(0xFFFF7D98),
            accentSoft = Color(0xFFFF3D67).copy(alpha = 0.16f),
            onAccent = Color(0xFF1B0409),
            success = Color(0xFF3DDC97),
            successSoft = Color(0xFF3DDC97).copy(alpha = 0.14f),
            warning = Color(0xFFFFB547),
            warningText = Color(0xFFFFB547),
            warningSoft = Color(0xFFFFB547).copy(alpha = 0.14f),
            danger = Color(0xFFFF8A80),
            seiraName = Color(0xFFFF7D98),
            lumiName = Color(0xFF3FD0DA),
            control = Color(0xFF1F2228),
            composer = Color(0xFF15171B),
            sheet = Color(0xFF15171B),
            bubble = Color(0xFF1F2228),
            grabber = Color(0xFFFFFFFF).copy(alpha = 0.18f),
            glass = Color(0xFF15171B).copy(alpha = 0.72f),
            scrim = Color(0xFF000000).copy(alpha = 0.62f),
            seg = Color(0xFF1F2228),
            thumb = Color(0xFF2C3038),
            switchTrackOff = DarkInk.copy(alpha = 0.18f),
            switchKnobOn = Color(0xFF0B0C0E),
            switchKnobOff = Color(0xFFF2F3F5),
            shadowRing = Color(0xFFFFFFFF).copy(alpha = 0.09f),
            shadowKey = Color(0xFF000000).copy(alpha = 0.60f),
            call = SeiraCallColors(
                app = Color(0xFF070809),
                danger = Color(0xFFFF4D5E),
                control = Color(0xFF232327),
            ),
        )

        fun of(dark: Boolean): SeiraColors = if (dark) Dark else Light
    }
}

val LocalSeiraColors = staticCompositionLocalOf { SeiraColors.Light }

object Seira {
    val colors: SeiraColors
        @Composable @ReadOnlyComposable get() = LocalSeiraColors.current
}

private val PlateBaseLight = 0xFFF1F1F4.toInt()
private val PlateBaseDark = 0xFF1C1C21.toInt()

/** ARGB of the plate behind a bot's face: its identity colour mixed into a neutral base. */
fun identityPlateArgb(colorName: String?, dark: Boolean): Int {
    val identity = if (colorName == null) BotPalette.FALLBACK else BotPalette.argb(colorName)
    return if (dark) {
        BotPalette.mix(PlateBaseDark, identity, 0.34)
    } else {
        BotPalette.mix(PlateBaseLight, identity, 0.26)
    }
}

fun identityPlate(colorName: String?, dark: Boolean): Color = Color(identityPlateArgb(colorName, dark))
