package com.openmausbot.companion.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.openmausbot.companion.core.SeiraCast
import com.openmausbot.companion.core.SeiraLook
import com.openmausbot.companion.ui.theme.ThemeMode
import java.io.File
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w390dp-h844dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChangeLookSheetPreviewTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun cast() = SeiraCast.decode(
        java.io.File("src/main/assets/seira-character/cast.json").takeIf(File::isFile)?.readText()
            ?: File("app/src/main/assets/seira-character/cast.json").readText(),
    )

    private fun render(name: String, mode: ThemeMode, category: ChangeLookCategory, prepare: (ChangeLookState) -> Unit = {}) {
        val state = ChangeLookState(SeiraLook.SEIRA, cast())
        prepare(state)
        state.category = category
        val shot = compose.captureDesign(name, mode) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                ChangeLookSheetBody(botName = "Dina Pratama", state = state, connected = true, onClose = {}, onSave = {})
            }
        }
        val out = File("build/outputs/character-preview").apply { mkdirs() }
        val target = File(out, "change-look-${shot.nameWithoutExtension}.png")
        shot.copyTo(target, overwrite = true)
        assertTrue(target.length() > 10_000, "the sheet picture is blank")
    }

    @Test
    fun charactersLight() = render("look-characters", ThemeMode.LIGHT, ChangeLookCategory.CHARACTERS)

    @Test
    fun charactersDark() = render("look-characters", ThemeMode.DARK, ChangeLookCategory.CHARACTERS) { it.setColor("blue") }

    @Test
    fun extrasLight() = render("look-extras", ThemeMode.LIGHT, ChangeLookCategory.EXTRAS) {
        it.toggleExtra("glasses")
        it.toggleExtra("ribbon")
    }

    @Test
    fun colourDark() = render("look-colour", ThemeMode.DARK, ChangeLookCategory.COLOUR)

    @Test
    fun faceLight() = render("look-face", ThemeMode.LIGHT, ChangeLookCategory.FACE) { it.setExpression("happy") }

    @Test
    fun hairLight() = render("look-hair", ThemeMode.LIGHT, ChangeLookCategory.HAIR)
}
