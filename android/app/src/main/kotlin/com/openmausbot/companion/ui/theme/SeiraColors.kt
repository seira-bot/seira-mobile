package com.openmausbot.companion.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.openmausbot.companion.ui.MausPalette

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
    val ink: Color,
    val ink2: Color,
    val ink3: Color,
    val hairline: Color,
    val hairlineChat: Color,
    val primary: Color,
    val onPrimary: Color,
    val accent: Color,
    val accentText: Color,
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
        private val LightInk = Color(0xFF141417)
        private val DarkInk = Color(0xFFF2F2F3)

        val Light = SeiraColors(
            isDark = false,
            app = Color(0xFFFFFFFF),
            tint = Color(0xFFF4F4F6),
            ink = LightInk,
            ink2 = LightInk.copy(alpha = 0.68f),
            ink3 = LightInk.copy(alpha = 0.60f),
            hairline = LightInk.copy(alpha = 0.08f),
            hairlineChat = LightInk.copy(alpha = 0.09f),
            primary = Color(0xFF141417),
            onPrimary = Color(0xFFFFFFFF),
            accent = Color(0xFF2563EB),
            accentText = Color(0xFF1D4FD0),
            success = Color(0xFF13834F),
            successSoft = Color(0xFF13834F).copy(alpha = 0.10f),
            warning = Color(0xFFD98A00),
            warningText = Color(0xFFA35F00),
            warningSoft = Color(0xFFD98A00).copy(alpha = 0.12f),
            danger = Color(0xFFC4232D),
            seiraName = Color(0xFFC81E4A),
            lumiName = Color(0xFF0B7480),
            control = Color(0xFFEBEBEE),
            composer = Color(0xFFFFFFFF),
            sheet = Color(0xFFFFFFFF),
            bubble = Color(0xFFF0F0F2),
            grabber = Color(0xFF101016).copy(alpha = 0.16f),
            glass = Color(0xFFFFFFFF).copy(alpha = 0.78f),
            scrim = Color(0xFF101016).copy(alpha = 0.24f),
            seg = Color(0xFFE8E8EB),
            thumb = Color(0xFFFFFFFF),
            switchTrackOff = LightInk.copy(alpha = 0.14f),
            switchKnobOn = Color(0xFFFFFFFF),
            switchKnobOff = Color(0xFFFFFFFF),
            shadowRing = Color(0xFF101016).copy(alpha = 0.08f),
            shadowKey = Color(0xFF101016).copy(alpha = 0.22f),
            call = SeiraCallColors(
                app = Color(0xFFFFFFFF),
                danger = Color(0xFFE5383B),
                control = Color(0xFFEFEFF1),
            ),
        )

        val Dark = SeiraColors(
            isDark = true,
            app = Color(0xFF111113),
            tint = Color(0xFF1B1B1E),
            ink = DarkInk,
            ink2 = DarkInk.copy(alpha = 0.68f),
            ink3 = DarkInk.copy(alpha = 0.50f),
            hairline = DarkInk.copy(alpha = 0.08f),
            hairlineChat = DarkInk.copy(alpha = 0.09f),
            primary = Color(0xFFF2F2F3),
            onPrimary = Color(0xFF111113),
            accent = Color(0xFF4F8DFF),
            accentText = Color(0xFF8CB5FF),
            success = Color(0xFF3CCF8E),
            successSoft = Color(0xFF3CCF8E).copy(alpha = 0.14f),
            warning = Color(0xFFF5A524),
            warningText = Color(0xFFF5B04A),
            warningSoft = Color(0xFFF5A524).copy(alpha = 0.14f),
            danger = Color(0xFFFF6B70),
            seiraName = Color(0xFFFF7A95),
            lumiName = Color(0xFF3FD0DA),
            control = Color(0xFF26262A),
            composer = Color(0xFF1C1C1F),
            sheet = Color(0xFF1C1C1F),
            bubble = Color(0xFF26262B),
            grabber = Color(0xFFFFFFFF).copy(alpha = 0.18f),
            glass = Color(0xFF1E1E22).copy(alpha = 0.72f),
            scrim = Color(0xFF000000).copy(alpha = 0.55f),
            seg = Color(0xFF26262A),
            thumb = Color(0xFF3A3A40),
            switchTrackOff = DarkInk.copy(alpha = 0.18f),
            switchKnobOn = Color(0xFF111113),
            switchKnobOff = Color(0xFFF2F2F3),
            shadowRing = Color(0xFFFFFFFF).copy(alpha = 0.09f),
            shadowKey = Color(0xFF000000).copy(alpha = 0.60f),
            call = SeiraCallColors(
                app = Color(0xFF0D0D0F),
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
    val identity = if (colorName == null) MausPalette.FALLBACK else MausPalette.argb(colorName)
    return if (dark) {
        MausPalette.mix(PlateBaseDark, identity, 0.34)
    } else {
        MausPalette.mix(PlateBaseLight, identity, 0.26)
    }
}

fun identityPlate(colorName: String?, dark: Boolean): Color = Color(identityPlateArgb(colorName, dark))
