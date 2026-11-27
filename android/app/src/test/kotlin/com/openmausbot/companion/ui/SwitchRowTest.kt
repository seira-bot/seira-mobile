package com.openmausbot.companion.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNode
import androidx.compose.ui.test.performClick
import com.openmausbot.companion.ui.theme.SeiraIcons
import com.openmausbot.companion.ui.theme.SeiraSwitch
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SwitchRowTest {
    @get:Rule val compose = createComposeRule()

    private val isSwitch = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch)

    @Test
    fun `a row with an icon is one switch and a tap toggles it`() {
        var toggles = 0
        compose.setContent {
            var on by remember { mutableStateOf(false) }
            SwitchRow(
                title = "Needs your OK",
                subtitle = "When a bot waits",
                icon = SeiraIcons.Bell,
                checked = on,
                onToggle = {
                    toggles += 1
                    on = !on
                },
            )
        }
        compose.onNode(isSwitch).assertIsOff()
        compose.onNode(isSwitch).performClick()
        compose.waitForIdle()
        assertEquals(1, toggles)
        compose.onNode(isSwitch).assertIsOn()
    }

    @Test
    fun `a row without an icon keeps the same switch semantics`() {
        compose.setContent { SwitchRow(title = "Always on", subtitle = null, icon = null, checked = true, onToggle = {}) }
        compose.onNode(isSwitch).assertIsOn()
        compose.onNode(isSwitch).assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.On))
    }

    @Test
    fun `a standalone switch reports the new value on a tap`() {
        val values = mutableListOf<Boolean>()
        compose.setContent {
            SeiraSwitch(checked = false, onCheckedChange = { values += it }, contentDescription = "Sound")
        }
        compose.onNode(isSwitch).performClick()
        compose.waitForIdle()
        assertEquals(listOf(true), values)
    }

    @Test
    fun `the live icon picks its effect from the icon`() {
        assertEquals(LiveIconKind.BELL, LiveRowIconRules.kindOf(SeiraIcons.Bell))
        assertEquals(LiveIconKind.CHECK, LiveRowIconRules.kindOf(SeiraIcons.Check))
        assertEquals(LiveIconKind.WARNING, LiveRowIconRules.kindOf(SeiraIcons.Warning))
        assertEquals(LiveIconKind.OTHER, LiveRowIconRules.kindOf(SeiraIcons.Moon))
    }

    @Test
    fun `the bell stays within 14 degrees and the pulse returns to one`() {
        for (step in 0..100) {
            assertTrue(abs(LiveRowIconRules.bellAngle(step / 100f)) <= 14f)
        }
        assertEquals(1f, LiveRowIconRules.pulse(0f, 0.18f), 0.0001f)
        assertEquals(1.18f, LiveRowIconRules.pulse(0.5f, 0.18f), 0.0001f)
        assertEquals(1f, LiveRowIconRules.pulse(1f, 0.18f), 0.0001f)
    }

    @Test
    fun `every channel row has an icon`() {
        assertEquals(SeiraIcons.Check, NotificationChannelRow.APPROVAL.icon)
        assertEquals(SeiraIcons.Bell, NotificationChannelRow.DONE.icon)
        assertEquals(SeiraIcons.Warning, NotificationChannelRow.ROUTINE.icon)
    }
}
