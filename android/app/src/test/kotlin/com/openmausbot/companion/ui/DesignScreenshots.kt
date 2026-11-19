package com.openmausbot.companion.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import com.openmausbot.companion.ui.theme.Seira
import com.openmausbot.companion.ui.theme.ThemeMode
import java.io.File

internal fun designScreenshotFile(name: String, mode: ThemeMode): File {
    val directory = File("build/design-screens").apply { mkdirs() }
    val suffix = if (mode == ThemeMode.DARK) "dark" else "light"
    return File(directory, "$name-$suffix.png")
}

/**
 * Renders [content] in the theme for [mode] and writes the root to
 * `build/design-screens/<name>-<light|dark>.png`. A rule takes one `setContent`,
 * so call this once per test. Use `@GraphicsMode(GraphicsMode.Mode.NATIVE)` and
 * `@Config(qualifiers = "w390dp-h844dp-xxhdpi")` on the test class.
 */
internal fun ComposeContentTestRule.captureDesign(
    name: String,
    mode: ThemeMode,
    content: @Composable () -> Unit,
): File {
    require(mode != ThemeMode.AUTO) { "A screenshot needs an explicit light or dark mode." }
    setContent {
        CompanionTheme(mode = mode, darkTheme = mode == ThemeMode.DARK) {
            Box(Modifier.fillMaxSize().background(Seira.colors.app)) { content() }
        }
    }
    waitForIdle()
    val file = designScreenshotFile(name, mode)
    file.outputStream().use { out ->
        onRoot().captureToImage().asAndroidBitmap()
            .compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
    }
    return file
}
