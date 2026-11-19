package com.openmausbot.companion.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import com.openmausbot.companion.ui.theme.Seira
import com.openmausbot.companion.ui.theme.ThemeMode
import java.io.File

internal fun designScreenshotFile(name: String, mode: ThemeMode): File {
    val directory = File("build/design-screens").apply { mkdirs() }
    val suffix = if (mode == ThemeMode.DARK) "dark" else "light"
    return File(directory, "$name-$suffix.png")
}

/**
 * Renders [content] in the theme for [mode] and draws the window to
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
    val decor = (this as AndroidComposeTestRule<*, *>).activity.window.decorView
    val metrics = decor.resources.displayMetrics
    var width = decor.width
    var height = decor.height
    if (width == 0 || height == 0) {
        width = metrics.widthPixels
        height = metrics.heightPixels
        decor.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        decor.layout(0, 0, width, height)
    }
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    decor.draw(Canvas(bitmap))
    file.outputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
    return file
}
