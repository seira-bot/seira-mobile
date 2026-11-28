package bot.seira.companion.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import bot.seira.companion.core.SeiraCast
import bot.seira.companion.core.SeiraLook
import bot.seira.companion.ui.theme.ThemeMode
import java.io.File
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w390dp-h844dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChangeLookSheetPreviewTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>(
        effectContext = object : MotionDurationScale { override val scaleFactor = 0f },
    )

    private fun cast() = SeiraCast.decode(
        java.io.File("src/main/assets/seira-character/cast.json").takeIf(File::isFile)?.readText()
            ?: File("app/src/main/assets/seira-character/cast.json").readText(),
    )

    private fun render(
        name: String,
        mode: ThemeMode,
        category: ChangeLookCategory,
        tab: AvatarTab = AvatarTab.SEIRA,
        panels: AvatarPanels = AvatarPanels(),
        prepare: (ChangeLookState) -> Unit = {},
    ) {
        val state = ChangeLookState(SeiraLook.SEIRA, cast())
        prepare(state)
        state.category = category
        val shot = compose.captureDesign(name, mode) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                ChangeLookSheetBody(
                    botName = "Dina Pratama",
                    state = state,
                    connected = true,
                    tab = tab,
                    panels = panels,
                    currentAvatar = { size ->
                        Box(
                            Modifier
                                .size(size)
                                .clip(CircleShape)
                                .background(Brush.linearGradient(listOf(Color(0xFFF2B880), Color(0xFF7BA7D9))), CircleShape),
                        )
                    },
                    onClose = {},
                    onSave = {},
                )
            }
        }
        val out = File("build/outputs/character-preview").apply { mkdirs() }
        val target = File(out, "change-look-${shot.nameWithoutExtension}.png")
        shot.copyTo(target, overwrite = true)
        assertTrue(target.length() > 10_000, "the sheet picture is blank")
    }

    @Test
    fun seiraTabLight() = render("look-seira", ThemeMode.LIGHT, ChangeLookCategory.HAIR)

    @Test
    fun seiraTabDark() = render("look-seira", ThemeMode.DARK, ChangeLookCategory.HAIR) { it.setColor("blue") }

    @Test
    fun generateNotSetUpLight() = render(
        "look-generate",
        ThemeMode.LIGHT,
        ChangeLookCategory.HAIR,
        tab = AvatarTab.GENERATE,
        panels = AvatarPanels(generationReady = false, canGenerate = false),
    )

    @Test
    fun uploadWithPictureLight() = render(
        "look-upload",
        ThemeMode.LIGHT,
        ChangeLookCategory.HAIR,
        tab = AvatarTab.UPLOAD,
        panels = AvatarPanels(hasPicture = true, showsPicture = false),
    )

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
