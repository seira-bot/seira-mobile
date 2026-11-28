package bot.seira.companion.character

import android.app.Application
import android.graphics.Bitmap
import bot.seira.companion.core.SeiraCast
import bot.seira.companion.ui.CharacterMood
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** One transparent portrait per cast member, happy and idle, for the store listing. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CastPortraitTest {
    private val context get() = RuntimeEnvironment.getApplication() as Application

    @Test
    fun `every cast member gets a happy and an idle portrait`() {
        val json = File("src/main/assets/seira-character/cast.json").takeIf(File::isFile)?.readText()
            ?: File("app/src/main/assets/seira-character/cast.json").readText()
        val members = SeiraCast.decode(json)
        assertEquals(26, members.size)
        val dir = File("build/design-screens/cast").also { it.mkdirs() }
        for (member in members) {
            for ((suffix, mood) in listOf("" to CharacterMood.HAPPY, "-idle" to CharacterMood.IDLE)) {
                val bitmap = SeiraCharacterSnapshot.render(
                    context, member.mascotBody, member.color, member.mascotAccessories, null, 768, mood, 0f,
                )
                val file = File(dir, "${member.id}$suffix.png")
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                assertTrue(file.length() > 10_000, "${file.name} is blank")
            }
        }
    }
}
