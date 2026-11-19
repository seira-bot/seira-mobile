package com.openmausbot.companion.ui.theme

import android.app.UiModeManager
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable

enum class ThemeMode(val wireValue: String) {
    LIGHT("light"),
    DARK("dark"),
    AUTO("auto"),
    ;

    fun isDark(systemDark: Boolean): Boolean = when (this) {
        LIGHT -> false
        DARK -> true
        AUTO -> systemDark
    }

    companion object {
        val DEFAULT = AUTO

        fun fromWire(raw: String?): ThemeMode =
            entries.firstOrNull { it.wireValue == raw } ?: DEFAULT
    }
}

/** Whether this mode draws dark right now, following the phone when it is [ThemeMode.AUTO]. */
@Composable
fun ThemeMode.resolvesDark(): Boolean = isDark(isSystemInDarkTheme())

/**
 * Tells the system which night mode the app chose, so the system splash screen
 * and system dialogs match it. `UiModeManager.setApplicationNightMode` exists
 * from API 31; older releases only see the Compose theme.
 */
class NightModeApplier(
    private val setNightMode: ((Int) -> Unit)?,
    private val sdkInt: Int = Build.VERSION.SDK_INT,
) {
    fun apply(mode: ThemeMode) {
        if (sdkInt < Build.VERSION_CODES.S) return
        setNightMode?.invoke(nightModeFor(mode))
    }

    companion object {
        fun forSystem(manager: UiModeManager?): NightModeApplier = NightModeApplier(
            if (manager == null) null else { code: Int -> manager.setApplicationNightMode(code) },
        )

        fun nightModeFor(mode: ThemeMode): Int = when (mode) {
            ThemeMode.LIGHT -> UiModeManager.MODE_NIGHT_NO
            ThemeMode.DARK -> UiModeManager.MODE_NIGHT_YES
            ThemeMode.AUTO -> UiModeManager.MODE_NIGHT_AUTO
        }
    }
}
