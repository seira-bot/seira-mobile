package com.openmausbot.companion.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.openmausbot.companion.ui.theme.AvatarRing
import com.openmausbot.companion.ui.theme.ControlCircleButton
import com.openmausbot.companion.ui.theme.GlassCircleButton
import com.openmausbot.companion.ui.theme.IdentityAvatar
import com.openmausbot.companion.ui.theme.InkPillButton
import com.openmausbot.companion.ui.theme.Seira
import com.openmausbot.companion.ui.theme.SeiraCard
import com.openmausbot.companion.ui.theme.SeiraIcons
import com.openmausbot.companion.ui.theme.SeiraMark
import com.openmausbot.companion.ui.theme.SeiraSwitch
import com.openmausbot.companion.ui.theme.SeiraType
import com.openmausbot.companion.ui.theme.SegmentedControl
import com.openmausbot.companion.ui.theme.ThemeMode
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
class ThemeGalleryScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun lightGalleryIsWritten() {
        assertTrue(compose.captureDesign("theme-gallery", ThemeMode.LIGHT) { Gallery() }.length() > 0)
    }

    @Test
    fun darkGalleryIsWritten() {
        assertTrue(compose.captureDesign("theme-gallery", ThemeMode.DARK) { Gallery() }.length() > 0)
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    private fun Gallery() {
        val scene = androidx.compose.runtime.remember { WiringScene() }
        CompositionLocalProvider(LocalCompanion provides scene.environment) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SeiraMark(size = 40.dp)
                    Text("Seira", style = SeiraType.title1, color = Seira.colors.ink)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    InkPillButton("Talk", onClick = {}, icon = SeiraIcons.AudioLines)
                    InkPillButton("New chat", onClick = {}, icon = SeiraIcons.PenSquare)
                    GlassCircleButton(SeiraIcons.Search, "Search", onClick = {})
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    ControlCircleButton(SeiraIcons.Mic, "Mute", onClick = {})
                    ControlCircleButton(SeiraIcons.Volume2, "Speaker", onClick = {}, selected = true)
                    SeiraSwitch(checked = true, onCheckedChange = {}, contentDescription = "On")
                    SeiraSwitch(checked = false, onCheckedChange = {}, contentDescription = "Off")
                }
                SegmentedControl(
                    options = listOf(
                        "Light" to SeiraIcons.Sun,
                        "Dark" to SeiraIcons.Moon,
                        "Auto" to SeiraIcons.Smartphone,
                    ),
                    selectedIndex = 1,
                    onSelect = {},
                )
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.padding(6.dp)) {
                    listOf("red", "blue", "green", "purple").forEachIndexed { index, color ->
                        IdentityAvatar(
                            bot = bot(id = "bot-$index").copy(color = color),
                            size = 56.dp,
                            ring = if (index == 0) AvatarRing.Working else AvatarRing.None,
                            waitingDot = index == 1,
                            animated = false,
                        )
                    }
                }
                SeiraCard(Modifier.fillMaxWidth()) {
                    Text(
                        "Needs your OK",
                        style = SeiraType.headline,
                        color = Seira.colors.ink,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    SeiraIcons.all.forEach { icon ->
                        Icon(icon, contentDescription = null, tint = Seira.colors.ink, modifier = Modifier.size(24.dp))
                    }
                }
            }
        }
    }
}
